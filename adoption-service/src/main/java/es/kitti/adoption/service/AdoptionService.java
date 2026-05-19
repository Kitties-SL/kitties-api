package es.kitti.adoption.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.kitti.adoption.client.CatClient;
import es.kitti.adoption.dto.*;
import es.kitti.adoption.entity.AdoptionRequest;
import es.kitti.adoption.entity.AdoptionStatus;
import es.kitti.adoption.event.AdoptionFormAnalysedEvent;
import es.kitti.adoption.mapper.AdoptionMapper;
import es.kitti.adoption.repository.*;
import es.kitti.mon.either.Either;
import es.kitti.mon.either.Unit;
import es.kitti.mon.error.ConflictError;
import es.kitti.mon.error.DomainError;
import es.kitti.mon.error.ForbiddenError;
import es.kitti.mon.error.NotFoundError;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class AdoptionService {

    @Inject ObjectMapper objectMapper;
    @Inject AdoptionRequestRepository adoptionRequestRepository;
    @Inject AdoptionRequestFormRepository adoptionRequestFormRepository;
    @Inject AdoptionFormRepository adoptionFormRepository;
    @Inject InterviewRepository interviewRepository;
    @Inject ExpenseRepository expenseRepository;
    @Inject AdoptionMapper adoptionMapper;
    @Inject AdoptionWriteService adoptionWriteService;

    @RestClient CatClient catClient;

    public Uni<Either<DomainError, AdoptionRequestResponse>> createAdoptionRequest(
            AdoptionRequestCreateRequest request, Long adopterId) {

        return verifyCatActive(request.catId())
                .onItem().transformToUni(catEither -> catEither.fold(
                        err -> Uni.createFrom().item(Either.left(err)),
                        v   -> adoptionWriteService.createRequest(request, adopterId)
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
    public Uni<Either<DomainError, AdoptionRequestFormResponse>> findFormByIdForOrg(Long id, Long orgId) {
        return findAdoptionOrNotFound(id)
                .onItem().transformToUni(either -> either.fold(
                        err -> Uni.createFrom().item(Either.left(err)),
                        adoption -> checkOrganizationOwner(adoption, orgId)
                                .fold(
                                        err -> Uni.createFrom().item(Either.<DomainError, AdoptionRequestFormResponse>left(err)),
                                        v   -> adoptionRequestFormRepository.findByAdoptionRequestId(id)
                                                .onItem().transform(form -> form == null
                                                        ? Either.<DomainError, AdoptionRequestFormResponse>left(new NotFoundError("ADOPTION_FORM_NOT_FOUND"))
                                                        : Either.<DomainError, AdoptionRequestFormResponse>right(adoptionMapper.toResponse(form)))
                                )
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

    @WithSession
    public Uni<Either<DomainError, AdoptionRequestResponse>> updateStatus(
            Long id, AdoptionStatusUpdateRequest request, Long userId) {

        boolean isTerminal = request.status() == AdoptionStatus.Rejected
                || request.status() == AdoptionStatus.Completed;

        return findAdoptionOrNotFound(id)
                .onItem().transform(either -> either.flatMap(adoption ->
                        checkOrganizationOwner(adoption, userId).map(v -> adoption.catId)
                ))
                .onItem().transformToUni(either -> either.fold(
                        err   -> Uni.createFrom().item(Either.left(err)),
                        catId -> isTerminal
                                ? Uni.createFrom().item(Either.<DomainError>unit())
                                : verifyCatActive(catId)
                ))
                .onItem().transformToUni(either -> either.fold(
                        err -> Uni.createFrom().item(Either.left(err)),
                        v   -> adoptionWriteService.updateStatus(id, userId, request.status(), request.reason())
                ));
    }

    @WithSession
    public Uni<Either<DomainError, AdoptionRequestFormResponse>> submitRequestForm(
            Long adoptionRequestId, AdoptionRequestFormCreateRequest request, Long adopterId) {

        return findAdoptionOrNotFound(adoptionRequestId)
                .onItem().transform(either -> either.flatMap(adoption ->
                        checkAdopter(adoption, adopterId)
                                .flatMap(v -> checkStatus(adoption, AdoptionStatus.Pending))
                                .map(v -> adoption.catId)
                ))
                .onItem().transformToUni(either -> either.fold(
                        err   -> Uni.createFrom().item(Either.left(err)),
                        this::verifyCatActive
                ))
                .onItem().transformToUni(either -> either.fold(
                        err -> Uni.createFrom().item(Either.left(err)),
                        v   -> adoptionWriteService.submitRequestForm(adoptionRequestId, request, adopterId)
                ));
    }

    @WithSession
    public Uni<Either<DomainError, InterviewResponse>> scheduleInterview(
            Long adoptionRequestId, InterviewCreateRequest request, Long organizationId) {

        return findAdoptionOrNotFound(adoptionRequestId)
                .onItem().transform(either -> either.flatMap(adoption ->
                        checkOrganizationOwner(adoption, organizationId)
                                .flatMap(v -> checkStatus(adoption, AdoptionStatus.Accepted))
                                .map(v -> adoption.catId)
                ))
                .onItem().transformToUni(either -> either.fold(
                        err   -> Uni.createFrom().item(Either.left(err)),
                        this::verifyCatActive
                ))
                .onItem().transformToUni(either -> either.fold(
                        err -> Uni.createFrom().item(Either.left(err)),
                        v   -> adoptionWriteService.scheduleInterview(adoptionRequestId, request, organizationId)
                ));
    }

    @WithSession
    public Uni<Either<DomainError, AdoptionFormResponse>> submitAdoptionForm(
            Long adoptionRequestId, AdoptionFormCreateRequest request, Long adopterId) {

        return findAdoptionOrNotFound(adoptionRequestId)
                .onItem().transform(either -> either.flatMap(adoption ->
                        checkAdopter(adoption, adopterId)
                                .flatMap(v -> checkStatus(adoption, AdoptionStatus.Accepted))
                                .map(v -> adoption.catId)
                ))
                .onItem().transformToUni(either -> either.fold(
                        err   -> Uni.createFrom().item(Either.left(err)),
                        this::verifyCatActive
                ))
                .onItem().transformToUni(either -> either.fold(
                        err -> Uni.createFrom().item(Either.left(err)),
                        v   -> adoptionWriteService.submitAdoptionForm(adoptionRequestId, request, adopterId)
                ));
    }

    @WithSession
    public Uni<AdoptionDataExport> exportByAdopterId(Long adopterId) {
        return adoptionRequestRepository.findByAdopterId(adopterId)
                .onItem().transformToUni(requests -> {
                    if (requests.isEmpty())
                        return Uni.createFrom().item(new AdoptionDataExport(List.of()));
                    return Multi.createFrom().iterable(requests)
                            .onItem().transformToUniAndConcatenate(this::buildExportEntry)
                            .collect().asList()
                            .onItem().transform(AdoptionDataExport::new);
                });
    }

    private Uni<AdoptionExportEntry> buildExportEntry(AdoptionRequest r) {
        return adoptionRequestFormRepository.findByAdoptionRequestId(r.id)
                .onItem().transformToUni(reqForm ->
                adoptionFormRepository.findByAdoptionRequestId(r.id)
                        .onItem().transformToUni(adoptForm ->
                interviewRepository.findByAdoptionRequestId(r.id)
                        .onItem().transformToUni(interviews ->
                expenseRepository.findByAdoptionRequestId(r.id)
                        .onItem().transform(expenses -> new AdoptionExportEntry(
                                adoptionMapper.toResponse(r),
                                reqForm   != null ? adoptionMapper.toResponse(reqForm)   : null,
                                adoptForm != null ? adoptionMapper.toResponse(adoptForm) : null,
                                interviews.stream().map(adoptionMapper::toResponse).toList(),
                                expenses.stream().map(adoptionMapper::toResponse).toList()
                        )))));
    }

    @Incoming("adoption-form-analysed")
    public Uni<Void> onFormAnalysed(String message) {
        try {
            AdoptionFormAnalysedEvent event = objectMapper.readValue(message, AdoptionFormAnalysedEvent.class);
            return adoptionWriteService.applyFormAnalysisResult(
                    event.adoptionRequestId(), event.decision(), event.rejectionReason());
        } catch (Exception e) {
            Log.errorf(e, "Error procesando adoption-form-analysed: %s", message);
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

    private Either<DomainError, Unit> checkOrganizationOwner(AdoptionRequest adoption, Long orgId) {
        return adoption.organizationId.equals(orgId)
                ? Either.unit()
                : Either.left(new ForbiddenError("ACCESS_DENIED"));
    }

    private Either<DomainError, Unit> checkAdopter(AdoptionRequest adoption, Long adopterId) {
        return adoption.adopterId.equals(adopterId)
                ? Either.unit()
                : Either.left(new ForbiddenError("ACCESS_DENIED"));
    }

    private Either<DomainError, Unit> checkStatus(AdoptionRequest adoption, AdoptionStatus required) {
        return adoption.status == required
                ? Either.unit()
                : Either.left(new ConflictError("INVALID_ADOPTION_STATUS"));
    }

    private Either<DomainError, Unit> checkParticipant(AdoptionRequest adoption, Long callerId) {
        return adoption.adopterId.equals(callerId) || adoption.organizationId.equals(callerId)
                ? Either.unit()
                : Either.left(new ForbiddenError("ACCESS_DENIED"));
    }

    private Uni<Either<DomainError, Unit>> verifyCatActive(Long catId) {
        return catClient.findById(catId)
                .onFailure(jakarta.ws.rs.WebApplicationException.class)
                .recoverWithItem(e -> ((jakarta.ws.rs.WebApplicationException) e).getResponse())
                .onItem().transform(response -> response.getStatus() == 200
                        ? Either.<DomainError>unit()
                        : Either.<DomainError, Unit>left(new ConflictError("CAT_NOT_AVAILABLE")))
                .onFailure(org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException.class)
                .recoverWithItem(__ -> {
                    Log.warnf("cat-service circuit breaker OPEN — catId=%d", catId);
                    return Either.<DomainError, Unit>left(new ConflictError("CAT_SERVICE_UNAVAILABLE"));
                });
    }
}
