package es.kitti.notification.config;

import es.kitti.mon.error.ErrorResponse;
import es.kitti.notification.client.IpGeolocationClient;
import es.kitti.notification.dto.NotificationResponse;
import es.kitti.notification.dto.UnreadCountResponse;
import es.kitti.notification.entity.Notification;
import es.kitti.notification.entity.NotificationType;
import es.kitti.notification.event.AdoptionFormAnalysedEvent;
import es.kitti.notification.event.PasswordChangedEvent;
import es.kitti.notification.event.PasswordResetRequestedEvent;
import es.kitti.notification.event.UserRegisteredEvent;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection(targets = {
        AdoptionFormAnalysedEvent.class,
        PasswordChangedEvent.class,
        PasswordResetRequestedEvent.class,
        UserRegisteredEvent.class,
        IpGeolocationClient.GeoResponse.class,
        Notification.class,
        NotificationType.class,
        NotificationResponse.class,
        UnreadCountResponse.class,
        ErrorResponse.class
})
public class NativeConfig {}
