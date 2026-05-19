package es.kitti.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.kitti.mon.either.Either;
import es.kitti.mon.either.Unit;
import es.kitti.mon.error.*;
import es.kitti.user.client.AdoptionInternalClient;
import es.kitti.user.client.AuthInternalClient;
import es.kitti.user.client.ChatInternalClient;
import es.kitti.user.dto.*;
import es.kitti.user.entity.UserRole;
import es.kitti.user.entity.UserStatus;
import es.kitti.user.event.PasswordChangedEvent;
import es.kitti.user.event.UserRegisteredEvent;
import es.kitti.user.mapper.UserMapper;
import es.kitti.user.repository.UserRepository;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class UserService {

    @Inject UserRepository userRepository;
    @Inject UserMapper userMapper;
    @Inject @Channel("user-registered") Emitter<UserRegisteredEvent> userRegisteredEmitter;
    @Inject @Channel("password-changed") Emitter<PasswordChangedEvent> passwordChangedEmitter;
    @Inject ObjectMapper objectMapper;

    @RestClient AdoptionInternalClient adoptionInternalClient;
    @RestClient AuthInternalClient authInternalClient;
    @RestClient ChatInternalClient chatInternalClient;

    @ConfigProperty(name = "kitties.internal.secret")
    String internalSecret;

    @WithSession
    public Uni<Either<DomainError, UserResponse>> findById(Long id) {
        return userRepository.findById(id)
                .onItem().transform(user ->
                        user == null
                                ? Either.left(new NotFoundError("USER_NOT_FOUND"))
                                : Either.<DomainError, UserResponse>right(userMapper.toResponse(user))
                );
    }

    @WithSession
    public Uni<Either<DomainError, UserResponse>> findByEmail(String email) {
        return userRepository.findByEmail(email)
                .onItem().transform(user ->
                        user == null
                                ? Either.left(new NotFoundError("USER_NOT_FOUND"))
                                : Either.<DomainError, UserResponse>right(userMapper.toResponse(user))
                );
    }

    public Uni<List<UserResponse>> findAllActiveUsers() {
        return userRepository.findAllActiveUsers()
                .onItem().transform(users -> users.stream().map(userMapper::toResponse).toList());
    }

    @WithTransaction
    public Uni<Either<DomainError, UserResponse>> createUser(UserCreateRequest request) {
        return userRepository.existsByEmail(request.email())
                .onItem().transformToUni(exists -> {
                    if (exists)
                        return Uni.createFrom().item(Either.left(new ConflictError("EMAIL_ALREADY_EXISTS")));
                    var hashedPassword = BcryptUtil.bcryptHash(request.password());
                    var user = userMapper.toEntity(request, hashedPassword);
                    return userRepository.persist(user)
                            .onItem().transform(saved -> {
                                userRegisteredEmitter.send(new UserRegisteredEvent(
                                        saved.id, saved.email, saved.name, saved.activationToken));
                                return Either.<DomainError, UserResponse>right(userMapper.toResponse(saved));
                            });
                });
    }

    @WithTransaction
    public Uni<Either<DomainError, UserResponse>> updateUser(String email, UserUpdateRequest request) {
        return userRepository.findByEmail(email)
                .onItem().transformToUni(user -> {
                    if (user == null)
                        return Uni.createFrom().item(Either.left(new NotFoundError("USER_NOT_FOUND")));
                    userMapper.updateEntity(user, request);
                    return userRepository.persist(user)
                            .onItem().transform(saved -> Either.right(userMapper.toResponse(saved)));
                });
    }

    @WithTransaction
    public Uni<Either<DomainError, UserResponse>> deactivateUser(String email) {
        return userRepository.findByEmail(email)
                .onItem().transformToUni(user -> {
                    if (user == null)
                        return Uni.createFrom().item(Either.left(new NotFoundError("USER_NOT_FOUND")));
                    user.status = UserStatus.Inactive;
                    return userRepository.persist(user)
                            .onItem().transform(saved -> Either.right(userMapper.toResponse(saved)));
                });
    }

    @WithTransaction
    public Uni<Either<DomainError, UserResponse>> activateUser(String email) {
        return userRepository.findByEmail(email)
                .onItem().transformToUni(user -> {
                    if (user == null)
                        return Uni.createFrom().item(Either.left(new NotFoundError("USER_NOT_FOUND")));
                    user.status = UserStatus.Active;
                    return userRepository.persist(user)
                            .onItem().transform(saved -> Either.right(userMapper.toResponse(saved)));
                });
    }

    @WithTransaction
    public Uni<Either<DomainError, UserResponse>> changeRole(Long targetUserId, UserRole role) {
        if (role == UserRole.Admin)
            return Uni.createFrom().item(Either.left(new ForbiddenError("ROLE_PROMOTION_FORBIDDEN")));
        return userRepository.findById(targetUserId)
                .onItem().transformToUni(user -> {
                    if (user == null)
                        return Uni.createFrom().item(Either.left(new NotFoundError("USER_NOT_FOUND")));
                    user.role = role;
                    return userRepository.persist(user)
                            .onItem().transform(saved -> Either.<DomainError, UserResponse>right(userMapper.toResponse(saved)));
                });
    }

    @WithTransaction
    public Uni<Either<DomainError, Unit>> changePassword(Long userId, ChangePasswordRequest request, String requestIp) {
        return userRepository.findById(userId)
                .onItem().transformToUni(user -> {
                    if (user == null)
                        return Uni.createFrom().item(Either.<DomainError, Unit>left(new NotFoundError("USER_NOT_FOUND")));
                    if (!BcryptUtil.matches(request.currentPassword(), user.passwordHash))
                        return Uni.createFrom().item(Either.<DomainError, Unit>left(new BadRequestError("INVALID_CURRENT_PASSWORD")));

                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime rollbackExpiresAt = now.plusHours(24);
                    String rollbackToken = UUID.randomUUID().toString();

                    user.previousPasswordHash      = user.passwordHash;
                    user.passwordHash              = BcryptUtil.bcryptHash(request.newPassword());
                    user.passwordRollbackToken     = rollbackToken;
                    user.passwordRollbackExpiresAt = rollbackExpiresAt;

                    return userRepository.persist(user)
                            .onItem().invoke(saved -> passwordChangedEmitter.send(new PasswordChangedEvent(
                                    saved.id, saved.email, saved.name, requestIp, now, rollbackToken, rollbackExpiresAt)))
                            .onItem().transform(__ -> Either.<DomainError>unit());
                })
                .call(either -> either.isRight()
                        ? authInternalClient.deleteTokensByUser(userId, internalSecret)
                                .replaceWithVoid()
                                .onFailure().recoverWithUni(e -> {
                                    Log.warnf("Could not revoke tokens for user %d after password change: %s", userId, e.getMessage());
                                    return Uni.createFrom().voidItem();
                                })
                        : Uni.createFrom().voidItem()
                );
    }

    @WithTransaction
    public Uni<Either<DomainError, Unit>> rollbackPassword(String token) {
        return userRepository.findByPasswordRollbackToken(token)
                .onItem().transformToUni(user -> {
                    if (user == null || user.previousPasswordHash == null)
                        return Uni.createFrom().item(Either.<DomainError, Unit>left(new NotFoundError("ROLLBACK_TOKEN_INVALID")));
                    if (user.passwordRollbackExpiresAt == null
                            || user.passwordRollbackExpiresAt.isBefore(LocalDateTime.now()))
                        return Uni.createFrom().item(Either.<DomainError, Unit>left(new ConflictError("ROLLBACK_TOKEN_EXPIRED")));

                    Long capturedUserId = user.id;
                    user.passwordHash              = user.previousPasswordHash;
                    user.previousPasswordHash      = null;
                    user.passwordRollbackToken     = null;
                    user.passwordRollbackExpiresAt = null;

                    return userRepository.persist(user)
                            .call(__ -> authInternalClient.deleteTokensByUser(capturedUserId, internalSecret)
                                    .replaceWithVoid()
                                    .onFailure().recoverWithUni(e -> {
                                        Log.warnf("Could not revoke tokens for user %d after password rollback: %s", capturedUserId, e.getMessage());
                                        return Uni.createFrom().voidItem();
                                    }))
                            .onItem().transform(__ -> Either.<DomainError>unit());
                });
    }

    @WithSession
    public Uni<Either<DomainError, UserDataExportResponse>> exportMyData(Long userId) {
        return userRepository.findById(userId)
                .onItem().transformToUni(user -> {
                    if (user == null)
                        return Uni.createFrom().item(Either.left(new NotFoundError("USER_NOT_FOUND")));
                    return Uni.combine().all().unis(
                            adoptionInternalClient.exportUser(userId, internalSecret),
                            chatInternalClient.exportUser(userId, internalSecret)
                    ).asTuple().onItem().transform(t ->
                            Either.right(new UserDataExportResponse(
                                    userMapper.toResponse(user), t.getItem1(), t.getItem2()))
                    );
                });
    }

    @WithTransaction
    public Uni<Either<DomainError, UserResponse>> activateByToken(String token) {
        return userRepository.findByActivationToken(token)
                .onItem().transformToUni(user -> {
                    if (user == null)
                        return Uni.createFrom().item(Either.left(new UnauthorizedError("INVALID_ACTIVATION_TOKEN")));
                    if (user.activationTokenExpiresAt != null
                            && user.activationTokenExpiresAt.isBefore(java.time.LocalDateTime.now()))
                        return Uni.createFrom().item(Either.left(new UnauthorizedError("INVALID_ACTIVATION_TOKEN")));
                    user.status = UserStatus.Active;
                    user.activationToken = null;
                    user.activationTokenExpiresAt = null;
                    return userRepository.persist(user)
                            .onItem().transform(saved -> Either.right(userMapper.toResponse(saved)));
                });
    }
}
