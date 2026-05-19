package es.kitti.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.kitti.mon.either.Either;
import es.kitti.mon.error.*;
import es.kitti.user.client.AdoptionInternalClient;
import es.kitti.user.client.AuthInternalClient;
import es.kitti.user.client.ChatInternalClient;
import es.kitti.user.dto.*;
import es.kitti.user.entity.User;
import es.kitti.user.entity.UserRole;
import es.kitti.user.entity.UserStatus;
import es.kitti.user.event.PasswordChangedEvent;
import es.kitti.user.event.UserRegisteredEvent;
import es.kitti.user.mapper.UserMapper;
import es.kitti.user.repository.UserRepository;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.reactive.messaging.Emitter;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserMapper userMapper;
    @Mock Emitter<UserRegisteredEvent> userRegisteredEmitter;
    @Mock Emitter<PasswordChangedEvent> passwordChangedEmitter;
    @Mock AdoptionInternalClient adoptionInternalClient;
    @Mock AuthInternalClient authInternalClient;
    @Mock ChatInternalClient chatInternalClient;
    @Mock ObjectMapper objectMapper;
    @Mock JWTParser jwtParser;

    @InjectMocks
    UserService userService;

    private User testUser;
    private UserResponse testUserResponse;

    @BeforeEach
    void setUp() throws Exception {
        var field = UserService.class.getDeclaredField("internalSecret");
        field.setAccessible(true);
        field.set(userService, "test-secret");

        testUser = new User();
        testUser.id = 1L;
        testUser.email = "test@kitti.es";
        testUser.name = "Test";
        testUser.surname = "User";
        testUser.status = UserStatus.Pending;
        testUser.activationToken = "valid-token-123";

        testUserResponse = new UserResponse(
                1L, "test@kitti.es", "Test", "User",
                UserStatus.Pending, UserRole.User, null,
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    // --- createUser ---

    @Test
    void createUser_success() {
        var request = new UserCreateRequest(
                "test@kitti.es", "password123", "Test", "User", null, null);

        when(userRepository.existsByEmail(request.email())).thenReturn(Uni.createFrom().item(false));
        when(userRepository.persist(any(User.class))).thenReturn(Uni.createFrom().item(testUser));
        when(userMapper.toEntity(any(), anyString())).thenReturn(testUser);
        when(userMapper.toResponse(testUser)).thenReturn(testUserResponse);

        var result = userService.createUser(request).await().indefinitely();

        assertTrue(result.isRight());
        assertEquals("test@kitti.es", result.getOrElse(null).email());
        verify(userRegisteredEmitter).send(any(UserRegisteredEvent.class));
    }

    @Test
    void createUser_duplicateEmail_returnsLeft409() {
        var request = new UserCreateRequest(
                "duplicate@kitti.es", "password123", "Test", "User", null, null);

        when(userRepository.existsByEmail(request.email())).thenReturn(Uni.createFrom().item(true));

        var result = userService.createUser(request).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(ConflictError.class, ((Either.Left<?, ?>) result).value());
        assertEquals(409, result.fold(DomainError::httpStatus, __ -> 0));
        verify(userRegisteredEmitter, never()).send(any(UserRegisteredEvent.class));
    }

    // --- findByEmail ---

    @Test
    void findByEmail_userExists_returnsRight() {
        when(userRepository.findByEmail("test@kitti.es")).thenReturn(Uni.createFrom().item(testUser));
        when(userMapper.toResponse(testUser)).thenReturn(testUserResponse);

        var result = userService.findByEmail("test@kitti.es").await().indefinitely();

        assertTrue(result.isRight());
        assertEquals("test@kitti.es", result.getOrElse(null).email());
    }

    @Test
    void findByEmail_userNotFound_returnsLeft404() {
        when(userRepository.findByEmail("nonexistent@kitti.es"))
                .thenReturn(Uni.createFrom().nullItem());

        var result = userService.findByEmail("nonexistent@kitti.es").await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(NotFoundError.class, ((Either.Left<?, ?>) result).value());
        assertEquals(404, result.fold(DomainError::httpStatus, __ -> 0));
    }

    // --- activateByToken ---

    @Test
    void activateByToken_validToken_activatesUser() {
        var activeResponse = new UserResponse(
                1L, "test@kitti.es", "Test", "User",
                UserStatus.Active, UserRole.User, null,
                LocalDateTime.now(), LocalDateTime.now());

        when(userRepository.findByActivationToken("valid-token-123"))
                .thenReturn(Uni.createFrom().item(testUser));
        when(userRepository.persist(any(User.class))).thenReturn(Uni.createFrom().item(testUser));
        when(userMapper.toResponse(testUser)).thenReturn(activeResponse);

        var result = userService.activateByToken("valid-token-123").await().indefinitely();

        assertTrue(result.isRight());
        assertEquals(UserStatus.Active, result.getOrElse(null).status());
        assertNull(testUser.activationToken);
    }

    @Test
    void activateByToken_invalidToken_returnsLeft401() {
        when(userRepository.findByActivationToken("invalid-token"))
                .thenReturn(Uni.createFrom().nullItem());

        var result = userService.activateByToken("invalid-token").await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(UnauthorizedError.class, ((Either.Left<?, ?>) result).value());
        assertEquals(401, result.fold(DomainError::httpStatus, __ -> 0));
    }

    // --- deactivateUser ---

    @Test
    void deactivateUser_userExists_setsInactiveStatus() {
        var inactiveResponse = new UserResponse(
                1L, "test@kitti.es", "Test", "User",
                UserStatus.Inactive, UserRole.User, null,
                LocalDateTime.now(), LocalDateTime.now());

        when(userRepository.findByEmail("test@kitti.es")).thenReturn(Uni.createFrom().item(testUser));
        when(userRepository.persist(any(User.class))).thenReturn(Uni.createFrom().item(testUser));
        when(userMapper.toResponse(testUser)).thenReturn(inactiveResponse);

        var result = userService.deactivateUser("test@kitti.es").await().indefinitely();

        assertTrue(result.isRight());
        assertEquals(UserStatus.Inactive, result.getOrElse(null).status());
        assertEquals(UserStatus.Inactive, testUser.status);
    }

    @Test
    void deactivateUser_userNotFound_returnsLeft404() {
        when(userRepository.findByEmail("nonexistent@kitti.es"))
                .thenReturn(Uni.createFrom().nullItem());

        var result = userService.deactivateUser("nonexistent@kitti.es").await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(404, result.fold(DomainError::httpStatus, __ -> 0));
    }

    // --- findById ---

    @Test
    void findById_userExists_returnsRight() {
        when(userRepository.findById(1L)).thenReturn(Uni.createFrom().item(testUser));
        when(userMapper.toResponse(testUser)).thenReturn(testUserResponse);

        var result = userService.findById(1L).await().indefinitely();

        assertTrue(result.isRight());
        assertEquals(1L, result.getOrElse(null).id());
    }

    @Test
    void findById_userNotFound_returnsLeft404() {
        when(userRepository.findById(99L)).thenReturn(Uni.createFrom().nullItem());

        var result = userService.findById(99L).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(NotFoundError.class, ((Either.Left<?, ?>) result).value());
        assertEquals(404, result.fold(DomainError::httpStatus, __ -> 0));
    }

    // --- activateByToken (casos adicionales) ---

    @Test
    void activateByToken_expiredToken_returnsLeft401() {
        testUser.activationTokenExpiresAt = LocalDateTime.now().minusHours(1);
        when(userRepository.findByActivationToken("expired-token"))
                .thenReturn(Uni.createFrom().item(testUser));

        var result = userService.activateByToken("expired-token").await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(UnauthorizedError.class, ((Either.Left<?, ?>) result).value());
        assertEquals(401, result.fold(DomainError::httpStatus, __ -> 0));
    }

    @Test
    void activateByToken_clearsTokenAndExpiryOnSuccess() {
        testUser.activationTokenExpiresAt = LocalDateTime.now().plusHours(1);
        when(userRepository.findByActivationToken("valid-token-123"))
                .thenReturn(Uni.createFrom().item(testUser));
        when(userRepository.persist(any(User.class))).thenReturn(Uni.createFrom().item(testUser));
        when(userMapper.toResponse(testUser)).thenReturn(testUserResponse);

        userService.activateByToken("valid-token-123").await().indefinitely();

        assertNull(testUser.activationToken);
        assertNull(testUser.activationTokenExpiresAt);
    }

    // --- activateUser (activación por email, uso admin) ---

    @Test
    void activateUser_userExists_setsActiveStatus() {
        var activeResponse = new UserResponse(
                1L, "test@kitti.es", "Test", "User",
                UserStatus.Active, UserRole.User, null,
                LocalDateTime.now(), LocalDateTime.now());

        when(userRepository.findByEmail("test@kitti.es")).thenReturn(Uni.createFrom().item(testUser));
        when(userRepository.persist(any(User.class))).thenReturn(Uni.createFrom().item(testUser));
        when(userMapper.toResponse(testUser)).thenReturn(activeResponse);

        var result = userService.activateUser("test@kitti.es").await().indefinitely();

        assertTrue(result.isRight());
        assertEquals(UserStatus.Active, result.getOrElse(null).status());
        assertEquals(UserStatus.Active, testUser.status);
    }

    @Test
    void activateUser_userNotFound_returnsLeft404() {
        when(userRepository.findByEmail("nonexistent@kitti.es"))
                .thenReturn(Uni.createFrom().nullItem());

        var result = userService.activateUser("nonexistent@kitti.es").await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(NotFoundError.class, ((Either.Left<?, ?>) result).value());
        assertEquals(404, result.fold(DomainError::httpStatus, __ -> 0));
    }

    // --- updateUser ---

    @Test
    void updateUser_userExists_returnsRight() {
        var request = new UserUpdateRequest("Nuevo", "Apellido", null);
        when(userRepository.findByEmail("test@kitti.es")).thenReturn(Uni.createFrom().item(testUser));
        when(userRepository.persist(any(User.class))).thenReturn(Uni.createFrom().item(testUser));
        when(userMapper.toResponse(testUser)).thenReturn(testUserResponse);

        var result = userService.updateUser("test@kitti.es", request).await().indefinitely();

        assertTrue(result.isRight());
        verify(userMapper).updateEntity(eq(testUser), eq(request));
    }

    @Test
    void updateUser_userNotFound_returnsLeft404() {
        var request = new UserUpdateRequest("Nuevo", "Apellido", null);
        when(userRepository.findByEmail("nonexistent@kitti.es"))
                .thenReturn(Uni.createFrom().nullItem());

        var result = userService.updateUser("nonexistent@kitti.es", request).await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(404, result.fold(DomainError::httpStatus, __ -> 0));
    }

    // --- findAllActiveUsers ---

    @Test
    void findAllActiveUsers_returnsMappedList() {
        when(userRepository.findAllActiveUsers()).thenReturn(Uni.createFrom().item(List.of(testUser)));
        when(userMapper.toResponse(testUser)).thenReturn(testUserResponse);

        var result = userService.findAllActiveUsers().await().indefinitely();

        assertEquals(1, result.size());
        assertEquals("test@kitti.es", result.get(0).email());
    }

    // --- changeRole ---

    @Test
    void changeRole_success_promotesToOrganization() {
        var orgResponse = new UserResponse(
                1L, "test@kitti.es", "Test", "User",
                UserStatus.Active, UserRole.Organization, null,
                LocalDateTime.now(), LocalDateTime.now());

        when(userRepository.findById(1L)).thenReturn(Uni.createFrom().item(testUser));
        when(userRepository.persist(any(User.class))).thenReturn(Uni.createFrom().item(testUser));
        when(userMapper.toResponse(testUser)).thenReturn(orgResponse);

        var result = userService.changeRole(1L, UserRole.Organization).await().indefinitely();

        assertTrue(result.isRight());
        assertEquals(UserRole.Organization, result.getOrElse(null).role());
        assertEquals(UserRole.Organization, testUser.role);
    }

    @Test
    void changeRole_userNotFound_returnsLeft404() {
        when(userRepository.findById(99L)).thenReturn(Uni.createFrom().nullItem());

        var result = userService.changeRole(99L, UserRole.Organization).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(NotFoundError.class, ((Either.Left<?, ?>) result).value());
        assertEquals(404, result.fold(DomainError::httpStatus, __ -> 0));
    }

    @Test
    void changeRole_toAdmin_returnsLeft403() {
        var result = userService.changeRole(1L, UserRole.Admin).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(ForbiddenError.class, ((Either.Left<?, ?>) result).value());
        assertEquals(403, result.fold(DomainError::httpStatus, __ -> 0));
        verifyNoInteractions(userRepository);
    }

    // --- exportMyData ---

    @Test
    void exportMyData_userExists_returnsCombinedExport() {
        var adoptionsJson = mock(JsonNode.class);
        var chatJson = mock(JsonNode.class);
        var exportResponse = new UserDataExportResponse(testUserResponse, adoptionsJson, chatJson);

        when(userRepository.findById(1L)).thenReturn(Uni.createFrom().item(testUser));
        when(userMapper.toResponse(testUser)).thenReturn(testUserResponse);
        when(adoptionInternalClient.exportUser(eq(1L), anyString()))
                .thenReturn(Uni.createFrom().item(adoptionsJson));
        when(chatInternalClient.exportUser(eq(1L), anyString()))
                .thenReturn(Uni.createFrom().item(chatJson));

        var result = userService.exportMyData(1L).await().indefinitely();

        assertTrue(result.isRight());
        assertEquals(testUserResponse, result.getOrElse(null).profile());
        verify(adoptionInternalClient).exportUser(1L, "test-secret");
        verify(chatInternalClient).exportUser(1L, "test-secret");
    }

    @Test
    void exportMyData_userNotFound_returnsLeft404() {
        when(userRepository.findById(99L)).thenReturn(Uni.createFrom().nullItem());

        var result = userService.exportMyData(99L).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(NotFoundError.class, ((Either.Left<?, ?>) result).value());
        verifyNoInteractions(adoptionInternalClient, chatInternalClient);
    }

    // --- changePassword ---

    @Test
    void changePassword_success_persistsNewHashRevokesTokensEmitsEventAndStoresJti() {
        String oldHash = BcryptUtil.bcryptHash("oldpass123");
        testUser.passwordHash = oldHash;
        var request = new ChangePasswordRequest("oldpass123", "newpass456");

        when(userRepository.findById(1L)).thenReturn(Uni.createFrom().item(testUser));
        when(authInternalClient.requestPasswordResetToken(any(), anyString()))
                .thenReturn(Uni.createFrom().item(new PasswordResetTokenIssueResponse(
                        "jwt.payload.sig", "jti-abc", LocalDateTime.now().plusHours(24))));
        when(userRepository.persist(any(User.class))).thenReturn(Uni.createFrom().item(testUser));
        when(authInternalClient.deleteTokensByUser(eq(1L), anyString()))
                .thenReturn(Uni.createFrom().item(Response.ok().build()));

        var result = userService.changePassword(1L, request, "9.9.9.9").await().indefinitely();

        assertTrue(result.isRight());
        assertTrue(BcryptUtil.matches("newpass456", testUser.passwordHash));
        assertEquals(oldHash, testUser.previousPasswordHash);
        assertEquals("jti-abc", testUser.passwordResetJti);
        verify(authInternalClient).deleteTokensByUser(1L, "test-secret");
        verify(passwordChangedEmitter).send(any(PasswordChangedEvent.class));
    }

    @Test
    void changePassword_wrongCurrentPassword_returnsLeft400_andDoesNotPersist() {
        testUser.passwordHash = BcryptUtil.bcryptHash("oldpass123");
        var request = new ChangePasswordRequest("wrongpass", "newpass456");

        when(userRepository.findById(1L)).thenReturn(Uni.createFrom().item(testUser));

        var result = userService.changePassword(1L, request, "9.9.9.9").await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(BadRequestError.class, ((Either.Left<?, ?>) result).value());
        assertEquals("INVALID_CURRENT_PASSWORD",
                ((BadRequestError) ((Either.Left<?, ?>) result).value()).code());
        assertEquals(400, result.fold(DomainError::httpStatus, __ -> 0));
        verify(userRepository, never()).persist(any(User.class));
        verify(authInternalClient, never()).deleteTokensByUser(anyLong(), anyString());
        verify(passwordChangedEmitter, never()).send(any(PasswordChangedEvent.class));
    }

    @Test
    void changePassword_userNotFound_returnsLeft404() {
        var request = new ChangePasswordRequest("anything", "newpass456");
        when(userRepository.findById(99L)).thenReturn(Uni.createFrom().nullItem());

        var result = userService.changePassword(99L, request, "9.9.9.9").await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(NotFoundError.class, ((Either.Left<?, ?>) result).value());
        assertEquals(404, result.fold(DomainError::httpStatus, __ -> 0));
        verify(userRepository, never()).persist(any(User.class));
        verify(authInternalClient, never()).deleteTokensByUser(anyLong(), anyString());
        verify(passwordChangedEmitter, never()).send(any(PasswordChangedEvent.class));
    }

    @Test
    void changePassword_strictPolicyReusingPrevious_returnsLeft409() {
        String oldHash      = BcryptUtil.bcryptHash("oldpass123");
        String previousHash = BcryptUtil.bcryptHash("recycled1");
        testUser.passwordHash         = oldHash;
        testUser.previousPasswordHash = previousHash;
        testUser.strictPasswordPolicy = true;
        var request = new ChangePasswordRequest("oldpass123", "recycled1");

        when(userRepository.findById(1L)).thenReturn(Uni.createFrom().item(testUser));

        var result = userService.changePassword(1L, request, "9.9.9.9").await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(ConflictError.class, ((Either.Left<?, ?>) result).value());
        assertEquals("PASSWORD_REUSED",
                ((ConflictError) ((Either.Left<?, ?>) result).value()).code());
        verify(userRepository, never()).persist(any(User.class));
        verify(authInternalClient, never()).requestPasswordResetToken(any(), anyString());
    }

    @Test
    void changePassword_authClientFails_stillReturnsRight() {
        testUser.passwordHash = BcryptUtil.bcryptHash("oldpass123");
        var request = new ChangePasswordRequest("oldpass123", "newpass456");

        when(userRepository.findById(1L)).thenReturn(Uni.createFrom().item(testUser));
        when(authInternalClient.requestPasswordResetToken(any(), anyString()))
                .thenReturn(Uni.createFrom().item(new PasswordResetTokenIssueResponse(
                        "jwt.payload.sig", "jti-abc", LocalDateTime.now().plusHours(24))));
        when(userRepository.persist(any(User.class))).thenReturn(Uni.createFrom().item(testUser));
        when(authInternalClient.deleteTokensByUser(eq(1L), anyString()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("auth down")));

        var result = userService.changePassword(1L, request, "9.9.9.9").await().indefinitely();

        assertTrue(result.isRight());
        assertTrue(BcryptUtil.matches("newpass456", testUser.passwordHash));
    }

    // --- resetPassword ---

    @Test
    void resetPassword_validToken_persistsNewHashClearsJtiAndRevokesTokens() throws Exception {
        String oldHash = BcryptUtil.bcryptHash("oldpass123");
        testUser.passwordHash     = oldHash;
        testUser.passwordResetJti = "jti-xyz";

        JsonWebToken parsed = mockJwt(1L, "jti-xyz", "password-reset");
        when(jwtParser.parse("some.jwt.token")).thenReturn(parsed);
        when(userRepository.findById(1L)).thenReturn(Uni.createFrom().item(testUser));
        when(userRepository.persist(any(User.class))).thenReturn(Uni.createFrom().item(testUser));
        when(authInternalClient.deleteTokensByUser(eq(1L), anyString()))
                .thenReturn(Uni.createFrom().item(Response.ok().build()));

        var result = userService.resetPassword("some.jwt.token", "freshpass789").await().indefinitely();

        assertTrue(result.isRight());
        assertTrue(BcryptUtil.matches("freshpass789", testUser.passwordHash));
        assertEquals(oldHash, testUser.previousPasswordHash);
        assertNull(testUser.passwordResetJti);
        verify(authInternalClient).deleteTokensByUser(1L, "test-secret");
    }

    @Test
    void resetPassword_invalidSignature_returnsLeft401() throws Exception {
        when(jwtParser.parse("bad.jwt")).thenThrow(new ParseException("invalid"));

        var result = userService.resetPassword("bad.jwt", "freshpass789").await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(UnauthorizedError.class, ((Either.Left<?, ?>) result).value());
        assertEquals("INVALID_RESET_TOKEN",
                ((UnauthorizedError) ((Either.Left<?, ?>) result).value()).code());
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    void resetPassword_wrongPurpose_returnsLeft401() throws Exception {
        JsonWebToken parsed = mockJwt(1L, "jti-xyz", "access");
        when(jwtParser.parse("wrong.purpose.jwt")).thenReturn(parsed);

        var result = userService.resetPassword("wrong.purpose.jwt", "freshpass789").await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(UnauthorizedError.class, ((Either.Left<?, ?>) result).value());
    }

    @Test
    void resetPassword_jtiMismatch_returnsLeft409() throws Exception {
        testUser.passwordHash     = BcryptUtil.bcryptHash("oldpass123");
        testUser.passwordResetJti = "jti-current";

        JsonWebToken parsed = mockJwt(1L, "jti-stale", "password-reset");
        when(jwtParser.parse("stale.jwt")).thenReturn(parsed);
        when(userRepository.findById(1L)).thenReturn(Uni.createFrom().item(testUser));

        var result = userService.resetPassword("stale.jwt", "freshpass789").await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(ConflictError.class, ((Either.Left<?, ?>) result).value());
        assertEquals("RESET_TOKEN_USED_OR_SUPERSEDED",
                ((ConflictError) ((Either.Left<?, ?>) result).value()).code());
    }

    @Test
    void resetPassword_userNotFound_returnsLeft401() throws Exception {
        JsonWebToken parsed = mockJwt(99L, "jti-xyz", "password-reset");
        when(jwtParser.parse("orphan.jwt")).thenReturn(parsed);
        when(userRepository.findById(99L)).thenReturn(Uni.createFrom().nullItem());

        var result = userService.resetPassword("orphan.jwt", "freshpass789").await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(UnauthorizedError.class, ((Either.Left<?, ?>) result).value());
    }

    @Test
    void resetPassword_strictPolicyReusingPrevious_returnsLeft409() throws Exception {
        String previousHash = BcryptUtil.bcryptHash("recycled1");
        testUser.passwordHash         = BcryptUtil.bcryptHash("currentpass");
        testUser.previousPasswordHash = previousHash;
        testUser.passwordResetJti     = "jti-xyz";
        testUser.strictPasswordPolicy = true;

        JsonWebToken parsed = mockJwt(1L, "jti-xyz", "password-reset");
        when(jwtParser.parse("ok.jwt")).thenReturn(parsed);
        when(userRepository.findById(1L)).thenReturn(Uni.createFrom().item(testUser));

        var result = userService.resetPassword("ok.jwt", "recycled1").await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(ConflictError.class, ((Either.Left<?, ?>) result).value());
        assertEquals("PASSWORD_REUSED",
                ((ConflictError) ((Either.Left<?, ?>) result).value()).code());
        verify(userRepository, never()).persist(any(User.class));
    }

    // --- setPasswordPolicy ---

    @Test
    void setPasswordPolicy_enables_persistsFlag() {
        testUser.strictPasswordPolicy = false;
        when(userRepository.findById(1L)).thenReturn(Uni.createFrom().item(testUser));
        when(userRepository.persist(any(User.class))).thenReturn(Uni.createFrom().item(testUser));

        var result = userService.setPasswordPolicy(1L, true).await().indefinitely();

        assertTrue(result.isRight());
        assertTrue(testUser.strictPasswordPolicy);
    }

    @Test
    void setPasswordPolicy_userNotFound_returnsLeft404() {
        when(userRepository.findById(99L)).thenReturn(Uni.createFrom().nullItem());

        var result = userService.setPasswordPolicy(99L, true).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(NotFoundError.class, ((Either.Left<?, ?>) result).value());
    }

    private JsonWebToken mockJwt(long sub, String jti, String purpose) {
        JsonWebToken jwt = mock(JsonWebToken.class);
        lenient().when(jwt.getSubject()).thenReturn(String.valueOf(sub));
        lenient().when(jwt.getClaim("jti")).thenReturn(jti);
        lenient().when(jwt.getClaim("purpose")).thenReturn(purpose);
        return jwt;
    }
}
