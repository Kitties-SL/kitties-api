package es.kitti.organization.service;

import es.kitti.mon.either.Either;
import es.kitti.mon.error.ConflictError;
import es.kitti.mon.error.DomainError;
import es.kitti.mon.error.NotFoundError;
import es.kitti.organization.client.UserServiceClient;
import es.kitti.organization.client.dto.CreateUserRequest;
import es.kitti.organization.dto.RegisterOrganizationRequest;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import es.kitti.organization.dto.CreateOrganizationRequest;
import es.kitti.organization.dto.OrganizationResponse;
import es.kitti.organization.dto.UpdateOrganizationRequest;
import es.kitti.organization.entity.Organization;
import es.kitti.organization.mapper.OrganizationMapper;
import es.kitti.organization.repository.OrganizationRepository;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class OrganizationService {

    @Inject OrganizationRepository organizationRepository;
    @Inject OrganizationMemberService memberService;
    @Inject OrganizationMapper mapper;
    @Inject OrganizationWriteService writeService;
    @RestClient UserServiceClient userServiceClient;
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
        return userServiceClient.checkEmailExists(request.adminEmail(), internalSecret)
                .onFailure().retry().atMost(2)
                .onItem().transformToUni(checkResponse -> {
                    if (checkResponse.getStatus() == 200)
                        return Uni.createFrom().<Either<DomainError, OrganizationResponse>>item(
                                Either.left(new ConflictError("ADMIN_EMAIL_ALREADY_EXISTS")));
                    return writeService.createOrg(request)
                            .onItem().transformToUni(org -> {
                                var userReq = new CreateUserRequest(
                                        request.adminEmail(), request.adminPassword(),
                                        request.adminName(), request.adminSurname(), request.adminBirthdate());
                                return userServiceClient.createUser(userReq)
                                        .onFailure().retry().atMost(2)
                                        .onItem().transformToUni(created ->
                                                writeService.addAdminMember(org.id, created.id())
                                                        .onItem().transformToUni(__ ->
                                                                userServiceClient.promoteToOrganization(created.id(), internalSecret)
                                                                        .onItem().transformToUni(___ ->
                                                                                writeService.activateOrg(org.id)
                                                                                        .onItem().transform(activated ->
                                                                                                Either.<DomainError, OrganizationResponse>right(
                                                                                                        mapper.toResponse(activated))))))
                                        .onFailure().recoverWithItem(__ ->
                                                Either.<DomainError, OrganizationResponse>left(
                                                        new ConflictError("USER_SERVICE_UNAVAILABLE")));
                            });
                })
                .onFailure().recoverWithItem(__ ->
                        Either.<DomainError, OrganizationResponse>left(
                                new ConflictError("USER_SERVICE_UNAVAILABLE")));
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
