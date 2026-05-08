package es.kitti.storage.config;

import es.kitti.mon.error.ErrorResponse;
import es.kitti.mon.error.FieldViolation;
import es.kitti.mon.error.ValidationError;
import es.kitti.storage.dto.UploadResponse;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection(targets = {
        UploadResponse.class,
        ErrorResponse.class,
        FieldViolation.class,
        ValidationError.class
})
public class NativeConfig {}
