package es.kitti.notification.mapper;

import es.kitti.notification.dto.NotificationResponse;
import es.kitti.notification.entity.Notification;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NotificationMapper {

    public NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.id,
                n.type.name(),
                n.code,
                n.title,
                n.body,
                n.metadata,
                n.read,
                n.readAt,
                n.createdAt
        );
    }
}
