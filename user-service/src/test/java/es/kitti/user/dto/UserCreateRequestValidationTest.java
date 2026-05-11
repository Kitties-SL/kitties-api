package es.kitti.user.dto;

import es.kitti.user.entity.UserRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserCreateRequestValidationTest {

    private static final String VALID_EMAIL    = "user@kitti.es";
    private static final String VALID_PASSWORD = "password123";
    private static final String VALID_NAME     = "Francisco";
    private static final String VALID_SURNAME  = "Domínguez";

    @Test
    void validate_allValid_emailNormalized() {
        var req = new UserCreateRequest("USER@KITTI.ES", VALID_PASSWORD, VALID_NAME, VALID_SURNAME,
                null, null, UserRole.User);
        var valid = req.validate().match(
                err -> fail("Expected valid: " + err.violations()),
                r   -> r);
        assertEquals("user@kitti.es", valid.email());
    }

    @Test
    void validate_invalidEmail_singleViolation() {
        var req = new UserCreateRequest("notanemail", VALID_PASSWORD, VALID_NAME, VALID_SURNAME,
                null, null, UserRole.User);
        var violations = req.validate().match(
                err -> err.violations(),
                __  -> fail("Expected invalid"));
        assertEquals(1, violations.size());
        assertEquals("email", violations.getFirst().field());
    }

    @Test
    void validate_shortPassword_singleViolation() {
        var req = new UserCreateRequest(VALID_EMAIL, "short", VALID_NAME, VALID_SURNAME,
                null, null, UserRole.User);
        var violations = req.validate().match(
                err -> err.violations(),
                __  -> fail("Expected invalid"));
        assertEquals(1, violations.size());
        assertEquals("password", violations.getFirst().field());
    }

    @Test
    void validate_invalidEmailAndPassword_accumulatesBothErrors() {
        var req = new UserCreateRequest("bad", "x", VALID_NAME, VALID_SURNAME,
                null, null, UserRole.User);
        var violations = req.validate().match(
                err -> err.violations(),
                __  -> fail("Expected invalid"));
        assertEquals(2, violations.size());
    }

    @Test
    void validate_allFieldsInvalid_accumulatesAllErrors() {
        var req = new UserCreateRequest("bad", "x", "", "",
                null, null, UserRole.User);
        var violations = req.validate().match(
                err -> err.violations(),
                __  -> fail("Expected invalid"));
        assertEquals(4, violations.size());
    }
}
