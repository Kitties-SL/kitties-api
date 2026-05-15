package es.kitti.user.service;

import es.kitti.mon.either.Either;
import es.kitti.mon.error.ConflictError;
import es.kitti.mon.error.DomainError;
import es.kitti.mon.error.NotFoundError;
import es.kitti.user.client.AdoptionInternalClient;
import es.kitti.user.client.AuthInternalClient;
import es.kitti.user.client.ChatInternalClient;
import es.kitti.user.entity.ErasureRequest;
import es.kitti.user.entity.User;
import es.kitti.user.entity.UserStatus;
import es.kitti.user.repository.ErasureRequestRepository;
import es.kitti.user.repository.UserRepository;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ErasureServiceTest {

    @Mock UserRepository userRepository;
    @Mock ErasureRequestRepository erasureRequestRepository;
    @Mock ErasureWriteService erasureWriteService;
    @Mock AuthInternalClient authInternalClient;
    @Mock AdoptionInternalClient adoptionInternalClient;
    @Mock ChatInternalClient chatInternalClient;

    @InjectMocks
    ErasureService erasureService;

    private User testUser;
    private ErasureRequest testErasureRequest;

    @BeforeEach
    void setUp() throws Exception {
        var field = ErasureService.class.getDeclaredField("internalSecret");
        field.setAccessible(true);
        field.set(erasureService, "test-secret");

        testUser = new User();
        testUser.id = 1L;
        testUser.status = UserStatus.Active;
        testUser.legalHoldUntil = null;

        testErasureRequest = new ErasureRequest();
        testErasureRequest.userId = 1L;
        testErasureRequest.scheduledPurgeAt = LocalDateTime.now().minusDays(1);
        testErasureRequest.blockedByHold = false;
    }

    // --- requestErasure ---

    @Test
    void requestErasure_userExists_noHold_returnsRight() {
        when(userRepository.findById(1L)).thenReturn(Uni.createFrom().item(testUser));
        when(erasureWriteService.createErasureRequest(any(User.class), any(ErasureRequest.class)))
                .thenReturn(Uni.createFrom().voidItem());
        when(authInternalClient.deleteTokensByUser(eq(1L), anyString()))
                .thenReturn(Uni.createFrom().item(Response.ok().build()));

        var result = erasureService.requestErasure(1L, "1.2.3.4").await().indefinitely();

        assertTrue(result.isRight());
        assertEquals(UserStatus.Inactive, testUser.status);
        assertNotNull(testUser.deletedAt);
        assertNotNull(testUser.scheduledPurgeAt);
        verify(erasureWriteService).createErasureRequest(eq(testUser), any(ErasureRequest.class));
        verify(authInternalClient).deleteTokensByUser(1L, "test-secret");
    }

    @Test
    void requestErasure_legalHoldActive_returnsLeft409() {
        testUser.legalHoldUntil = LocalDateTime.now().plusDays(10);
        when(userRepository.findById(1L)).thenReturn(Uni.createFrom().item(testUser));

        var result = erasureService.requestErasure(1L, "1.2.3.4").await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(ConflictError.class, ((Either.Left<?, ?>) result).value());
        assertEquals("LEGAL_HOLD_ACTIVE", ((ConflictError) ((Either.Left<?, ?>) result).value()).code());
        verify(erasureWriteService, never()).createErasureRequest(any(), any());
        verify(authInternalClient, never()).deleteTokensByUser(anyLong(), anyString());
    }

    @Test
    void requestErasure_userNotFound_returnsLeft404() {
        when(userRepository.findById(99L)).thenReturn(Uni.createFrom().nullItem());

        var result = erasureService.requestErasure(99L, "1.2.3.4").await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(NotFoundError.class, ((Either.Left<?, ?>) result).value());
        assertEquals(404, result.fold(DomainError::httpStatus, __ -> 0));
        verify(erasureWriteService, never()).createErasureRequest(any(), any());
    }

    @Test
    void requestErasure_authClientFails_stillReturnsRight() {
        when(userRepository.findById(1L)).thenReturn(Uni.createFrom().item(testUser));
        when(erasureWriteService.createErasureRequest(any(), any()))
                .thenReturn(Uni.createFrom().voidItem());
        when(authInternalClient.deleteTokensByUser(eq(1L), anyString()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("auth down")));

        var result = erasureService.requestErasure(1L, "1.2.3.4").await().indefinitely();

        assertTrue(result.isRight());
        verify(erasureWriteService).createErasureRequest(any(), any());
    }

    @Test
    void requestErasure_setsRequestIpOnErasureRequest() {
        when(userRepository.findById(1L)).thenReturn(Uni.createFrom().item(testUser));
        when(erasureWriteService.createErasureRequest(any(), any()))
                .thenReturn(Uni.createFrom().voidItem());
        when(authInternalClient.deleteTokensByUser(anyLong(), anyString()))
                .thenReturn(Uni.createFrom().item(Response.ok().build()));

        erasureService.requestErasure(1L, "10.0.0.1").await().indefinitely();

        var captor = ArgumentCaptor.forClass(ErasureRequest.class);
        verify(erasureWriteService).createErasureRequest(any(), captor.capture());
        assertEquals("10.0.0.1", captor.getValue().requestedIp);
        assertEquals(1L, captor.getValue().userId);
    }

    // --- setLegalHold ---

    @Test
    void setLegalHold_userExists_returnsRight() {
        LocalDateTime holdUntil = LocalDateTime.now().plusDays(30);
        when(userRepository.findById(1L)).thenReturn(Uni.createFrom().item(testUser));
        when(userRepository.persist(any(User.class))).thenReturn(Uni.createFrom().item(testUser));

        var result = erasureService.setLegalHold(1L, holdUntil).await().indefinitely();

        assertTrue(result.isRight());
        assertEquals(holdUntil, testUser.legalHoldUntil);
    }

    @Test
    void setLegalHold_userNotFound_returnsLeft404() {
        when(userRepository.findById(99L)).thenReturn(Uni.createFrom().nullItem());

        var result = erasureService.setLegalHold(99L, LocalDateTime.now().plusDays(1)).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(NotFoundError.class, ((Either.Left<?, ?>) result).value());
        verify(userRepository, never()).persist(any(User.class));
    }

    // --- purgeExpiredUnactivatedUsers ---

    @Test
    void purgeExpiredUnactivatedUsers_delegatesToRepository() {
        when(userRepository.deleteExpiredUnactivated()).thenReturn(Uni.createFrom().item(3L));

        erasureService.purgeExpiredUnactivatedUsers().await().indefinitely();

        verify(userRepository).deleteExpiredUnactivated();
    }

    // --- purgeEligibleUsers ---

    @Test
    void purgeEligibleUsers_emptyList_noInteractions() {
        when(erasureRequestRepository.findEligibleForPurge())
                .thenReturn(Uni.createFrom().item(List.of()));

        erasureService.purgeEligibleUsers().await().indefinitely();

        verifyNoInteractions(authInternalClient, adoptionInternalClient, chatInternalClient, erasureWriteService);
    }

    @Test
    void purgeEligibleUsers_userWithoutHold_executesAnonymization() {
        when(erasureRequestRepository.findEligibleForPurge())
                .thenReturn(Uni.createFrom().item(List.of(testErasureRequest)));
        when(userRepository.findById(1L)).thenReturn(Uni.createFrom().item(testUser));
        when(authInternalClient.deleteTokensByUser(eq(1L), anyString()))
                .thenReturn(Uni.createFrom().item(Response.ok().build()));
        when(adoptionInternalClient.anonymizeUser(eq(1L), anyString()))
                .thenReturn(Uni.createFrom().item(Response.ok().build()));
        when(chatInternalClient.anonymizeUser(eq(1L), anyString()))
                .thenReturn(Uni.createFrom().item(Response.ok().build()));
        when(erasureWriteService.deleteUserAndMarkPurged(eq(1L), any()))
                .thenReturn(Uni.createFrom().voidItem());

        erasureService.purgeEligibleUsers().await().indefinitely();

        verify(authInternalClient).deleteTokensByUser(1L, "test-secret");
        verify(adoptionInternalClient).anonymizeUser(1L, "test-secret");
        verify(chatInternalClient).anonymizeUser(1L, "test-secret");
        verify(erasureWriteService).deleteUserAndMarkPurged(eq(1L), eq(testErasureRequest));
    }

    @Test
    void purgeEligibleUsers_userWithActiveHold_marksBlockedAndSkipsAnonymization() {
        testUser.legalHoldUntil = LocalDateTime.now().plusDays(10);
        when(erasureRequestRepository.findEligibleForPurge())
                .thenReturn(Uni.createFrom().item(List.of(testErasureRequest)));
        when(userRepository.findById(1L)).thenReturn(Uni.createFrom().item(testUser));
        when(erasureWriteService.markBlockedByHold(testErasureRequest))
                .thenReturn(Uni.createFrom().voidItem());

        erasureService.purgeEligibleUsers().await().indefinitely();

        verify(erasureWriteService).markBlockedByHold(testErasureRequest);
        verifyNoInteractions(authInternalClient, adoptionInternalClient, chatInternalClient);
        verify(erasureWriteService, never()).deleteUserAndMarkPurged(anyLong(), any());
    }

    @Test
    void purgeEligibleUsers_alreadyBlockedByHold_skipsMarkAndAnonymization() {
        testUser.legalHoldUntil = LocalDateTime.now().plusDays(10);
        testErasureRequest.blockedByHold = true;
        when(erasureRequestRepository.findEligibleForPurge())
                .thenReturn(Uni.createFrom().item(List.of(testErasureRequest)));
        when(userRepository.findById(1L)).thenReturn(Uni.createFrom().item(testUser));

        erasureService.purgeEligibleUsers().await().indefinitely();

        verify(erasureWriteService, never()).markBlockedByHold(any());
        verifyNoInteractions(authInternalClient, adoptionInternalClient, chatInternalClient);
    }
}
