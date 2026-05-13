package es.kitti.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.kitti.mon.either.Either;
import es.kitti.mon.error.ConflictError;
import es.kitti.mon.error.DomainError;
import es.kitti.mon.error.NotFoundError;
import es.kitti.mon.error.UnauthorizedError;
import es.kitti.user.client.AdoptionInternalClient;
import es.kitti.user.client.ChatInternalClient;
import es.kitti.user.dto.UserDataExportResponse;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import es.kitti.user.dto.UserCreateRequest;
import es.kitti.user.dto.UserResponse;
import es.kitti.user.dto.UserUpdateRequest;
import es.kitti.user.entity.User;
import es.kitti.user.entity.UserStatus;
import es.kitti.user.event.UserRegisteredEvent;
import es.kitti.user.mapper.UserMapper;
import es.kitti.user.repository.UserRepository;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;

@ApplicationScoped
public class UserService {

    @Inject UserRepository userRepository;
    @Inject UserMapper userMapper;
    @Inject @Channel("user-registered") Emitter<UserRegisteredEvent> userRegisteredEmitter;
    @Inject ObjectMapper objectMapper;

    @RestClient AdoptionInternalClient adoptionInternalClient;
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
                            .onItem().transform(saved -> {
                                userRegisteredEmitter.send(new UserRegisteredEvent(
                                        saved.id, saved.email, saved.name, saved.activationToken));
                                return Either.right(userMapper.toResponse(saved));
                            });
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
