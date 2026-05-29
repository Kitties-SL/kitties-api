package es.kitti.notification.service;

import es.kitti.mon.either.Either;
import es.kitti.mon.error.DomainError;
import es.kitti.mon.error.ForbiddenError;
import es.kitti.mon.error.NotFoundError;
import es.kitti.notification.entity.Notification;
import es.kitti.notification.entity.NotificationType;
import es.kitti.notification.repository.NotificationRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDateTime;

@ApplicationScoped
public class NotificationWriteService {

    @Inject
    NotificationRepository notificationRepository;

    @WithTransaction
    public Uni<Notification> create(Long userId, NotificationType type,
                                    String code, String title,
                                    String body, String metadata) {
        Notification n = new Notification();
        n.userId = userId;
        n.type = type;
        n.code = code;
        n.title = title;
        n.body = body;
        n.metadata = metadata;
        return notificationRepository.persist(n);
    }

    @WithTransaction
    public Uni<Either<DomainError, Notification>> markRead(Long notificationId, Long callerId) {
        return notificationRepository.findById(notificationId)
                .onItem().transform(n -> {
                    if (n == null)
                        return Either.<DomainError, Notification>left(new NotFoundError("NOTIFICATION_NOT_FOUND"));
                    if (!n.userId.equals(callerId))
                        return Either.<DomainError, Notification>left(new ForbiddenError("ACCESS_DENIED"));
                    if (!n.read) {
                        n.read = true;
                        n.readAt = LocalDateTime.now();
                    }
                    return Either.<DomainError, Notification>right(n);
                });
    }

    @WithTransaction
    public Uni<Integer> markAllRead(Long userId) {
        return notificationRepository.markAllReadByUserId(userId);
    }
}
