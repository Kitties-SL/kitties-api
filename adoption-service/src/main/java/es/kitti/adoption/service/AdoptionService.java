package es.kitti.adoption.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.kitti.mon.either.Either;
import es.kitti.mon.error.*;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import es.kitti.adoption.client.CatClient;
import es.kitti.adoption.dto.*;
import java.util.stream.Collectors;
import es.kitti.adoption.entity.*;
import es.kitti.adoption.event.AdoptionFormAnalysedEvent;
import es.kitti.adoption.event.AdoptionFormSubmittedEvent;
import es.kitti.adoption.mapper.AdoptionMapper;
import es.kitti.adoption.repository.*;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;

@ApplicationScoped
public class AdoptionService {

    @Inject ObjectMapper objectMapper;
    @Inject AdoptionRequestRepository adoptionRequestRepository;
    @Inject AdoptionRequestFormRepository adoptionRequestFormRepository;
    @Inject AdoptionFormRepository adoptionFormRepository;
    @Inject InterviewRepository interviewRepository;
    @Inject ExpenseRepository expenseRepository;
    @Inject AdoptionMapper adoptionMapper;

    @RestClient CatClient catClient;

    @Inject
    @Channel("adoption-form-submitted")
    Emitter<AdoptionFormSubmittedEvent> adoptionFormSubmittedEmitter;

    public Uni<Either<DomainError, AdoptionRequestResponse>> createAdoptionRequest(
            AdoptionRequestCreateRequest request, Long adopterId) {

        return verifyCatActive(request.catId())
                .onItem().transformToUni(catEither -> catEither.fold(
                        err -> Uni.createFrom().item(Either.left(err)),
                        v   -> Panache.withTransaction(() ->
                                adoptionRequestRepository.existsActiveByCatId(request.catId())
                                        .onItem().transformToUni(exists -> {
                                            if (exists)
                                                return Uni.createFrom().item(Either.<DomainError, AdoptionRequestResponse>left(new ConflictError("CAT_NOT_AVAILABLE")));
                                            AdoptionRequest entity = adoptionMapper.toEntity(request, adopterId);
                                            return adoptionRequestRepository.persist(entity)
                                                    .onItem().transform(saved -> Either.<DomainError, AdoptionRequestResponse>right(adoptionMapper.toResponse(saved)));
                                        })
                        )
                ));
    }

    @WithSession
    public Uni<Either<DomainError, AdoptionRequestResponse>> findById(Long id, Long callerId) {
        return findAdoptionOrNotFound(id)
                .onItem().transform(either -> either.flatMap(adoption ->
                        checkParticipant(adoption, callerId).map(v -> adoptionMapper.toResponse(adoption))
                ));
    }

    @WithSession
    public Uni<List<AdoptionRequestResponse>> findByAdopterId(Long adopterId) {
        return adoptionRequestRepository.findByAdopterId(adopterId)
                .onItem().transform(list -> list.stream().map(adoptionMapper::toResponse).toList());
    }

    @WithSession
    public Uni<List<AdoptionRequestResponse>> findByOrganizationId(Long organizationId) {
        return adoptionRequestRepository.findByOrganizationId(organizationId)
                .onItem().transform(list -> list.stream().map(adoptionMapper::toResponse).toList());
    }

    @WithSession
    public Uni<AdoptionPipelineStatsResponse> getOrgPipeline(Long organizationId) {
        return adoptionRequestRepository.findByOrganizationId(organizationId)
                .onItem().transform(list -> {
                    var counts = list.stream()
                            .collect(Collectors.groupingBy(a -> a.status, Collectors.counting()));
                    return new AdoptionPipelineStatsResponse(
                            counts.getOrDefault(AdoptionStatus.Pending, 0L),
                            counts.getOrDefault(AdoptionStatus.Reviewing, 0L),
                            counts.getOrDefault(AdoptionStatus.Accepted, 0L),
                            counts.getOrDefault(AdoptionStatus.FormCompleted, 0L),
                            counts.getOrDefault(AdoptionStatus.PaymentPending, 0L),
                            counts.getOrDefault(AdoptionStatus.PaymentFailed, 0L),
                            counts.getOrDefault(AdoptionStatus.Completed, 0L),
                            counts.getOrDefault(AdoptionStatus.Rejected, 0L)
                    );
                });
    }

    @WithSession
    public Uni<List<AdoptionRequestResponse>> findByCatIdForOrg(Long catId, Long organizationId) {
        return adoptionRequestRepository.findByCatIdAndOrganizationId(catId, organizationId)
                .onItem().transform(list -> list.stream().map(adoptionMapper::toResponse).toList());
    }

    public Uni<Either<DomainError, AdoptionRequestResponse>> updateStatus(
            Long id, AdoptionStatusUpdateRequest request, Long userId) {

        boolean isTerminal = request.status() == AdoptionStatus.Rejected
                || request.status() == AdoptionStatus.Completed;

        return Panache.withSession(() ->
                findAdoptionOrNotFound(id)
                        .onItem().transform(either -> either.flatMap(adoption ->
                                checkOrganizationOwner(adoption, userId).map(v -> adoption.catId)
                        ))
        )
        .onItem().transformToUni(either -> either.fold(
                err   -> Uni.createFrom().item(Either.left(err)),
                catId -> isTerminal
                        ? Uni.createFrom().item(Either.<DomainError, Void>right(null))
                        : verifyCatActive(catId)
        ))
        .onItem().transformToUni(either -> either.fold(
                err -> Uni.createFrom().item(Either.left(err)),
                v   -> Panache.withTransaction(() ->
                        findAdoptionOrNotFound(id)
                                .onItem().transformToUni(either2 -> either2.fold(
                                        err -> Uni.createFrom().item(Either.left(err)),
                                        adoption -> checkOrganizationOwner(adoption, userId).fold(
                                                err -> Uni.createFrom().item(Either.left(err)),
                                                v2  -> {
                                                    adoption.status = request.status();
                                                    adoption.rejectionReason = request.reason();
                                                    return adoptionRequestRepository.persist(adoption)
                                                            .onItem().transform(saved -> Either.<DomainError, AdoptionRequestResponse>right(adoptionMapper.toResponse(saved)));
                                                }
                                        )
                                ))
                )
        ));
    }

    public Uni<Either<DomainError, AdoptionRequestFormResponse>> submitRequestForm(
            Long adoptionRequestId, AdoptionRequestFormCreateRequest request, Long adopterId) {

        return Panache.withSession(() ->
                findAdoptionOrNotFound(adoptionRequestId)
                        .onItem().transform(either -> either.flatMap(adoption ->
                                checkAdopter(adoption, adopterId)
                                        .flatMap(v -> checkStatus(adoption, AdoptionStatus.Pending))
                                        .map(v -> adoption.catId)
                        ))
        )
        .onItem().transformToUni(either -> either.fold(
                err   -> Uni.createFrom().item(Either.left(err)),
                catId -> verifyCatActive(catId)
        ))
        .onItem().transformToUni(either -> either.fold(
                err -> Uni.createFrom().item(Either.left(err)),
                v   -> Panache.withTransaction(() ->
                        findAdoptionOrNotFound(adoptionRequestId)
                                .onItem().transformToUni(either2 -> either2.fold(
                                        err -> Uni.createFrom().item(Either.left(err)),
                                        adoption -> checkAdopter(adoption, adopterId).fold(
                                                err -> Uni.createFrom().item(Either.left(err)),
                                                v2  -> checkStatus(adoption, AdoptionStatus.Pending).fold(
                                                        err -> Uni.createFrom().item(Either.left(err)),
                                                        v3  -> {
                                                            AdoptionRequestForm form = adoptionMapper.toEntity(request, adoptionRequestId);
                                                            adoption.status = AdoptionStatus.Reviewing;
                                                            return adoptionRequestFormRepository.persist(form)
                                                                    .onItem().transformToUni(saved ->
                                                                            adoptionRequestRepository.persist(adoption)
                                                                                    .onItem().transform(persisted -> {
                                                                                        adoptionFormSubmittedEmitter.send(buildFormSubmittedEvent(adoption, saved));
                                                                                        return Either.<DomainError, AdoptionRequestFormResponse>right(adoptionMapper.toResponse(saved));
                                                                                    })
                                                                    );
                                                        }
                                                )
                                        )
                                ))
                )
        ));
    }

    public Uni<Either<DomainError, InterviewResponse>> scheduleInterview(
            Long adoptionRequestId, InterviewCreateRequest request, Long organizationId) {

        return Panache.withSession(() ->
                findAdoptionOrNotFound(adoptionRequestId)
                        .onItem().transform(either -> either.flatMap(adoption ->
                                checkOrganizationOwner(adoption, organizationId)
                                        .flatMap(v -> checkStatus(adoption, AdoptionStatus.Accepted))
                                        .map(v -> adoption.catId)
                        ))
        )
        .onItem().transformToUni(either -> either.fold(
                err   -> Uni.createFrom().item(Either.left(err)),
                catId -> verifyCatActive(catId)
        ))
        .onItem().transformToUni(either -> either.fold(
                err -> Uni.createFrom().item(Either.left(err)),
                v   -> Panache.withTransaction(() ->
                        findAdoptionOrNotFound(adoptionRequestId)
                                .onItem().transformToUni(either2 -> either2.fold(
                                        err -> Uni.createFrom().item(Either.left(err)),
                                        adoption -> checkOrganizationOwner(adoption, organizationId).fold(
                                                err -> Uni.createFrom().item(Either.left(err)),
                                                v2  -> checkStatus(adoption, AdoptionStatus.Accepted).fold(
                                                        err -> Uni.createFrom().item(Either.left(err)),
                                                        v3  -> {
                                                            Interview interview = adoptionMapper.toEntity(request, adoptionRequestId);
                                                            return interviewRepository.persist(interview)
                                                                    .onItem().transform(saved -> Either.<DomainError, InterviewResponse>right(adoptionMapper.toResponse(saved)));
                                                        }
                                                )
                                        )
                                ))
                )
        ));
    }

    public Uni<Either<DomainError, AdoptionFormResponse>> submitAdoptionForm(
            Long adoptionRequestId, AdoptionFormCreateRequest request, Long adopterId) {

        return Panache.withSession(() ->
                findAdoptionOrNotFound(adoptionRequestId)
                        .onItem().transform(either -> either.flatMap(adoption ->
                                checkAdopter(adoption, adopterId)
                                        .flatMap(v -> checkStatus(adoption, AdoptionStatus.Accepted))
                                        .map(v -> adoption.catId)
                        ))
        )
        .onItem().transformToUni(either -> either.fold(
                err   -> Uni.createFrom().item(Either.left(err)),
                catId -> verifyCatActive(catId)
        ))
        .onItem().transformToUni(either -> either.fold(
                err -> Uni.createFrom().item(Either.left(err)),
                v   -> Panache.withTransaction(() ->
                        findAdoptionOrNotFound(adoptionRequestId)
                                .onItem().transformToUni(either2 -> either2.fold(
                                        err -> Uni.createFrom().item(Either.left(err)),
                                        adoption -> checkAdopter(adoption, adopterId).fold(
                                                err -> Uni.createFrom().item(Either.left(err)),
                                                v2  -> checkStatus(adoption, AdoptionStatus.Accepted).fold(
                                                        err -> Uni.createFrom().item(Either.left(err)),
                                                        v3  -> adoptionFormRepository.findByAdoptionRequestId(adoptionRequestId)
                                                                .onItem().transformToUni(existing -> {
                                                                    if (existing != null)
                                                                        return Uni.createFrom().item(Either.<DomainError, AdoptionFormResponse>left(new ConflictError("ADOPTION_FORM_ALREADY_SUBMITTED")));
                                                                    AdoptionForm form = adoptionMapper.toEntity(request, adoptionRequestId);
                                                                    adoption.status = AdoptionStatus.FormCompleted;
                                                                    return adoptionFormRepository.persist(form)
                                                                            .onItem().transformToUni(saved ->
                                                                                    adoptionRequestRepository.persist(adoption)
                                                                                            .onItem().transform(persisted -> Either.<DomainError, AdoptionFormResponse>right(adoptionMapper.toResponse(saved)))
                                                                            );
                                                                })
                                                )
                                        )
                                ))
                )
        ));
    }

    @WithSession
    public Uni<AdoptionDataExport> exportByAdopterId(Long adopterId) {
        return adoptionRequestRepository.findByAdopterId(adopterId)
                .onItem().transformToUni(requests -> {
                    if (requests.isEmpty())
                        return Uni.createFrom().item(new AdoptionDataExport(List.of()));
                    List<Uni<AdoptionExportEntry>> entries = requests.stream()
                            .map(r -> Uni.combine().all().unis(
                                    adoptionRequestFormRepository.findByAdoptionRequestId(r.id),
                                    adoptionFormRepository.findByAdoptionRequestId(r.id),
                                    interviewRepository.findByAdoptionRequestId(r.id),
                                    expenseRepository.findByAdoptionRequestId(r.id)
                            ).asTuple().onItem().transform(t -> new AdoptionExportEntry(
                                    adoptionMapper.toResponse(r),
                                    t.getItem1() != null ? adoptionMapper.toResponse(t.getItem1()) : null,
                                    t.getItem2() != null ? adoptionMapper.toResponse(t.getItem2()) : null,
                                    t.getItem3().stream().map(adoptionMapper::toResponse).toList(),
                                    t.getItem4().stream().map(adoptionMapper::toResponse).toList()
                            ))).toList();
                    return Uni.join().all(entries).andFailFast()
                            .onItem().transform(AdoptionDataExport::new);
                });
    }

    @Incoming("adoption-form-analysed")
    public Uni<Void> onFormAnalysed(String message) {
        try {
            AdoptionFormAnalysedEvent event = objectMapper.readValue(message, AdoptionFormAnalysedEvent.class);
            return Panache.withTransaction(() ->
                    findAdoptionOrNotFound(event.adoptionRequestId())
                            .onItem().transformToUni(either -> either.fold(
                                    err      -> Uni.createFrom().voidItem(),
                                    adoption -> {
                                        if ("REJECTED".equals(event.decision())) {
                                            adoption.status = AdoptionStatus.Rejected;
                                            adoption.rejectionReason = event.rejectionReason();
                                        }
                                        return adoptionRequestRepository.persist(adoption).replaceWithVoid();
                                    }
                            ))
            );
        } catch (Exception e) {
            return Uni.createFrom().voidItem();
        }
    }

    // --- helpers privados ---

    private Uni<Either<DomainError, AdoptionRequest>> findAdoptionOrNotFound(Long id) {
        return adoptionRequestRepository.findById(id)
                .onItem().transform(adoption -> adoption == null
                        ? Either.<DomainError, AdoptionRequest>left(new NotFoundError("ADOPTION_REQUEST_NOT_FOUND"))
                        : Either.<DomainError, AdoptionRequest>right(adoption));
    }

    private Either<DomainError, Void> checkOrganizationOwner(AdoptionRequest adoption, Long orgId) {
        return adoption.organizationId.equals(orgId)
                ? Either.right(null)
                : Either.left(new ForbiddenError("ACCESS_DENIED"));
    }

    private Either<DomainError, Void> checkAdopter(AdoptionRequest adoption, Long adopterId) {
        return adoption.adopterId.equals(adopterId)
                ? Either.right(null)
                : Either.left(new ForbiddenError("ACCESS_DENIED"));
    }

    private Either<DomainError, Void> checkStatus(AdoptionRequest adoption, AdoptionStatus required) {
        return adoption.status == required
                ? Either.right(null)
                : Either.left(new ConflictError("INVALID_ADOPTION_STATUS"));
    }

    private Either<DomainError, Void> checkParticipant(AdoptionRequest adoption, Long callerId) {
        return adoption.adopterId.equals(callerId) || adoption.organizationId.equals(callerId)
                ? Either.right(null)
                : Either.left(new ForbiddenError("ACCESS_DENIED"));
    }

    private Uni<Either<DomainError, Void>> verifyCatActive(Long catId) {
        return catClient.findById(catId)
                .onFailure(jakarta.ws.rs.WebApplicationException.class)
                .recoverWithItem(e -> ((jakarta.ws.rs.WebApplicationException) e).getResponse())
                .onItem().transform(response -> response.getStatus() == 200
                        ? Either.<DomainError, Void>right(null)
                        : Either.<DomainError, Void>left(new ConflictError("CAT_NOT_AVAILABLE")));
    }

    private AdoptionFormSubmittedEvent buildFormSubmittedEvent(AdoptionRequest adoption, AdoptionRequestForm form) {
        return new AdoptionFormSubmittedEvent(
                adoption.id, adoption.catId, adoption.adopterId, adoption.organizationId,
                form.hasPreviousCatExperience, form.previousPetsHistory,
                form.adultsInHousehold, form.hasChildren, form.childrenAges,
                form.hasOtherPets, form.otherPetsDescription, form.hoursAlonePerDay,
                form.stableHousing, form.housingInstabilityReason,
                form.housingType != null ? form.housingType.name() : null,
                form.housingSize, form.hasOutdoorAccess, form.isRental, form.rentalPetsAllowed,
                form.hasWindowsWithView, form.hasVerticalSpace, form.hasHidingSpots,
                form.householdActivityLevel != null ? form.householdActivityLevel.name() : null,
                form.whyCatsNeedToPlay, form.dailyPlayMinutes, form.plannedEnrichment,
                form.reactionToUnwantedBehavior, form.hasScratchingPost, form.willingToEnrichEnvironment,
                form.motivationToAdopt, form.understandsLongTermCommitment, form.hasVetBudget,
                form.allHouseholdMembersAgree, form.anyoneHasAllergies, form.allergiesDetail
        );
    }
}
