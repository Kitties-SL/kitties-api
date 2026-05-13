package es.kitti.adoption.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.kitti.mon.either.Either;
import es.kitti.mon.error.ConflictError;
import es.kitti.mon.error.DomainError;
import es.kitti.mon.error.ForbiddenError;
import es.kitti.mon.error.NotFoundError;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;
import es.kitti.adoption.client.CatClient;
import es.kitti.adoption.dto.*;
import es.kitti.adoption.entity.*;
import es.kitti.adoption.mapper.AdoptionMapper;
import es.kitti.adoption.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdoptionServiceTest {

    @Mock ObjectMapper objectMapper;
    @Mock AdoptionRequestRepository adoptionRequestRepository;
    @Mock AdoptionRequestFormRepository adoptionRequestFormRepository;
    @Mock AdoptionFormRepository adoptionFormRepository;
    @Mock InterviewRepository interviewRepository;
    @Mock ExpenseRepository expenseRepository;
    @Mock AdoptionMapper adoptionMapper;
    @Mock CatClient catClient;
    @Mock AdoptionWriteService adoptionWriteService;

    @InjectMocks
    AdoptionService adoptionService;

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

    // --- createAdoptionRequest ---

    @Test
    void createAdoptionRequest_catActive_returnsRight() {
        var request = new AdoptionRequestCreateRequest(10L, 200L);
        when(catClient.findById(10L)).thenReturn(Uni.createFrom().item(Response.ok().build()));
        when(adoptionWriteService.createRequest(request, 100L))
                .thenReturn(Uni.createFrom().item(Either.right(testResponse)));

        var result = adoptionService.createAdoptionRequest(request, 100L).await().indefinitely();

        assertTrue(result.isRight());
        assertEquals(1L, result.getOrElse(null).id());
    }

    @Test
    void createAdoptionRequest_catNotAvailable_returnsLeft409() {
        var request = new AdoptionRequestCreateRequest(10L, 200L);
        when(catClient.findById(10L))
                .thenReturn(Uni.createFrom().item(Response.status(404).build()));

        var result = adoptionService.createAdoptionRequest(request, 100L).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(ConflictError.class, ((Either.Left<?, ?>) result).value());
        assertEquals(409, result.fold(DomainError::httpStatus, __ -> 0));
    }

    // --- findById ---

    @Test
    void findById_asAdopter_returnsRight() {
        when(adoptionRequestRepository.findById(1L)).thenReturn(Uni.createFrom().item(testAdoption));
        when(adoptionMapper.toResponse(testAdoption)).thenReturn(testResponse);

        var result = adoptionService.findById(1L, 100L).await().indefinitely();

        assertTrue(result.isRight());
        assertEquals(1L, result.getOrElse(null).id());
    }

    @Test
    void findById_asOrganization_returnsRight() {
        when(adoptionRequestRepository.findById(1L)).thenReturn(Uni.createFrom().item(testAdoption));
        when(adoptionMapper.toResponse(testAdoption)).thenReturn(testResponse);

        var result = adoptionService.findById(1L, 200L).await().indefinitely();

        assertTrue(result.isRight());
        assertEquals(1L, result.getOrElse(null).id());
    }

    @Test
    void findById_thirdParty_returnsLeft403() {
        when(adoptionRequestRepository.findById(1L)).thenReturn(Uni.createFrom().item(testAdoption));

        var result = adoptionService.findById(1L, 999L).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(ForbiddenError.class, ((Either.Left<?, ?>) result).value());
        assertEquals(403, result.fold(DomainError::httpStatus, __ -> 0));
    }

    @Test
    void findById_notFound_returnsLeft404() {
        when(adoptionRequestRepository.findById(999L)).thenReturn(Uni.createFrom().nullItem());

        var result = adoptionService.findById(999L, 100L).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(NotFoundError.class, ((Either.Left<?, ?>) result).value());
        assertEquals(404, result.fold(DomainError::httpStatus, __ -> 0));
    }

    // --- updateStatus ---

    @Test
    void updateStatus_terminal_returnsRight() {
        var req = new AdoptionStatusUpdateRequest(AdoptionStatus.Rejected, "not a match");
        when(adoptionRequestRepository.findById(1L)).thenReturn(Uni.createFrom().item(testAdoption));
        when(adoptionWriteService.updateStatus(1L, 200L, AdoptionStatus.Rejected, "not a match"))
                .thenReturn(Uni.createFrom().item(Either.right(testResponse)));

        var result = adoptionService.updateStatus(1L, req, 200L).await().indefinitely();

        assertTrue(result.isRight());
        verifyNoInteractions(catClient);
    }

    @Test
    void updateStatus_nonTerminal_catActive_returnsRight() {
        var req = new AdoptionStatusUpdateRequest(AdoptionStatus.Reviewing, null);
        when(adoptionRequestRepository.findById(1L)).thenReturn(Uni.createFrom().item(testAdoption));
        when(catClient.findById(10L)).thenReturn(Uni.createFrom().item(Response.ok().build()));
        when(adoptionWriteService.updateStatus(1L, 200L, AdoptionStatus.Reviewing, null))
                .thenReturn(Uni.createFrom().item(Either.right(testResponse)));

        var result = adoptionService.updateStatus(1L, req, 200L).await().indefinitely();

        assertTrue(result.isRight());
    }

    @Test
    void updateStatus_nonTerminal_catNotActive_returnsLeft409() {
        var req = new AdoptionStatusUpdateRequest(AdoptionStatus.Reviewing, null);

        when(adoptionRequestRepository.findById(1L))
                .thenReturn(Uni.createFrom().item(testAdoption));
        when(catClient.findById(10L))
                .thenReturn(Uni.createFrom().item(Response.status(404).build()));

        var result = adoptionService.updateStatus(1L, req, 200L).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(ConflictError.class, ((Either.Left<?, ?>) result).value());
        assertEquals(409, result.fold(DomainError::httpStatus, __ -> 0));
    }

    @Test
    void updateStatus_notOwner_returnsLeft403() {
        var req = new AdoptionStatusUpdateRequest(AdoptionStatus.Reviewing, null);

        when(adoptionRequestRepository.findById(1L))
                .thenReturn(Uni.createFrom().item(testAdoption));

        var result = adoptionService.updateStatus(1L, req, 999L).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(ForbiddenError.class, ((Either.Left<?, ?>) result).value());
        assertEquals(403, result.fold(DomainError::httpStatus, __ -> 0));
    }

    // --- submitRequestForm ---

    @Test
    void submitRequestForm_catActive_returnsRight() {
        when(adoptionRequestRepository.findById(1L)).thenReturn(Uni.createFrom().item(testAdoption));
        when(catClient.findById(10L)).thenReturn(Uni.createFrom().item(Response.ok().build()));
        when(adoptionWriteService.submitRequestForm(eq(1L), any(), eq(100L)))
                .thenReturn(Uni.createFrom().item(Either.right(null)));

        var result = adoptionService.submitRequestForm(1L, null, 100L).await().indefinitely();

        assertTrue(result.isRight());
    }

    @Test
    void submitRequestForm_wrongAdopter_returnsLeft403() {
        when(adoptionRequestRepository.findById(1L)).thenReturn(Uni.createFrom().item(testAdoption));

        var result = adoptionService.submitRequestForm(1L, null, 999L).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(ForbiddenError.class, ((Either.Left<?, ?>) result).value());
    }

    // --- scheduleInterview ---

    @Test
    void scheduleInterview_catActive_returnsRight() {
        testAdoption.status = AdoptionStatus.Accepted;
        when(adoptionRequestRepository.findById(1L)).thenReturn(Uni.createFrom().item(testAdoption));
        when(catClient.findById(10L)).thenReturn(Uni.createFrom().item(Response.ok().build()));
        when(adoptionWriteService.scheduleInterview(eq(1L), any(), eq(200L)))
                .thenReturn(Uni.createFrom().item(Either.right(null)));

        var result = adoptionService.scheduleInterview(1L, null, 200L).await().indefinitely();

        assertTrue(result.isRight());
    }

    @Test
    void scheduleInterview_wrongStatus_returnsLeft409() {
        when(adoptionRequestRepository.findById(1L)).thenReturn(Uni.createFrom().item(testAdoption));

        var result = adoptionService.scheduleInterview(1L, null, 200L).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(ConflictError.class, ((Either.Left<?, ?>) result).value());
    }

    // --- submitAdoptionForm ---

    @Test
    void submitAdoptionForm_catActive_returnsRight() {
        testAdoption.status = AdoptionStatus.Accepted;
        when(adoptionRequestRepository.findById(1L)).thenReturn(Uni.createFrom().item(testAdoption));
        when(catClient.findById(10L)).thenReturn(Uni.createFrom().item(Response.ok().build()));
        when(adoptionWriteService.submitAdoptionForm(eq(1L), any(), eq(100L)))
                .thenReturn(Uni.createFrom().item(Either.right(null)));

        var result = adoptionService.submitAdoptionForm(1L, null, 100L).await().indefinitely();

        assertTrue(result.isRight());
    }

    @Test
    void submitAdoptionForm_wrongAdopter_returnsLeft403() {
        testAdoption.status = AdoptionStatus.Accepted;
        when(adoptionRequestRepository.findById(1L)).thenReturn(Uni.createFrom().item(testAdoption));

        var result = adoptionService.submitAdoptionForm(1L, null, 999L).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(ForbiddenError.class, ((Either.Left<?, ?>) result).value());
    }

    // --- exportByAdopterId ---

    @Test
    void exportByAdopterId_empty_returnsEmptyExport() {
        when(adoptionRequestRepository.findByAdopterId(100L))
                .thenReturn(Uni.createFrom().item(List.of()));

        var result = adoptionService.exportByAdopterId(100L).await().indefinitely();

        assertTrue(result.adoptionRequests().isEmpty());
    }

    @Test
    void exportByAdopterId_withRequest_returnsExportEntry() {
        when(adoptionRequestRepository.findByAdopterId(100L))
                .thenReturn(Uni.createFrom().item(List.of(testAdoption)));
        when(adoptionRequestFormRepository.findByAdoptionRequestId(1L))
                .thenReturn(Uni.createFrom().nullItem());
        when(adoptionFormRepository.findByAdoptionRequestId(1L))
                .thenReturn(Uni.createFrom().nullItem());
        when(interviewRepository.findByAdoptionRequestId(1L))
                .thenReturn(Uni.createFrom().item(List.of()));
        when(expenseRepository.findByAdoptionRequestId(1L))
                .thenReturn(Uni.createFrom().item(List.of()));
        when(adoptionMapper.toResponse(testAdoption)).thenReturn(testResponse);

        var result = adoptionService.exportByAdopterId(100L).await().indefinitely();

        assertEquals(1, result.adoptionRequests().size());
        assertEquals(1L, result.adoptionRequests().get(0).request().id());
        assertNull(result.adoptionRequests().get(0).requestForm());
        assertNull(result.adoptionRequests().get(0).adoptionForm());
        assertTrue(result.adoptionRequests().get(0).interviews().isEmpty());
        assertTrue(result.adoptionRequests().get(0).expenses().isEmpty());
    }

    // --- pure reads ---

    @Test
    void findByAdopterId_returnsListOfResponses() {
        when(adoptionRequestRepository.findByAdopterId(100L))
                .thenReturn(Uni.createFrom().item(List.of(testAdoption)));
        when(adoptionMapper.toResponse(testAdoption)).thenReturn(testResponse);

        var result = adoptionService.findByAdopterId(100L).await().indefinitely();

        assertEquals(1, result.size());
    }

    @Test
    void findByOrganizationId_returnsListOfResponses() {
        when(adoptionRequestRepository.findByOrganizationId(200L))
                .thenReturn(Uni.createFrom().item(List.of(testAdoption)));
        when(adoptionMapper.toResponse(testAdoption)).thenReturn(testResponse);

        var result = adoptionService.findByOrganizationId(200L).await().indefinitely();

        assertEquals(1, result.size());
    }
}
