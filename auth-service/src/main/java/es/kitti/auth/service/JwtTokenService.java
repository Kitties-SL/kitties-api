package es.kitti.auth.service;

import es.kitti.auth.dto.PasswordResetTokenResponse;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class JwtTokenService {

    public static final String PURPOSE_PASSWORD_RESET = "password-reset";

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    @ConfigProperty(name = "kitties.password-reset.lifespan-seconds", defaultValue = "86400")
    long passwordResetLifespanSeconds;

    public String generateAccessToken(long userId, String role, Long organizationId, String memberRole) {
        var builder = Jwt.issuer(issuer)
                .subject(String.valueOf(userId))
                .groups(Set.of(role))
                .expiresIn(900);
        if (organizationId != null) builder.claim("organizationId", organizationId);
        if (memberRole != null)     builder.claim("memberRole", memberRole);
        return builder.sign();
    }

    public PasswordResetTokenResponse generatePasswordResetToken(long userId) {
        String jti = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(passwordResetLifespanSeconds);
        String token = Jwt.issuer(issuer)
                .subject(String.valueOf(userId))
                .claim("purpose", PURPOSE_PASSWORD_RESET)
                .claim("jti", jti)
                .expiresAt(expiresAt)
                .sign();
        return new PasswordResetTokenResponse(
                token,
                jti,
                LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault())
        );
    }
}
