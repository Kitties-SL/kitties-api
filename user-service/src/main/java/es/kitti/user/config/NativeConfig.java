package es.kitti.user.config;

import es.kitti.mon.error.ErrorResponse;
import es.kitti.mon.error.FieldViolation;
import es.kitti.mon.error.ValidationError;
import es.kitti.user.dto.*;
import es.kitti.user.entity.UserRole;
import es.kitti.user.entity.UserStatus;
import es.kitti.user.event.PasswordChangedEvent;
import es.kitti.user.event.UserRegisteredEvent;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection(targets = {
        ActivationRequest.class,
        ChangePasswordRequest.class,
        PasswordResetRequest.class,
        PasswordPolicyUpdateRequest.class,
        PasswordResetTokenIssueRequest.class,
        PasswordResetTokenIssueResponse.class,
        UserCreateRequest.class,
        UserDataExportResponse.class,
        UserResponse.class,
        UserUpdateRequest.class,
        PasswordChangedEvent.class,
        UserRegisteredEvent.class,
        UserRole.class,
        UserStatus.class,
        ErrorResponse.class,
        FieldViolation.class,
        ValidationError.class
})
public class NativeConfig {}
