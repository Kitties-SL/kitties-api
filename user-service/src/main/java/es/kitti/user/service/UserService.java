package es.kitti.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.kitti.mon.either.Either;
import es.kitti.mon.either.Unit;
import es.kitti.mon.error.*;
import es.kitti.user.client.AdoptionInternalClient;
import es.kitti.user.client.AuthInternalClient;
import es.kitti.user.client.ChatInternalClient;
import es.kitti.user.dto.*;
import es.kitti.user.entity.User;
import es.kitti.user.entity.UserRole;
import es.kitti.user.entity.UserStatus;
import es.kitti.user.event.PasswordChangedEvent;
import es.kitti.user.event.PasswordResetRequestedEvent;
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
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class UserService {

    @Inject UserRepository userRepository;
    @Inject UserMapper userMapper;
    @Inject @Channel("user-registered")         Emitter<UserRegisteredEvent>         userRegisteredEmitter;
    @Inject @Channel("password-changed")         Emitter<PasswordChangedEvent>         passwordChangedEmitter;
    @Inject @Channel("password-reset-requested") Emitter<PasswordResetRequestedEvent> passwordResetRequestedEmitter;
    @Inject ObjectMapper objectMapper;
    @Inject JWTParser jwtParser;

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
                    if (violatesStrictPolicy(user, request.newPassword()))
                        return Uni.createFrom().item(Either.<DomainError, Unit>left(new ConflictError("PASSWORD_REUSED")));
                    return applyPasswordChange(user, request.newPassword(), requestIp);
                })
                .call(either -> either.isRight()
                        ? revokeTokensBestEffort(userId)
                        : Uni.createFrom().voidItem()
                );
    }

    @WithTransaction
    public Uni<Either<DomainError, Unit>> resetPassword(String token, String newPassword) {
        ParsedResetToken parsed;
        try {
            parsed = parseResetToken(token);
        } catch (ParseException e) {
            return Uni.createFrom().item(Either.<DomainError, Unit>left(new UnauthorizedError("INVALID_RESET_TOKEN")));
        }
        if (parsed == null)
            return Uni.createFrom().item(Either.<DomainError, Unit>left(new UnauthorizedError("INVALID_RESET_TOKEN")));

        return userRepository.findById(parsed.userId())
                .onItem().transformToUni(user -> {
                    if (user == null)
                        return Uni.createFrom().item(Either.<DomainError, Unit>left(new UnauthorizedError("INVALID_RESET_TOKEN")));
                    if (user.passwordResetJti == null || !user.passwordResetJti.equals(parsed.jti()))
                        return Uni.createFrom().item(Either.<DomainError, Unit>left(new ConflictError("RESET_TOKEN_USED_OR_SUPERSEDED")));
                    if (violatesStrictPolicy(user, newPassword))
                        return Uni.createFrom().item(Either.<DomainError, Unit>left(new ConflictError("PASSWORD_REUSED")));

                    user.previousPasswordHash = user.passwordHash;
                    user.passwordHash         = BcryptUtil.bcryptHash(newPassword);
                    user.passwordResetJti     = null;

                    return userRepository.persist(user)
                            .onItem().transform(__ -> Either.<DomainError>unit());
                })
                .call(either -> either.isRight()
                        ? revokeTokensBestEffort(parsed.userId())
                        : Uni.createFrom().voidItem()
                );
    }

    @WithTransaction
    public Uni<Either<DomainError, Unit>> requestPasswordReset(String email) {
        return userRepository.findByEmail(email)
                .onItem().transformToUni(user -> {
                    if (user == null || user.status != UserStatus.Active)
                        return Uni.createFrom().item(Either.<DomainError>unit());
                    return authInternalClient.requestPasswordResetToken(
                                    new PasswordResetTokenIssueRequest(user.id), internalSecret)
                            .onItem().transformToUni(issued -> {
                                user.passwordResetJti = issued.jti();
                                return userRepository.persist(user)
                                        .onItem().invoke(saved -> passwordResetRequestedEmitter.send(
                                                new PasswordResetRequestedEvent(
                                                        saved.id, saved.email, saved.name,
                                                        issued.token(), issued.expiresAt())))
                                        .onItem().transform(__ -> Either.<DomainError>unit());
                            });
                });
    }

    @WithTransaction
    public Uni<Either<DomainError, Unit>> setPasswordPolicy(Long userId, boolean strict) {
        return userRepository.findById(userId)
                .onItem().transformToUni(user -> {
                    if (user == null)
                        return Uni.createFrom().item(Either.<DomainError, Unit>left(new NotFoundError("USER_NOT_FOUND")));
                    user.strictPasswordPolicy = strict;
                    return userRepository.persist(user)
                            .onItem().transform(__ -> Either.<DomainError>unit());
                });
    }

    private Uni<Either<DomainError, Unit>> applyPasswordChange(User user, String newPassword, String requestIp) {
        LocalDateTime now = LocalDateTime.now();
        return authInternalClient.requestPasswordResetToken(
                        new PasswordResetTokenIssueRequest(user.id), internalSecret)
                .onItem().transformToUni(issued -> {
                    user.previousPasswordHash = user.passwordHash;
                    user.passwordHash         = BcryptUtil.bcryptHash(newPassword);
                    user.passwordResetJti     = issued.jti();

                    return userRepository.persist(user)
                            .onItem().invoke(saved -> passwordChangedEmitter.send(new PasswordChangedEvent(
                                    saved.id, saved.email, saved.name, requestIp, now,
                                    issued.token(), issued.expiresAt())))
                            .onItem().transform(__ -> Either.<DomainError>unit());
                });
    }

    private boolean violatesStrictPolicy(User user, String newPassword) {
        return user.strictPasswordPolicy
                && user.previousPasswordHash != null
                && BcryptUtil.matches(newPassword, user.previousPasswordHash);
    }

    private Uni<Void> revokeTokensBestEffort(Long userId) {
        return authInternalClient.deleteTokensByUser(userId, internalSecret)
                .replaceWithVoid()
                .onFailure().recoverWithUni(e -> {
                    Log.warnf("Could not revoke tokens for user %d after password change: %s", userId, e.getMessage());
                    return Uni.createFrom().voidItem();
                });
    }

    private ParsedResetToken parseResetToken(String token) throws ParseException {
        if (token == null || token.isBlank()) return null;
        JsonWebToken jwt = jwtParser.parse(token);
        Object purpose = jwt.getClaim("purpose");
        if (!"password-reset".equals(purpose)) return null;
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) return null;
        Object jti = jwt.getClaim("jti");
        if (jti == null || jti.toString().isBlank()) return null;
        try {
            return new ParsedResetToken(Long.parseLong(sub), jti.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record ParsedResetToken(long userId, String jti) {}

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
