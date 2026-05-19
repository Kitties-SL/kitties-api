package es.kitti.adoption.service;

import es.kitti.adoption.dto.*;
import es.kitti.adoption.entity.*;
import es.kitti.adoption.event.AdoptionFormSubmittedEvent;
import es.kitti.adoption.mapper.AdoptionMapper;
import es.kitti.adoption.repository.AdoptionFormRepository;
import es.kitti.adoption.repository.AdoptionRequestFormRepository;
import es.kitti.adoption.repository.AdoptionRequestRepository;
import es.kitti.adoption.repository.InterviewRepository;
import es.kitti.mon.either.Either;
import es.kitti.mon.error.ConflictError;
import es.kitti.mon.error.DomainError;
import es.kitti.mon.error.ForbiddenError;
import es.kitti.mon.error.NotFoundError;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
public class AdoptionWriteService {

    @Inject AdoptionRequestRepository adoptionRequestRepository;
    @Inject AdoptionRequestFormRepository adoptionRequestFormRepository;
    @Inject AdoptionFormRepository adoptionFormRepository;
    @Inject InterviewRepository interviewRepository;
    @Inject AdoptionMapper adoptionMapper;

    @Inject
    @Channel("adoption-form-submitted")
    Emitter<AdoptionFormSubmittedEvent> adoptionFormSubmittedEmitter;

    @WithTransaction
    public Uni<Either<DomainError, AdoptionRequestResponse>> createRequest(
            AdoptionRequestCreateRequest request, Long adopterId) {
        return adoptionRequestRepository.existsActiveByCatId(request.catId())
                .onItem().transformToUni(exists -> {
                    if (exists)
                        return Uni.createFrom().item(Either.left(new ConflictError("CAT_NOT_AVAILABLE")));
                    AdoptionRequest entity = adoptionMapper.toEntity(request, adopterId);
                    return adoptionRequestRepository.persist(entity)
                            .onItem().transform(saved -> Either.<DomainError, AdoptionRequestResponse>right(adoptionMapper.toResponse(saved)));
                });
    }

    @WithTransaction
    public Uni<Either<DomainError, AdoptionRequestResponse>> updateStatus(
            Long id, Long orgId, AdoptionStatus status, String reason) {
        return adoptionRequestRepository.findById(id)
                .onItem().transformToUni(adoption -> {
                    if (adoption == null)
                        return Uni.createFrom().item(Either.left(new NotFoundError("ADOPTION_REQUEST_NOT_FOUND")));
                    if (!adoption.organizationId.equals(orgId))
                        return Uni.createFrom().item(Either.left(new ForbiddenError("ACCESS_DENIED")));
                    adoption.status = status;
                    adoption.rejectionReason = reason;
                    return adoptionRequestRepository.persist(adoption)
                            .onItem().transform(saved -> Either.<DomainError, AdoptionRequestResponse>right(adoptionMapper.toResponse(saved)));
                });
    }

    @WithTransaction
    public Uni<Either<DomainError, AdoptionRequestFormResponse>> submitRequestForm(
            Long adoptionRequestId, AdoptionRequestFormCreateRequest request, Long adopterId) {
        return adoptionRequestRepository.findById(adoptionRequestId)
                .onItem().transformToUni(adoption -> {
                    if (adoption == null)
                        return Uni.createFrom().item(Either.left(new NotFoundError("ADOPTION_REQUEST_NOT_FOUND")));
                    if (!adoption.adopterId.equals(adopterId))
                        return Uni.createFrom().item(Either.left(new ForbiddenError("ACCESS_DENIED")));
                    if (adoption.status != AdoptionStatus.Pending)
                        return Uni.createFrom().item(Either.left(new ConflictError("INVALID_ADOPTION_STATUS")));
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
                });
    }

    @WithTransaction
    public Uni<Either<DomainError, InterviewResponse>> scheduleInterview(
            Long adoptionRequestId, InterviewCreateRequest request, Long organizationId) {
        return adoptionRequestRepository.findById(adoptionRequestId)
                .onItem().transformToUni(adoption -> {
                    if (adoption == null)
                        return Uni.createFrom().item(Either.left(new NotFoundError("ADOPTION_REQUEST_NOT_FOUND")));
                    if (!adoption.organizationId.equals(organizationId))
                        return Uni.createFrom().item(Either.left(new ForbiddenError("ACCESS_DENIED")));
                    if (adoption.status != AdoptionStatus.Accepted)
                        return Uni.createFrom().item(Either.left(new ConflictError("INVALID_ADOPTION_STATUS")));
                    Interview interview = adoptionMapper.toEntity(request, adoptionRequestId);
                    return interviewRepository.persist(interview)
                            .onItem().transform(saved -> Either.<DomainError, InterviewResponse>right(adoptionMapper.toResponse(saved)));
                });
    }

    @WithTransaction
    public Uni<Either<DomainError, AdoptionFormResponse>> submitAdoptionForm(
            Long adoptionRequestId, AdoptionFormCreateRequest request, Long adopterId) {
        return adoptionFormRepository.findByAdoptionRequestId(adoptionRequestId)
                .onItem().transformToUni(existing -> {
                    if (existing != null)
                        return Uni.createFrom().item(Either.left(new ConflictError("ADOPTION_FORM_ALREADY_SUBMITTED")));
                    return adoptionRequestRepository.findById(adoptionRequestId)
                            .onItem().transformToUni(adoption -> {
                                if (adoption == null)
                                    return Uni.createFrom().item(Either.left(new NotFoundError("ADOPTION_REQUEST_NOT_FOUND")));
                                if (!adoption.adopterId.equals(adopterId))
                                    return Uni.createFrom().item(Either.left(new ForbiddenError("ACCESS_DENIED")));
                                if (adoption.status != AdoptionStatus.Accepted)
                                    return Uni.createFrom().item(Either.left(new ConflictError("INVALID_ADOPTION_STATUS")));
                                AdoptionForm form = adoptionMapper.toEntity(request, adoptionRequestId);
                                adoption.status = AdoptionStatus.FormCompleted;
                                return adoptionFormRepository.persist(form)
                                        .onItem().transformToUni(saved ->
                                                adoptionRequestRepository.persist(adoption)
                                                        .onItem().transform(persisted ->
                                                                Either.<DomainError, AdoptionFormResponse>right(adoptionMapper.toResponse(saved)))
                                        );
                            });
                });
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

    @WithTransaction
    public Uni<Void> applyFormAnalysisResult(Long adoptionRequestId, String decision, String rejectionReason) {
        return adoptionRequestRepository.findById(adoptionRequestId)
                .onItem().transformToUni(adoption -> {
                    if (adoption == null) return Uni.createFrom().voidItem();
                    if ("Rejected".equals(decision)) {
                        adoption.status = AdoptionStatus.Rejected;
                        adoption.rejectionReason = rejectionReason;
                    }
                    return adoptionRequestRepository.persist(adoption).replaceWithVoid();
                });
    }
}
