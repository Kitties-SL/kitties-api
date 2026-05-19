package es.kitti.adoption.intake.service;

import es.kitti.adoption.intake.client.OrganizationClient;
import es.kitti.adoption.intake.client.OrganizationPublicMinimal;
import es.kitti.adoption.intake.dto.*;
import es.kitti.adoption.intake.entity.IntakeRequest;
import es.kitti.adoption.intake.entity.IntakeStatus;
import es.kitti.adoption.intake.mapper.IntakeMapper;
import es.kitti.adoption.intake.repository.IntakeRequestRepository;
import es.kitti.mon.either.Either;
import es.kitti.mon.error.ConflictError;
import es.kitti.mon.error.DomainError;
import es.kitti.mon.error.ForbiddenError;
import es.kitti.mon.error.NotFoundError;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class IntakeRequestService {

    @Inject IntakeRequestRepository repository;
    @Inject IntakeMapper mapper;

    @Inject
    @RestClient
    OrganizationClient organizationClient;

    @ConfigProperty(name = "kitties.internal.secret")
    String internalSecret;

    @WithTransaction
    public Uni<IntakeRequestResponse> create(IntakeRequestCreateRequest request, Long userId) {
        IntakeRequest entity = mapper.toEntity(request, userId);
        return repository.persist(entity)
                .onItem().transform(mapper::toResponse);
    }

    @WithSession
    public Uni<List<IntakeRequestResponse>> findMine(Long userId) {
        return repository.findByUserId(userId)
                .onItem().transform(list -> list.stream().map(mapper::toResponse).toList());
    }

    @WithSession
    public Uni<List<IntakeRequestResponse>> findByOrganization(Long organizationId) {
        return repository.findByTargetOrganizationId(organizationId)
                .onItem().transform(list -> list.stream().map(mapper::toResponse).toList());
    }

    @WithSession
    public Uni<IntakePipelineStatsResponse> getOrgStats(Long organizationId) {
        return repository.findByTargetOrganizationId(organizationId)
                .onItem().transform(list -> {
                    long pending  = list.stream().filter(i -> i.status == IntakeStatus.Pending).count();
                    long approved = list.stream().filter(i -> i.status == IntakeStatus.Approved).count();
                    long rejected = list.stream().filter(i -> i.status == IntakeStatus.Rejected).count();
                    return new IntakePipelineStatsResponse(pending, approved, rejected);
                });
    }

    @WithTransaction
    public Uni<Either<DomainError, IntakeRequestResponse>> approve(Long id, Long callerOrgId) {
        return findPendingForOrg(id, callerOrgId)
                .onItem().transformToUni(either -> either.fold(
                        err    -> Uni.createFrom().item(Either.left(err)),
                        entity -> {
                            entity.status = IntakeStatus.Approved;
                            entity.decidedAt = LocalDateTime.now();
                            return repository.persist(entity)
                                    .onItem().transform(saved -> Either.<DomainError, IntakeRequestResponse>right(mapper.toResponse(saved)));
                        }
                ));
    }

    @WithTransaction
    public Uni<Either<DomainError, IntakeRejectionResponse>> reject(
            Long id, IntakeDecisionRequest decision, Long callerOrgId) {

        return findPendingForOrg(id, callerOrgId)
                .onItem().transformToUni(either -> either.fold(
                        err    -> Uni.createFrom().item(Either.left(err)),
                        entity -> {
                            entity.status = IntakeStatus.Rejected;
                            entity.rejectionReason = decision.reason();
                            entity.decidedAt = LocalDateTime.now();
                            return repository.persist(entity)
                                    .onItem().transformToUni(saved ->
                                            findAlternatives(saved.region, saved.targetOrganizationId)
                                                    .onItem().transform(alts -> Either.<DomainError, IntakeRejectionResponse>right(
                                                            new IntakeRejectionResponse(mapper.toResponse(saved), alts)))
                                    );
                        }
                ));
    }

    private Uni<Either<DomainError, IntakeRequest>> findPendingForOrg(Long id, Long callerOrgId) {
        return repository.findById(id)
                .onItem().transform(entity -> {
                    if (entity == null)
                        return Either.<DomainError, IntakeRequest>left(new NotFoundError("INTAKE_REQUEST_NOT_FOUND"));
                    if (!entity.targetOrganizationId.equals(callerOrgId))
                        return Either.<DomainError, IntakeRequest>left(new ForbiddenError("ACCESS_DENIED"));
                    if (entity.status != IntakeStatus.Pending)
                        return Either.<DomainError, IntakeRequest>left(new ConflictError("INVALID_INTAKE_STATUS"));
                    return Either.<DomainError, IntakeRequest>right(entity);
                });
    }

    private Uni<List<OrganizationPublicMinimal>> findAlternatives(String region, Long excludeOrgId) {
        return organizationClient.findByRegion(region, internalSecret)
                .onItem().transform(list -> list.stream()
                        .filter(o -> !excludeOrgId.equals(o.id()))
                        .toList())
                .onFailure().recoverWithItem(t -> {
                    Log.warnf(t, "Could not fetch alternative organizations for region %s", region);
                    return List.of();
                });
    }
}
