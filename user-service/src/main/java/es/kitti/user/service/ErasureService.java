package es.kitti.user.service;

import es.kitti.user.client.AdoptionInternalClient;
import es.kitti.user.client.AuthInternalClient;
import es.kitti.user.client.ChatInternalClient;
import es.kitti.user.entity.ErasureRequest;
import es.kitti.user.entity.UserStatus;
import es.kitti.mon.either.Either;
import es.kitti.mon.either.Unit;
import es.kitti.mon.error.ConflictError;
import es.kitti.mon.error.DomainError;
import es.kitti.mon.error.NotFoundError;
import es.kitti.user.repository.ErasureRequestRepository;
import es.kitti.user.repository.UserRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.LocalDateTime;

@ApplicationScoped
public class ErasureService {

    @Inject
    UserRepository userRepository;

    @Inject
    ErasureRequestRepository erasureRequestRepository;

    @Inject
    ErasureWriteService erasureWriteService;

    @Inject
    @RestClient
    AuthInternalClient authInternalClient;

    @Inject
    @RestClient
    AdoptionInternalClient adoptionInternalClient;

    @Inject
    @RestClient
    ChatInternalClient chatInternalClient;

    @ConfigProperty(name = "kitties.internal.secret")
    String internalSecret;

    @WithSession
    public Uni<Either<DomainError, Unit>> requestErasure(Long userId, String requestIp) {
        return userRepository.findById(userId)
                .onItem().transformToUni(user -> {
                    if (user == null)
                        return Uni.createFrom().item(Either.<DomainError, Unit>left(new NotFoundError("USER_NOT_FOUND")));
                    if (user.legalHoldUntil != null && user.legalHoldUntil.isAfter(LocalDateTime.now()))
                        return Uni.createFrom().item(Either.<DomainError, Unit>left(new ConflictError("LEGAL_HOLD_ACTIVE")));

                    LocalDateTime now = LocalDateTime.now();
                    user.status = UserStatus.Inactive;
                    user.deletedAt = now;
                    user.scheduledPurgeAt = now.plusDays(30);

                    ErasureRequest er = new ErasureRequest();
                    er.userId = user.id;
                    er.requestedAt = now;
                    er.requestedIp = requestIp;
                    er.scheduledPurgeAt = user.scheduledPurgeAt;

                    return erasureWriteService.createErasureRequest(user, er)
                            .onItem().transform(v -> Either.<DomainError>unit());
                })
                .call(either -> either.isRight()
                        ? authInternalClient.deleteTokensByUser(userId, internalSecret)
                                .replaceWithVoid()
                                .onFailure().recoverWithUni(e -> {
                                    Log.warnf("Could not delete auth tokens for user %d (will retry at purge): %s", userId, e.getMessage());
                                    return Uni.createFrom().voidItem();
                                })
                        : Uni.createFrom().voidItem()
                );
    }

    @WithTransaction
    public Uni<Either<DomainError, Unit>> setLegalHold(Long userId, LocalDateTime holdUntil) {
        return userRepository.findById(userId)
                .onItem().transformToUni(user -> {
                    if (user == null)
                        return Uni.createFrom().item(Either.left(new NotFoundError("USER_NOT_FOUND")));
                    user.legalHoldUntil = holdUntil;
                    return userRepository.persist(user)
                            .onItem().transform(v -> Either.unit());
                });
    }

    @WithTransaction
    public Uni<Void> purgeExpiredUnactivatedUsers() {
        return userRepository.deleteExpiredUnactivated()
                .invoke(count -> Log.infof("Purged %d expired unactivated users", count))
                .replaceWithVoid();
    }

    @WithSession
    public Uni<Void> purgeEligibleUsers() {
        return erasureRequestRepository.findEligibleForPurge()
                .onItem().transformToUni(requests ->
                        Multi.createFrom().iterable(requests)
                                .onItem().transformToUniAndMerge(this::purgeUser)
                                .collect().asList()
                )
                .replaceWithVoid();
    }

    @WithSession
    private Uni<Void> purgeUser(ErasureRequest er) {
        Long userId = er.userId;
        return userRepository.findById(userId)
                .onItem().transformToUni(user -> {
                    if (user != null
                            && user.legalHoldUntil != null
                            && user.legalHoldUntil.isAfter(LocalDateTime.now())) {
                        if (!er.blockedByHold)
                            return erasureWriteService.markBlockedByHold(er);
                        return Uni.createFrom().voidItem();
                    }
                    return executeAnonymization(userId, er);
                })
                .onFailure().invoke(e -> Log.errorf("Error processing erasure for user %d: %s", userId, e.getMessage()))
                .onFailure().recoverWithUni(e -> Uni.createFrom().voidItem());
    }

    private Uni<Void> executeAnonymization(Long userId, ErasureRequest er) {
        return authInternalClient.deleteTokensByUser(userId, internalSecret).replaceWithVoid()
                .onFailure().recoverWithUni(e -> {
                    Log.warnf("Auth token deletion failed for user %d: %s", userId, e.getMessage());
                    return Uni.createFrom().voidItem();
                })
                .chain(() -> adoptionInternalClient.anonymizeUser(userId, internalSecret).replaceWithVoid())
                .chain(() -> chatInternalClient.anonymizeUser(userId, internalSecret).replaceWithVoid())
                .chain(() -> erasureWriteService.deleteUserAndMarkPurged(userId, er));
    }
}
