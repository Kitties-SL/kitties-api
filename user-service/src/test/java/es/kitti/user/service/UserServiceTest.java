package es.kitti.user.service;

import es.kitti.mon.either.Either;
import es.kitti.mon.error.ConflictError;
import es.kitti.mon.error.DomainError;
import es.kitti.mon.error.NotFoundError;
import es.kitti.mon.error.UnauthorizedError;
import io.smallrye.mutiny.Uni;
import es.kitti.user.dto.UserCreateRequest;
import es.kitti.user.dto.UserResponse;
import es.kitti.user.entity.User;
import es.kitti.user.entity.UserRole;
import es.kitti.user.entity.UserStatus;
import es.kitti.user.event.UserRegisteredEvent;
import es.kitti.user.mapper.UserMapper;
import es.kitti.user.repository.UserRepository;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserMapper userMapper;
    @Mock Emitter<UserRegisteredEvent> userRegisteredEmitter;

    @InjectMocks
    UserService userService;

    private User testUser;
    private UserResponse testUserResponse;

    @BeforeEach
    void setUp() {
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
                "test@kitti.es", "password123", "Test", "User", null, null, UserRole.User);

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
                "duplicate@kitti.es", "password123", "Test", "User", null, null, UserRole.User);

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
}
