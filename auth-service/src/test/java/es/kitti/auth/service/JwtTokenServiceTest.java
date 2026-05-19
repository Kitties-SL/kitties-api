package es.kitti.auth.service;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class JwtTokenServiceTest {

    @Inject JwtTokenService jwtTokenService;
    @Inject JWTParser jwtParser;

    @Test
    void generatePasswordResetToken_returnsParseableTokenWithExpectedClaims() throws ParseException {
        var issued = jwtTokenService.generatePasswordResetToken(42L);

        assertNotNull(issued.token());
        assertNotNull(issued.jti());
        assertTrue(issued.expiresAt().isAfter(LocalDateTime.now()));

        JsonWebToken parsed = jwtParser.parse(issued.token());
        assertEquals("42", parsed.getSubject());
        assertEquals("password-reset", parsed.getClaim("purpose"));
        assertEquals(issued.jti(), parsed.getClaim("jti"));
    }

    @Test
    void generatePasswordResetToken_eachCallProducesDifferentJti() {
        var first  = jwtTokenService.generatePasswordResetToken(1L);
        var second = jwtTokenService.generatePasswordResetToken(1L);
        assertNotEquals(first.jti(), second.jti());
        assertNotEquals(first.token(), second.token());
    }

    @Test
    void generatePasswordResetToken_expiresWithinConfiguredLifespan() {
        var issued = jwtTokenService.generatePasswordResetToken(7L);
        long diffSeconds = ChronoUnit.SECONDS.between(LocalDateTime.now(), issued.expiresAt());
        assertTrue(diffSeconds > 86000 && diffSeconds <= 86400,
                "expected ~86400s lifespan, got " + diffSeconds);
    }
}
