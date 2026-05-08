package es.kitti.notification.config;

import es.kitti.mon.error.ErrorResponse;
import es.kitti.mon.error.FieldViolation;
import es.kitti.mon.error.ValidationError;
import es.kitti.notification.event.AdoptionFormAnalysedEvent;
import es.kitti.notification.event.UserRegisteredEvent;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection(targets = {
        AdoptionFormAnalysedEvent.class,
        UserRegisteredEvent.class,
        ErrorResponse.class,
        FieldViolation.class,
        ValidationError.class
})
public class NativeConfig {}
