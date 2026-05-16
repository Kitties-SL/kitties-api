package es.kitti.auth.service;

import es.kitti.mon.error.DomainError;
import es.kitti.mon.error.UnauthorizedError;
import io.smallrye.mutiny.Uni;
import es.kitti.auth.dto.AuthRequest;
import es.kitti.auth.dto.AuthResponse;
import es.kitti.auth.dto.RefreshRequest;
import es.kitti.auth.entity.RefreshToken;
import es.kitti.auth.grpc.UserServiceClient;
import es.kitti.auth.repository.RefreshTokenRepository;
import es.kitti.user.grpc.ValidateCredentialsResponse;
import es.kitti.mon.either.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserServiceClient userServiceClient;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock JwtTokenService jwtTokenService;

    @InjectMocks
    AuthService authService;

    private RefreshToken validRefreshToken;

    @BeforeEach
    void setUp() {
        validRefreshToken = new RefreshToken();
        validRefreshToken.id = 1L;
        validRefreshToken.token = UUID.randomUUID().toString();
        validRefreshToken.userId = 1L;
        validRefreshToken.email = "test@kitti.es";
        validRefreshToken.role = "User";
        validRefreshToken.expiresAt = LocalDateTime.now().plusDays(7);
        validRefreshToken.revoked = false;
    }

    // --- authenticate ---

    @Test
    void authenticate_validCredentials_returnsRight() {
        var request = new AuthRequest("test@kitti.es", "password123");

        when(userServiceClient.validateCredentials("test@kitti.es", "password123"))
                .thenReturn(Uni.createFrom().item(
                        ValidateCredentialsResponse.newBuilder()
                                .setValid(true).setUserId(1L)
                                .setEmail("test@kitti.es").setRole("User")
                                .build()));
        when(jwtTokenService.generateAccessToken(1L, "User", null, null)).thenReturn("mocked-access-token");
        when(refreshTokenRepository.persist(any(RefreshToken.class)))
                .thenReturn(Uni.createFrom().item(validRefreshToken));

        var result = authService.authenticate(request).await().indefinitely();

        assertTrue(result.isRight());
        assertEquals("mocked-access-token", result.getOrElse(null).accessToken());
        assertNotNull(result.getOrElse(null).refreshToken());
        verify(jwtTokenService).generateAccessToken(1L, "User", null, null);
    }

    @Test
    void authenticate_persistedRefreshToken_hasCorrectUserData() {
        var request = new AuthRequest("test@kitti.es", "password123");
        when(userServiceClient.validateCredentials("test@kitti.es", "password123"))
                .thenReturn(Uni.createFrom().item(
                        ValidateCredentialsResponse.newBuilder()
                                .setValid(true).setUserId(1L)
                                .setEmail("test@kitti.es").setRole("User")
                                .build()));
        when(jwtTokenService.generateAccessToken(1L, "User", null, null)).thenReturn("token");
        when(refreshTokenRepository.persist(any(RefreshToken.class)))
                .thenReturn(Uni.createFrom().item(validRefreshToken));

        authService.authenticate(request).await().indefinitely();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).persist(captor.capture());
        RefreshToken saved = captor.getValue();
        assertEquals(1L, saved.userId);
        assertEquals("test@kitti.es", saved.email);
        assertEquals("User", saved.role);
        assertFalse(saved.revoked);
    }

    @Test
    void authenticate_persistedRefreshToken_expiresInSevenDays() {
        var request = new AuthRequest("test@kitti.es", "password123");
        LocalDateTime minExpiry = LocalDateTime.now().plusDays(6);
        when(userServiceClient.validateCredentials("test@kitti.es", "password123"))
                .thenReturn(Uni.createFrom().item(
                        ValidateCredentialsResponse.newBuilder()
                                .setValid(true).setUserId(1L)
                                .setEmail("test@kitti.es").setRole("User")
                                .build()));
        when(jwtTokenService.generateAccessToken(1L, "User", null, null)).thenReturn("token");
        when(refreshTokenRepository.persist(any(RefreshToken.class)))
                .thenReturn(Uni.createFrom().item(validRefreshToken));

        authService.authenticate(request).await().indefinitely();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).persist(captor.capture());
        assertTrue(captor.getValue().expiresAt.isAfter(minExpiry));
    }

    @Test
    void authenticate_returnsExpiresIn900() {
        var request = new AuthRequest("test@kitti.es", "password123");
        when(userServiceClient.validateCredentials("test@kitti.es", "password123"))
                .thenReturn(Uni.createFrom().item(
                        ValidateCredentialsResponse.newBuilder()
                                .setValid(true).setUserId(1L)
                                .setEmail("test@kitti.es").setRole("User")
                                .build()));
        when(jwtTokenService.generateAccessToken(1L, "User", null, null)).thenReturn("token");
        when(refreshTokenRepository.persist(any(RefreshToken.class)))
                .thenReturn(Uni.createFrom().item(validRefreshToken));

        var result = authService.authenticate(request).await().indefinitely();

        assertEquals(900L, result.getOrElse(null).expiresIn());
    }

    @Test
    void authenticate_invalidCredentials_returnsLeft401() {
        var request = new AuthRequest("wrong@kitti.es", "wrongpass");

        when(userServiceClient.validateCredentials("wrong@kitti.es", "wrongpass"))
                .thenReturn(Uni.createFrom().item(
                        ValidateCredentialsResponse.newBuilder().setValid(false).build()));

        var result = authService.authenticate(request).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(UnauthorizedError.class, result.fold(e -> e, __ -> null));
        assertEquals(401, result.fold(DomainError::httpStatus, __ -> 0));
        verify(refreshTokenRepository, never()).persist(any(RefreshToken.class));
    }

    // --- refresh ---

    @Test
    void refresh_validToken_returnsRight() {
        var request = new RefreshRequest(validRefreshToken.token);

        when(refreshTokenRepository.findByToken(validRefreshToken.token))
                .thenReturn(Uni.createFrom().item(validRefreshToken));
        when(jwtTokenService.generateAccessToken(1L, "User", null, null)).thenReturn("new-access-token");
        when(refreshTokenRepository.persist(any(RefreshToken.class)))
                .thenReturn(Uni.createFrom().item(validRefreshToken));

        var result = authService.refresh(request).await().indefinitely();

        assertTrue(result.isRight());
        assertEquals("new-access-token", result.getOrElse(null).accessToken());
    }

    @Test
    void refresh_revokesOldToken_andPersistsTwice() {
        var request = new RefreshRequest(validRefreshToken.token);
        when(refreshTokenRepository.findByToken(validRefreshToken.token))
                .thenReturn(Uni.createFrom().item(validRefreshToken));
        when(jwtTokenService.generateAccessToken(1L, "User", null, null)).thenReturn("new-token");
        when(refreshTokenRepository.persist(any(RefreshToken.class)))
                .thenReturn(Uni.createFrom().item(validRefreshToken));

        authService.refresh(request).await().indefinitely();

        assertTrue(validRefreshToken.revoked);
        verify(refreshTokenRepository, times(2)).persist(any(RefreshToken.class));
    }

    @Test
    void refresh_newRefreshToken_inheritsUserDataFromOldToken() {
        var request = new RefreshRequest(validRefreshToken.token);
        when(refreshTokenRepository.findByToken(validRefreshToken.token))
                .thenReturn(Uni.createFrom().item(validRefreshToken));
        when(jwtTokenService.generateAccessToken(1L, "User", null, null)).thenReturn("new-token");
        when(refreshTokenRepository.persist(any(RefreshToken.class)))
                .thenReturn(Uni.createFrom().item(validRefreshToken));

        authService.refresh(request).await().indefinitely();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).persist(captor.capture());
        RefreshToken newToken = captor.getAllValues().get(1);
        assertEquals(1L, newToken.userId);
        assertEquals("test@kitti.es", newToken.email);
        assertEquals("User", newToken.role);
        assertFalse(newToken.revoked);
    }

    @Test
    void refresh_returnsExpiresIn900() {
        var request = new RefreshRequest(validRefreshToken.token);
        when(refreshTokenRepository.findByToken(validRefreshToken.token))
                .thenReturn(Uni.createFrom().item(validRefreshToken));
        when(jwtTokenService.generateAccessToken(1L, "User", null, null)).thenReturn("new-token");
        when(refreshTokenRepository.persist(any(RefreshToken.class)))
                .thenReturn(Uni.createFrom().item(validRefreshToken));

        var result = authService.refresh(request).await().indefinitely();

        assertEquals(900L, result.getOrElse(null).expiresIn());
    }

    @Test
    void refresh_tokenNotFound_returnsLeft401() {
        when(refreshTokenRepository.findByToken("nonexistent-token"))
                .thenReturn(Uni.createFrom().nullItem());

        var result = authService.refresh(new RefreshRequest("nonexistent-token")).await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(401, result.fold(DomainError::httpStatus, __ -> 0));
    }

    @Test
    void refresh_expiredToken_returnsLeft401() {
        validRefreshToken.expiresAt = LocalDateTime.now().minusDays(1);

        when(refreshTokenRepository.findByToken(validRefreshToken.token))
                .thenReturn(Uni.createFrom().item(validRefreshToken));

        var result = authService.refresh(new RefreshRequest(validRefreshToken.token)).await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(401, result.fold(DomainError::httpStatus, __ -> 0));
    }

    @Test
    void refresh_revokedToken_returnsLeft401() {
        validRefreshToken.revoked = true;

        when(refreshTokenRepository.findByToken(validRefreshToken.token))
                .thenReturn(Uni.createFrom().item(validRefreshToken));

        var result = authService.refresh(new RefreshRequest(validRefreshToken.token)).await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(401, result.fold(DomainError::httpStatus, __ -> 0));
    }

    // --- logout ---

    @Test
    void logout_validToken_revokesToken() {
        when(refreshTokenRepository.findByToken(validRefreshToken.token))
                .thenReturn(Uni.createFrom().item(validRefreshToken));
        when(refreshTokenRepository.persist(any(RefreshToken.class)))
                .thenReturn(Uni.createFrom().item(validRefreshToken));

        var result = authService.logout(validRefreshToken.token).await().indefinitely();

        assertTrue(result.isRight());
        assertTrue(validRefreshToken.revoked);
        verify(refreshTokenRepository).persist(validRefreshToken);
    }

    @Test
    void logout_alreadyRevokedToken_returnsRight() {
        validRefreshToken.revoked = true;
        when(refreshTokenRepository.findByToken(validRefreshToken.token))
                .thenReturn(Uni.createFrom().item(validRefreshToken));
        when(refreshTokenRepository.persist(any(RefreshToken.class)))
                .thenReturn(Uni.createFrom().item(validRefreshToken));

        var result = authService.logout(validRefreshToken.token).await().indefinitely();

        assertTrue(result.isRight());
    }

    @Test
    void logout_expiredToken_returnsRight() {
        validRefreshToken.expiresAt = LocalDateTime.now().minusDays(1);
        when(refreshTokenRepository.findByToken(validRefreshToken.token))
                .thenReturn(Uni.createFrom().item(validRefreshToken));
        when(refreshTokenRepository.persist(any(RefreshToken.class)))
                .thenReturn(Uni.createFrom().item(validRefreshToken));

        var result = authService.logout(validRefreshToken.token).await().indefinitely();

        assertTrue(result.isRight());
    }

    @Test
    void logout_tokenNotFound_returnsLeft401() {
        when(refreshTokenRepository.findByToken("nonexistent-token"))
                .thenReturn(Uni.createFrom().nullItem());

        var result = authService.logout("nonexistent-token").await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(401, result.fold(DomainError::httpStatus, __ -> 0));
    }
}
