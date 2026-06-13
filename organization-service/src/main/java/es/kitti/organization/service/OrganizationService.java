package es.kitti.organization.service;

import es.kitti.mon.either.Either;
import es.kitti.mon.either.Unit;
import es.kitti.mon.error.BadRequestError;
import es.kitti.mon.error.ConflictError;
import es.kitti.mon.error.DomainError;
import es.kitti.mon.error.NotFoundError;
import es.kitti.organization.client.CatServiceClient;
import es.kitti.organization.client.UserServiceClient;
import es.kitti.organization.client.dto.CountByOrgsRequest;
import es.kitti.organization.client.dto.CreateUserRequest;
import es.kitti.organization.client.dto.OrgCatCount;
import es.kitti.organization.dto.CreateOrganizationRequest;
import es.kitti.organization.dto.NearbyOrganizationResponse;
import es.kitti.organization.dto.OrganizationPublicResponse;
import es.kitti.organization.dto.OrganizationResponse;
import es.kitti.organization.dto.OrganizationSummary;
import es.kitti.organization.dto.PageResponse;
import es.kitti.organization.dto.RegisterOrganizationRequest;
import es.kitti.organization.dto.UpdateOrganizationRequest;
import es.kitti.organization.entity.Organization;
import es.kitti.organization.entity.OrganizationStatus;
import es.kitti.organization.geo.Coordinates;
import es.kitti.organization.mapper.OrganizationMapper;
import es.kitti.organization.repository.CityCoordinatesRepository;
import es.kitti.organization.repository.OrganizationRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@ApplicationScoped
public class OrganizationService {

    @Inject OrganizationRepository organizationRepository;
    @Inject CityCoordinatesRepository cityCoordinatesRepository;
    @Inject OrganizationMemberService memberService;
    @Inject OrganizationMapper mapper;
    @Inject OrganizationWriteService writeService;
    @RestClient UserServiceClient userServiceClient;
    @RestClient CatServiceClient catServiceClient;
    @ConfigProperty(name = "kitties.internal.secret") String internalSecret;

    @WithTransaction
    public Uni<OrganizationResponse> create(CreateOrganizationRequest request, Long creatorUserId) {
        Organization org = mapper.toEntity(request);
        return organizationRepository.persist(org)
                .onItem().transformToUni(saved ->
                        memberService.addCreatorAsAdmin(saved.id, creatorUserId)
                                .onItem().transform(m -> mapper.toResponse(saved)));
    }

    @WithSession
    public Uni<Either<DomainError, OrganizationResponse>> findById(Long id, Long callerId) {
        return memberService.requireMember(id, callerId)
                .onItem().transformToUni(either -> either.fold(
                        err -> Uni.createFrom().item(Either.left(err)),
                        __ -> organizationRepository.findById(id)
                                .onItem().transform(org ->
                                        org == null
                                                ? Either.left(new NotFoundError("ORGANIZATION_NOT_FOUND"))
                                                : Either.<DomainError, OrganizationResponse>right(mapper.toResponse(org)))
                ));
    }

    public Uni<Either<DomainError, OrganizationResponse>> register(RegisterOrganizationRequest request) {
        // Capture the EventLoop so we can hop back to it after the REST client callback (HR000068).
        Context eventLoopCtx = Vertx.currentContext();
        return checkAdminEmailAvailable(request.adminEmail(), eventLoopCtx)
                .onItem().transformToUni(check -> check.fold(
                        // R in Either.left(err) has no inference target → witness it here
                        err -> Uni.createFrom().<Either<DomainError, OrganizationResponse>>item(Either.left(err)),
                        __  -> createOrgWithAdmin(request)
                ));
    }

    // Right(Unit) → admin email is free in user-service.
    // Left(ConflictError) → either already taken, or user-service unreachable after retries.
    private Uni<Either<DomainError, Unit>> checkAdminEmailAvailable(String email, Context eventLoopCtx) {
        return userServiceClient.checkEmailExists(email, internalSecret)
                // 404 means "email free" — recover the failure as a 404 Response value so the chain keeps flowing.
                .onFailure(OrganizationService::isHttpNotFound)
                .recoverWithItem(t -> ((WebApplicationException) t).getResponse())
                // The REST client error callback runs on an executor thread; hop back to the captured
                // EventLoop so Hibernate Reactive downstream finds its session context (HR000068).
                .emitOn(cmd -> eventLoopCtx.runOnContext(v -> cmd.run()))
                .onFailure().retry().atMost(2)
                .onItem().<Either<DomainError, Unit>>transform(resp -> {
                    Log.infof("[register] checkEmailExists status=%d for email=%s", resp.getStatus(), email);
                    return resp.getStatus() == 200
                            ? Either.left(new ConflictError("ADMIN_EMAIL_ALREADY_EXISTS"))
                            : Either.unit();
                })
                .onFailure().recoverWithItem(e -> {
                    Log.errorf(e, "[register] checkEmailExists failed for email=%s", email);
                    return Either.left(new ConflictError("USER_SERVICE_UNAVAILABLE"));
                });
    }

    private Uni<Either<DomainError, OrganizationResponse>> createOrgWithAdmin(RegisterOrganizationRequest request) {
        return writeService.createOrg(request)
                .onItem().transformToUni(org -> provisionAdminUser(request, org.id))
                .onFailure().recoverWithItem(e -> {
                    Log.errorf(e, "[register] createUser/promote/activate failed for email=%s", request.adminEmail());
                    return Either.left(new ConflictError("USER_SERVICE_UNAVAILABLE"));
                });
    }

    private Uni<Either<DomainError, OrganizationResponse>> provisionAdminUser(RegisterOrganizationRequest request, Long orgId) {
        CreateUserRequest userReq = new CreateUserRequest(
                request.adminEmail(), request.adminPassword(),
                request.adminName(), request.adminSurname(), request.adminBirthdate());
        return userServiceClient.createUser(userReq)
                .onFailure().retry().atMost(2)
                .onItem().transformToUni(created -> {
                    Log.infof("[register] createUser ok, userId=%d", created.id());
                    return linkAndActivate(orgId, created.id());
                });
    }

    private Uni<Either<DomainError, OrganizationResponse>> linkAndActivate(Long orgId, Long userId) {
        return writeService.addAdminMember(orgId, userId)
                .onItem().transformToUni(__  -> userServiceClient.promoteToOrganization(userId, internalSecret))
                .onItem().transformToUni(___ -> writeService.activateOrg(orgId))
                .onItem().transform(activated -> Either.<DomainError, OrganizationResponse>right(mapper.toResponse(activated)));
    }

    private static boolean isHttpNotFound(Throwable t) {
        return t instanceof WebApplicationException wae && wae.getResponse().getStatus() == 404;
    }

    @WithSession
    public Uni<Either<DomainError, OrganizationResponse>> findByCurrentUser(Long userId) {
        return memberService.findActiveByUserId(userId)
                .onItem().transformToUni(opt -> {
                    if (opt.isEmpty())
                        return Uni.createFrom().item(Either.left(new NotFoundError("ORGANIZATION_NOT_FOUND")));
                    return organizationRepository.findById(opt.get().organizationId)
                            .onItem().transform(org ->
                                    org == null
                                            ? Either.left(new NotFoundError("ORGANIZATION_NOT_FOUND"))
                                            : Either.<DomainError, OrganizationResponse>right(mapper.toResponse(org))
                            );
                });
    }

    @WithSession
    public Uni<PageResponse<OrganizationSummary>> search(
            String name, String region, String city, int page, int size) {
        if (size > 100) size = 100;
        final int effectiveSize = size;
        Context eventLoopCtx = Vertx.currentContext();
        return organizationRepository.search(name, region, city, page, effectiveSize)
                .onItem().transformToUni(orgs ->
                        organizationRepository.countSearch(name, region, city)
                                .onItem().transformToUni(total -> {
                                    if (orgs.isEmpty())
                                        return Uni.createFrom().item(
                                                PageResponse.of(List.<OrganizationSummary>of(), page, effectiveSize, total));
                                    List<Long> orgIds = orgs.stream().map(o -> o.id).toList();
                                    return fetchCatCounts(orgIds, eventLoopCtx)
                                            .onItem().transform(countMap -> {
                                                List<OrganizationSummary> content = orgs.stream()
                                                        .map(o -> mapper.toSummary(o, countMap.getOrDefault(o.id, 0L)))
                                                        .toList();
                                                return PageResponse.of(content, page, effectiveSize, total);
                                            });
                                })
                );
    }

    @WithSession
    public Uni<Either<DomainError, List<NearbyOrganizationResponse>>> findNearby(
            Double lat, Double lng, String city, int limit) {
        Coordinates origin;
        if (lat != null && lng != null) {
            origin = new Coordinates(lat, lng);
        } else if (city != null && !city.isBlank()) {
            var resolved = cityCoordinatesRepository.findByCity(city);
            if (resolved.isEmpty())
                return Uni.createFrom().item(Either.left(new BadRequestError("UNKNOWN_CITY")));
            origin = resolved.get();
        } else {
            return Uni.createFrom().item(Either.left(new BadRequestError("ORIGIN_REQUIRED")));
        }
        final Coordinates from = origin;
        final int effectiveLimit = limit <= 0 ? 10 : Math.min(limit, 50);
        return organizationRepository.findAllActive()
                .onItem().transform(orgs -> {
                    List<NearbyOrganizationResponse> content = orgs.stream()
                            .map(o -> cityCoordinatesRepository.findByCity(o.city)
                                    .map(c -> mapper.toNearbyResponse(o, from.distanceKm(c)))
                                    .orElse(null))
                            .filter(Objects::nonNull)
                            .sorted(Comparator.comparingDouble(NearbyOrganizationResponse::distanceKm))
                            .limit(effectiveLimit)
                            .toList();
                    return Either.<DomainError, List<NearbyOrganizationResponse>>right(content);
                });
    }

    @WithSession
    public Uni<Either<DomainError, OrganizationPublicResponse>> findPublicById(Long id) {
        Context eventLoopCtx = Vertx.currentContext();
        return organizationRepository.findById(id)
                .onItem().transformToUni(org -> {
                    if (org == null || org.status != OrganizationStatus.Active)
                        return Uni.createFrom().item(Either.<DomainError, OrganizationPublicResponse>left(
                                new NotFoundError("ORGANIZATION_NOT_FOUND")));
                    return fetchCatCounts(List.of(id), eventLoopCtx)
                            .onItem().transform(countMap -> Either.<DomainError, OrganizationPublicResponse>right(
                                    mapper.toPublicResponse(org, countMap.getOrDefault(id, 0L))));
                });
    }

    // Hop back to the EventLoop after the REST client callback to keep Hibernate Reactive's
    // session context alive for downstream work (gotcha HR000068). Context may be null in
    // unit tests outside a Vert.x request; skip the hop in that case.
    private Uni<Map<Long, Long>> fetchCatCounts(List<Long> orgIds, Context eventLoopCtx) {
        Uni<List<OrgCatCount>> call = catServiceClient.countByOrgs(new CountByOrgsRequest(orgIds), internalSecret);
        if (eventLoopCtx != null) {
            call = call.emitOn(cmd -> eventLoopCtx.runOnContext(v -> cmd.run()));
        }
        return call
                .onFailure().recoverWithItem(t -> {
                    Log.warnf(t, "cat-service unreachable; falling back to activeCatsCount=0");
                    return List.<OrgCatCount>of();
                })
                .onItem().transform(counts -> counts.stream()
                        .collect(Collectors.toMap(OrgCatCount::orgId, OrgCatCount::count)));
    }

    @WithTransaction
    public Uni<Either<DomainError, OrganizationResponse>> update(Long id, Long callerId, UpdateOrganizationRequest request) {
        return memberService.requireAdmin(id, callerId)
                .onItem().transformToUni(either -> either.fold(
                        err -> Uni.createFrom().item(Either.left(err)),
                        __ -> organizationRepository.findById(id)
                                .onItem().transformToUni(org -> {
                                    if (org == null)
                                        return Uni.createFrom().item(Either.left(new NotFoundError("ORGANIZATION_NOT_FOUND")));
                                    if (request.name() != null)        org.name = request.name();
                                    if (request.description() != null) org.description = request.description();
                                    if (request.address() != null)     org.address = request.address();
                                    if (request.city() != null)        org.city = request.city();
                                    if (request.region() != null)      org.region = request.region();
                                    if (request.country() != null)     org.country = request.country();
                                    if (request.phone() != null)       org.phone = request.phone();
                                    if (request.email() != null)       org.email = request.email();
                                    if (request.logoUrl() != null)     org.logoUrl = request.logoUrl();
                                    return organizationRepository.persist(org)
                                            .onItem().transform(saved ->
                                                    Either.<DomainError, OrganizationResponse>right(mapper.toResponse(saved)));
                                })
                ));
    }
}
