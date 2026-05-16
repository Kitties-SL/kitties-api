package es.kitti.auth.service;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Set;

@ApplicationScoped
public class JwtTokenService {

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    public String generateAccessToken(long userId, String role, Long organizationId, String memberRole) {
        var builder = Jwt.issuer(issuer)
                .subject(String.valueOf(userId))
                .groups(Set.of(role))
                .expiresIn(900);
        if (organizationId != null) builder.claim("organizationId", organizationId);
        if (memberRole != null)     builder.claim("memberRole", memberRole);
        return builder.sign();
    }
}