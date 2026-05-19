package es.kitti.auth.config;

import es.kitti.auth.dto.AuthRequest;
import es.kitti.auth.dto.AuthResponse;
import es.kitti.auth.dto.LogoutRequest;
import es.kitti.auth.dto.PasswordResetTokenRequest;
import es.kitti.auth.dto.PasswordResetTokenResponse;
import es.kitti.auth.dto.RefreshRequest;
import es.kitti.mon.error.ErrorResponse;
import es.kitti.mon.error.FieldViolation;
import es.kitti.mon.error.ValidationError;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection(targets = {
        AuthRequest.class,
        AuthResponse.class,
        LogoutRequest.class,
        PasswordResetTokenRequest.class,
        PasswordResetTokenResponse.class,
        RefreshRequest.class,
        ErrorResponse.class,
        FieldViolation.class,
        ValidationError.class
})
public class NativeConfig {}
