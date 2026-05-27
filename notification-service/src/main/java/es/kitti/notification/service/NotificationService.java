package es.kitti.notification.service;

import es.kitti.mon.either.Either;
import es.kitti.mon.error.DomainError;
import es.kitti.notification.dto.NotificationResponse;
import es.kitti.notification.dto.UnreadCountResponse;
import es.kitti.notification.mapper.NotificationMapper;
import es.kitti.notification.repository.NotificationRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class NotificationService {

    @Inject
    NotificationRepository notificationRepository;

    @Inject
    NotificationWriteService writeService;

    @Inject
    NotificationMapper mapper;

    @WithSession
    public Uni<List<NotificationResponse>> findByUserId(Long userId) {
        return notificationRepository.findByUserId(userId)
                .onItem().transform(list -> list.stream().map(mapper::toResponse).toList());
    }

    @WithSession
    public Uni<UnreadCountResponse> countUnread(Long userId) {
        return notificationRepository.countUnreadByUserId(userId)
                .onItem().transform(UnreadCountResponse::new);
    }

    public Uni<Either<DomainError, NotificationResponse>> markRead(Long notificationId, Long callerId) {
        return writeService.markRead(notificationId, callerId)
                .onItem().transform(either -> either.map(mapper::toResponse));
    }

    public Uni<Integer> markAllRead(Long userId) {
        return writeService.markAllRead(userId);
    }
}
