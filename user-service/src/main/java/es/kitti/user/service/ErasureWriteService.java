package es.kitti.user.service;

import es.kitti.user.entity.ErasureRequest;
import es.kitti.user.entity.User;
import es.kitti.user.repository.ErasureRequestRepository;
import es.kitti.user.repository.UserRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;

@ApplicationScoped
public class ErasureWriteService {

    @Inject
    UserRepository userRepository;

    @Inject
    ErasureRequestRepository erasureRequestRepository;

    @WithTransaction
    public Uni<Void> createErasureRequest(User user, ErasureRequest er) {
        return userRepository.persist(user)
                .chain(() -> erasureRequestRepository.persist(er))
                .replaceWithVoid();
    }

    @WithTransaction
    public Uni<Void> markBlockedByHold(ErasureRequest er) {
        er.blockedByHold = true;
        return erasureRequestRepository.persist(er).replaceWithVoid();
    }

    @WithTransaction
    public Uni<Void> deleteUserAndMarkPurged(Long userId, ErasureRequest er) {
        er.purgedAt = LocalDateTime.now();
        return userRepository.delete("id = ?1", userId)
                .chain(() -> erasureRequestRepository.persist(er).replaceWithVoid());
    }
}
