package es.kitti.organization.service;

import es.kitti.mon.either.Either;
import es.kitti.mon.error.DomainError;
import es.kitti.mon.error.NotFoundError;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import es.kitti.organization.dto.CreateOrganizationRequest;
import es.kitti.organization.dto.OrganizationResponse;
import es.kitti.organization.dto.UpdateOrganizationRequest;
import es.kitti.organization.entity.Organization;
import es.kitti.organization.mapper.OrganizationMapper;
import es.kitti.organization.repository.OrganizationRepository;

@ApplicationScoped
public class OrganizationService {

    @Inject OrganizationRepository organizationRepository;
    @Inject OrganizationMemberService memberService;
    @Inject OrganizationMapper mapper;

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
                .onItem().transformToUni(ignored -> organizationRepository.findById(id))
                .onItem().transform(org ->
                        org == null
                                ? Either.left(new NotFoundError("ORGANIZATION_NOT_FOUND"))
                                : Either.<DomainError, OrganizationResponse>right(mapper.toResponse(org))
                );
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
                .onItem().transformToUni(ignored -> organizationRepository.findById(id))
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
                });
    }
}
