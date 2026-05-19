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
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdoptionWriteServiceTest {

    @Mock AdoptionRequestRepository adoptionRequestRepository;
    @Mock AdoptionRequestFormRepository adoptionRequestFormRepository;
    @Mock AdoptionFormRepository adoptionFormRepository;
    @Mock InterviewRepository interviewRepository;
    @Mock AdoptionMapper adoptionMapper;
    @Mock Emitter<AdoptionFormSubmittedEvent> adoptionFormSubmittedEmitter;

    @InjectMocks
    AdoptionWriteService adoptionWriteService;

    private AdoptionRequest testAdoption;
    private AdoptionRequestResponse testResponse;

    @BeforeEach
    void setUp() {
        testAdoption = new AdoptionRequest();
        testAdoption.id = 1L;
        testAdoption.catId = 10L;
        testAdoption.adopterId = 100L;
        testAdoption.organizationId = 200L;
        testAdoption.status = AdoptionStatus.Pending;

        testResponse = new AdoptionRequestResponse(
                1L, 10L, 100L, 200L,
                AdoptionStatus.Pending, null, null,
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    // --- createRequest ---

    @Test
    void createRequest_catAvailable_returnsRight() {
        var request = new AdoptionRequestCreateRequest(10L, 200L);
        when(adoptionRequestRepository.existsActiveByCatId(10L)).thenReturn(Uni.createFrom().item(false));
        when(adoptionMapper.toEntity(request, 100L)).thenReturn(testAdoption);
        when(adoptionRequestRepository.persist(testAdoption)).thenReturn(Uni.createFrom().item(testAdoption));
        when(adoptionMapper.toResponse(testAdoption)).thenReturn(testResponse);

        var result = adoptionWriteService.createRequest(request, 100L).await().indefinitely();

        assertTrue(result.isRight());
        assertEquals(1L, result.getOrElse(null).id());
    }

    @Test
    void createRequest_catNotAvailable_returnsLeft409() {
        var request = new AdoptionRequestCreateRequest(10L, 200L);
        when(adoptionRequestRepository.existsActiveByCatId(10L)).thenReturn(Uni.createFrom().item(true));

        var result = adoptionWriteService.createRequest(request, 100L).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(ConflictError.class, ((Either.Left<?, ?>) result).value());
    }

    // --- updateStatus ---

    @Test
    void updateStatus_happyPath_returnsRight() {
        when(adoptionRequestRepository.findById(1L)).thenReturn(Uni.createFrom().item(testAdoption));
        when(adoptionRequestRepository.persist(testAdoption)).thenReturn(Uni.createFrom().item(testAdoption));
        when(adoptionMapper.toResponse(testAdoption)).thenReturn(testResponse);

        var result = adoptionWriteService.updateStatus(1L, 200L, AdoptionStatus.Reviewing, null)
                                          .await().indefinitely();

        assertTrue(result.isRight());
        assertEquals(1L, result.getOrElse(null).id());
    }

    @Test
    void updateStatus_notFound_returnsLeft404() {
        when(adoptionRequestRepository.findById(999L)).thenReturn(Uni.createFrom().nullItem());

        var result = adoptionWriteService.updateStatus(999L, 200L, AdoptionStatus.Reviewing, null)
                                          .await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(NotFoundError.class, ((Either.Left<?, ?>) result).value());
        assertEquals(404, result.fold(DomainError::httpStatus, __ -> 0));
    }

    @Test
    void updateStatus_notOwner_returnsLeft403() {
        when(adoptionRequestRepository.findById(1L)).thenReturn(Uni.createFrom().item(testAdoption));

        var result = adoptionWriteService.updateStatus(1L, 999L, AdoptionStatus.Reviewing, null)
                                          .await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(ForbiddenError.class, ((Either.Left<?, ?>) result).value());
    }

    // --- submitRequestForm ---

    @Test
    void submitRequestForm_happyPath_returnsRight() {
        var form = new AdoptionRequestForm();
        when(adoptionRequestRepository.findById(1L)).thenReturn(Uni.createFrom().item(testAdoption));
        when(adoptionMapper.toEntity((AdoptionRequestFormCreateRequest) null, 1L)).thenReturn(form);
        when(adoptionRequestFormRepository.persist(form)).thenReturn(Uni.createFrom().item(form));
        when(adoptionRequestRepository.persist(testAdoption)).thenReturn(Uni.createFrom().item(testAdoption));
        when(adoptionMapper.toResponse(form)).thenReturn(null);

        var result = adoptionWriteService.submitRequestForm(1L, null, 100L).await().indefinitely();

        assertTrue(result.isRight());
        verify(adoptionFormSubmittedEmitter).send(any(AdoptionFormSubmittedEvent.class));
    }

    @Test
    void submitRequestForm_notFound_returnsLeft404() {
        when(adoptionRequestRepository.findById(999L)).thenReturn(Uni.createFrom().nullItem());

        var result = adoptionWriteService.submitRequestForm(999L, null, 100L).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(NotFoundError.class, ((Either.Left<?, ?>) result).value());
    }

    // --- scheduleInterview ---

    @Test
    void scheduleInterview_happyPath_returnsRight() {
        testAdoption.status = AdoptionStatus.Accepted;
        var interview = new Interview();
        when(adoptionRequestRepository.findById(1L)).thenReturn(Uni.createFrom().item(testAdoption));
        when(adoptionMapper.toEntity((InterviewCreateRequest) null, 1L)).thenReturn(interview);
        when(interviewRepository.persist(interview)).thenReturn(Uni.createFrom().item(interview));
        when(adoptionMapper.toResponse(interview)).thenReturn(null);

        var result = adoptionWriteService.scheduleInterview(1L, null, 200L).await().indefinitely();

        assertTrue(result.isRight());
    }

    @Test
    void scheduleInterview_wrongStatus_returnsLeft409() {
        when(adoptionRequestRepository.findById(1L)).thenReturn(Uni.createFrom().item(testAdoption));

        var result = adoptionWriteService.scheduleInterview(1L, null, 200L).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(ConflictError.class, ((Either.Left<?, ?>) result).value());
    }

    // --- submitAdoptionForm ---

    @Test
    void submitAdoptionForm_happyPath_returnsRight() {
        testAdoption.status = AdoptionStatus.Accepted;
        var form = new AdoptionForm();
        when(adoptionFormRepository.findByAdoptionRequestId(1L)).thenReturn(Uni.createFrom().nullItem());
        when(adoptionRequestRepository.findById(1L)).thenReturn(Uni.createFrom().item(testAdoption));
        when(adoptionMapper.toEntity((AdoptionFormCreateRequest) null, 1L)).thenReturn(form);
        when(adoptionFormRepository.persist(form)).thenReturn(Uni.createFrom().item(form));
        when(adoptionRequestRepository.persist(testAdoption)).thenReturn(Uni.createFrom().item(testAdoption));
        when(adoptionMapper.toResponse(form)).thenReturn(null);

        var result = adoptionWriteService.submitAdoptionForm(1L, null, 100L).await().indefinitely();

        assertTrue(result.isRight());
    }

    @Test
    void submitAdoptionForm_alreadySubmitted_returnsLeft409() {
        when(adoptionFormRepository.findByAdoptionRequestId(1L))
                .thenReturn(Uni.createFrom().item(new AdoptionForm()));

        var result = adoptionWriteService.submitAdoptionForm(1L, null, 100L).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(ConflictError.class, ((Either.Left<?, ?>) result).value());
        assertEquals("ADOPTION_FORM_ALREADY_SUBMITTED",
                ((ConflictError) ((Either.Left<?, ?>) result).value()).code());
    }

    // --- applyFormAnalysisResult ---

    @Test
    void applyFormAnalysisResult_rejected_updatesStatusAndPersists() {
        when(adoptionRequestRepository.findById(1L)).thenReturn(Uni.createFrom().item(testAdoption));
        when(adoptionRequestRepository.persist(testAdoption)).thenReturn(Uni.createFrom().item(testAdoption));

        adoptionWriteService.applyFormAnalysisResult(1L, "REJECTED", "not suitable").await().indefinitely();

        assertEquals(AdoptionStatus.Rejected, testAdoption.status);
        assertEquals("not suitable", testAdoption.rejectionReason);
        verify(adoptionRequestRepository).persist(testAdoption);
    }

    @Test
    void applyFormAnalysisResult_notRejected_persistsWithoutStatusChange() {
        when(adoptionRequestRepository.findById(1L)).thenReturn(Uni.createFrom().item(testAdoption));
        when(adoptionRequestRepository.persist(testAdoption)).thenReturn(Uni.createFrom().item(testAdoption));

        adoptionWriteService.applyFormAnalysisResult(1L, "ACCEPTED", null).await().indefinitely();

        assertEquals(AdoptionStatus.Pending, testAdoption.status);
        verify(adoptionRequestRepository).persist(testAdoption);
    }

    @Test
    void applyFormAnalysisResult_notFound_returnsVoid() {
        when(adoptionRequestRepository.findById(999L)).thenReturn(Uni.createFrom().nullItem());

        adoptionWriteService.applyFormAnalysisResult(999L, "REJECTED", "reason").await().indefinitely();

        verify(adoptionRequestRepository, never()).persist(any(AdoptionRequest.class));
    }
}
