package es.kitti.notification.config;

import es.kitti.notification.event.AdoptionFormAnalysedEvent;
import es.kitti.notification.event.UserRegisteredEvent;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection(targets = {
        AdoptionFormAnalysedEvent.class,
        UserRegisteredEvent.class
})
public class NativeConfig {}
