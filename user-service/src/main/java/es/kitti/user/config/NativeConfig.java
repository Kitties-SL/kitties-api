package es.kitti.user.config;

import es.kitti.mon.error.ErrorResponse;
import es.kitti.mon.error.FieldViolation;
import es.kitti.mon.error.ValidationError;
import es.kitti.user.dto.ActivationRequest;
import es.kitti.user.dto.UserCreateRequest;
import es.kitti.user.dto.UserDataExportResponse;
import es.kitti.user.dto.UserResponse;
import es.kitti.user.dto.UserUpdateRequest;
import es.kitti.user.entity.UserRole;
import es.kitti.user.entity.UserStatus;
import es.kitti.user.event.UserRegisteredEvent;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection(targets = {
        ActivationRequest.class,
        UserCreateRequest.class,
        UserDataExportResponse.class,
        UserResponse.class,
        UserUpdateRequest.class,
        UserRegisteredEvent.class,
        UserRole.class,
        UserStatus.class,
        ErrorResponse.class,
        FieldViolation.class,
        ValidationError.class
})
public class NativeConfig {}
