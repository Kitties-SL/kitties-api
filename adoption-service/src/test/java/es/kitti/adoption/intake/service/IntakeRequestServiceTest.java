package es.kitti.adoption.intake.service;

import es.kitti.adoption.client.CatClient;
import es.kitti.adoption.client.dto.CatCreateInternalRequest;
import es.kitti.adoption.client.dto.CatResponse;
import es.kitti.adoption.intake.client.OrganizationClient;
import es.kitti.adoption.intake.client.OrganizationPublicMinimal;
import es.kitti.adoption.intake.dto.IntakeApproveRequest;
import es.kitti.adoption.intake.dto.IntakeDecisionRequest;
import es.kitti.adoption.intake.dto.IntakeRequestCreateRequest;
import es.kitti.adoption.intake.dto.IntakeRequestResponse;
import es.kitti.adoption.intake.entity.IntakeRequest;
import es.kitti.adoption.intake.entity.IntakeStatus;
import es.kitti.adoption.intake.mapper.IntakeMapper;
import es.kitti.adoption.intake.repository.IntakeRequestRepository;
import es.kitti.mon.either.Either;
import es.kitti.mon.error.ConflictError;
import es.kitti.mon.error.DomainError;
import es.kitti.mon.error.ForbiddenError;
import es.kitti.mon.error.NotFoundError;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntakeRequestServiceTest {

    @Mock IntakeRequestRepository repository;
    @Mock IntakeMapper mapper;
    @Mock OrganizationClient organizationClient;
    @Mock CatClient catClient;

    @InjectMocks
    IntakeRequestService service;

    private IntakeRequest pending;
    private IntakeRequestResponse pendingResponse;

    @BeforeEach
    void setUp() {
        service.internalSecret = "test-secret";

        pending = new IntakeRequest();
        pending.id = 1L;
        pending.userId = 100L;
        pending.targetOrganizationId = 200L;
        pending.catName = "Mishi";
        pending.catAge = 3;
        pending.region = "Santa Cruz de Tenerife";
        pending.city = "La Orotava";
        pending.vaccinated = true;
        pending.status = IntakeStatus.Pending;
        pending.createdAt = LocalDateTime.now();

        pendingResponse = new IntakeRequestResponse(
                1L, 100L, 200L, "Mishi", 3,
                "Santa Cruz de Tenerife", "La Orotava", true,
                null, IntakeStatus.Pending, null,
                pending.createdAt, null
        );
    }

    // --- create ---

    @Test
    void create_success() {
        var request = new IntakeRequestCreateRequest(200L, "Mishi", 3,
                "Santa Cruz de Tenerife", "La Orotava", true, null);

        when(mapper.toEntity(request, 100L)).thenReturn(pending);
        when(repository.persist(any(IntakeRequest.class))).thenReturn(Uni.createFrom().item(pending));
        when(mapper.toResponse(pending)).thenReturn(pendingResponse);

        var result = service.create(request, 100L).await().indefinitely();

        assertEquals(IntakeStatus.Pending, result.status());
        assertEquals(100L, result.userId());
    }

    // --- approve ---

    @Test
    void approve_pendingAndOwner_returnsRight() {
        var approveReq = new IntakeApproveRequest("Female", "ES", null, null, null, null, null, true);
        var catResp = new CatResponse(42L, "Mishi", 3, "Female", null, true, "Available",
                "La Orotava", "Santa Cruz de Tenerife", "ES", 200L, LocalDateTime.now());

        when(repository.findById(1L)).thenReturn(Uni.createFrom().item(pending));
        when(mapper.toCatCreateInternal(any(IntakeRequest.class), any(IntakeApproveRequest.class)))
                .thenReturn(new CatCreateInternalRequest("Mishi", 3, "Female", null, true,
                        "La Orotava", "Santa Cruz de Tenerife", "ES", null, null, 200L));
        when(catClient.createInternal(any(CatCreateInternalRequest.class), eq("test-secret")))
                .thenReturn(Uni.createFrom().item(catResp));
        doReturn(Uni.createFrom().item(pending)).when(repository).persist(any(IntakeRequest.class));
        when(mapper.toResponse(any())).thenReturn(pendingResponse);

        var result = service.approve(1L, approveReq, 200L).await().indefinitely();

        assertTrue(result.isRight());
        assertEquals(IntakeStatus.Approved, pending.status);
        assertNotNull(pending.decidedAt);
        assertEquals(42L, result.getOrElse(null).createdCat().id());
    }

    @Test
    void approve_catServiceFails_returnsLeft409() {
        var approveReq = new IntakeApproveRequest("Female", "ES", null, null, null, null, null, true);

        when(repository.findById(1L)).thenReturn(Uni.createFrom().item(pending));
        when(mapper.toCatCreateInternal(any(IntakeRequest.class), any(IntakeApproveRequest.class)))
                .thenReturn(new CatCreateInternalRequest("Mishi", 3, "Female", null, true,
                        "La Orotava", "Santa Cruz de Tenerife", "ES", null, null, 200L));
        when(catClient.createInternal(any(CatCreateInternalRequest.class), eq("test-secret")))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("cat-service down")));

        var result = service.approve(1L, approveReq, 200L).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(ConflictError.class, ((Either.Left<?, ?>) result).value());
        assertEquals(IntakeStatus.Pending, pending.status);
    }

    @Test
    void approve_notOwner_returnsLeft403() {
        var approveReq = new IntakeApproveRequest("Female", "ES", null, null, null, null, null, true);
        when(repository.findById(1L)).thenReturn(Uni.createFrom().item(pending));

        var result = service.approve(1L, approveReq, 999L).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(ForbiddenError.class, ((Either.Left<?, ?>) result).value());
        assertEquals(403, result.fold(DomainError::httpStatus, __ -> 0));
    }

    @Test
    void approve_notFound_returnsLeft404() {
        var approveReq = new IntakeApproveRequest("Female", "ES", null, null, null, null, null, true);
        when(repository.findById(999L)).thenReturn(Uni.createFrom().nullItem());

        var result = service.approve(999L, approveReq, 200L).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(NotFoundError.class, ((Either.Left<?, ?>) result).value());
        assertEquals(404, result.fold(DomainError::httpStatus, __ -> 0));
    }

    @Test
    void approve_notPending_returnsLeft409() {
        var approveReq = new IntakeApproveRequest("Female", "ES", null, null, null, null, null, true);
        pending.status = IntakeStatus.Approved;
        when(repository.findById(1L)).thenReturn(Uni.createFrom().item(pending));

        var result = service.approve(1L, approveReq, 200L).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(ConflictError.class, ((Either.Left<?, ?>) result).value());
        assertEquals(409, result.fold(DomainError::httpStatus, __ -> 0));
    }

    // --- reject ---

    @Test
    void reject_pendingAndOwner_returnsRightWithFilteredAlternatives() {
        var decision = new IntakeDecisionRequest("Out of capacity");
        var rejectingOrg = new OrganizationPublicMinimal(200L, "Rejecting Org", "La Orotava", "SC Tenerife", "+34", "r@kitti.es");
        var alt         = new OrganizationPublicMinimal(201L, "Other Org",    "La Laguna",  "SC Tenerife", "+34", "o@kitti.es");

        when(repository.findById(1L)).thenReturn(Uni.createFrom().item(pending));
        doReturn(Uni.createFrom().item(pending)).when(repository).persist(any(IntakeRequest.class));
        when(mapper.toResponse(any())).thenReturn(pendingResponse);
        when(organizationClient.findByRegion(eq("Santa Cruz de Tenerife"), eq("test-secret")))
                .thenReturn(Uni.createFrom().item(List.of(rejectingOrg, alt)));

        var result = service.reject(1L, decision, 200L).await().indefinitely();

        assertTrue(result.isRight());
        assertEquals(IntakeStatus.Rejected, pending.status);
        assertEquals("Out of capacity", pending.rejectionReason);
        assertEquals(1, result.getOrElse(null).alternatives().size(), "rejecting org must be excluded");
        assertEquals(201L, result.getOrElse(null).alternatives().get(0).id());
    }

    @Test
    void reject_clientFails_returnsRightWithEmptyAlternatives() {
        var decision = new IntakeDecisionRequest("nope");

        when(repository.findById(1L)).thenReturn(Uni.createFrom().item(pending));
        doReturn(Uni.createFrom().item(pending)).when(repository).persist(any(IntakeRequest.class));
        when(mapper.toResponse(any())).thenReturn(pendingResponse);
        when(organizationClient.findByRegion(any(), any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("organization-service down")));

        var result = service.reject(1L, decision, 200L).await().indefinitely();

        assertTrue(result.isRight());
        assertTrue(result.getOrElse(null).alternatives().isEmpty());
    }

    @Test
    void reject_notOwner_returnsLeft403() {
        when(repository.findById(1L)).thenReturn(Uni.createFrom().item(pending));

        var result = service.reject(1L, new IntakeDecisionRequest("nope"), 999L).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(ForbiddenError.class, ((Either.Left<?, ?>) result).value());
    }

    @Test
    void reject_notPending_returnsLeft409() {
        pending.status = IntakeStatus.Rejected;
        when(repository.findById(1L)).thenReturn(Uni.createFrom().item(pending));

        var result = service.reject(1L, new IntakeDecisionRequest("done"), 200L).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(ConflictError.class, ((Either.Left<?, ?>) result).value());
    }
}
