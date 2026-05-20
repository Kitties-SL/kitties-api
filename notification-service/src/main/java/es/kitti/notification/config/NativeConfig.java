package es.kitti.notification.config;

import es.kitti.notification.client.IpGeolocationClient;
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
        IpGeolocationClient.GeoResponse.class
})
public class NativeConfig {}
