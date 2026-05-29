package es.kitti.notification.repository;

import es.kitti.notification.entity.Notification;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class NotificationRepository implements PanacheRepository<Notification> {

    public Uni<List<Notification>> findByUserId(Long userId) {
        return list("userId = ?1 order by createdAt desc", userId);
    }

    public Uni<Long> countUnreadByUserId(Long userId) {
        return count("userId = ?1 and read = false", userId);
    }

    public Uni<Integer> markAllReadByUserId(Long userId) {
        return update("read = true, readAt = ?1 where userId = ?2 and read = false",
                LocalDateTime.now(), userId);
    }
}
