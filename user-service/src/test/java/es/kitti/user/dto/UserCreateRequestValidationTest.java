package es.kitti.user.dto;

import es.kitti.mon.error.ValidationError;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserCreateRequestValidationTest {

    private static final String VALID_EMAIL    = "user@kitti.es";
    private static final String VALID_PASSWORD = "password123";
    private static final String VALID_NAME     = "Francisco";
    private static final String VALID_SURNAME  = "Domínguez";

    @Test
    void validate_allValid_emailNormalized() {
        String input    = "USER@KITTI.ES";
        String expected = "user@kitti.es";

        var result = new UserCreateRequest(input, VALID_PASSWORD, VALID_NAME, VALID_SURNAME,
                null, null).validate().match(
                        err -> fail("Expected valid: " + err.violations()),
                        r   -> r);

        assertEquals(expected, result.email());
    }

    @Test
    void validate_invalidEmail_singleViolation() {
        String input    = "notanemail";
        String expected = "email";

        var violations = new UserCreateRequest(input, VALID_PASSWORD, VALID_NAME, VALID_SURNAME,
                null, null).validate().match(
                        ValidationError::violations,
                        __ -> fail("Expected invalid"));

        assertEquals(1,        violations.size());
        assertEquals(expected, violations.getFirst().field());
    }

    @Test
    void validate_shortPassword_singleViolation() {
        String input    = "short";
        String expected = "password";

        var violations = new UserCreateRequest(VALID_EMAIL, input, VALID_NAME, VALID_SURNAME,
                null, null).validate().match(
                        ValidationError::violations,
                        __ -> fail("Expected invalid"));

        assertEquals(1,        violations.size());
        assertEquals(expected, violations.getFirst().field());
    }

    @Test
    void validate_invalidEmailAndPassword_accumulatesBothErrors() {
        int expected = 2;

        var violations = new UserCreateRequest("bad", "x", VALID_NAME, VALID_SURNAME,
                null, null).validate().match(
                        ValidationError::violations,
                        __ -> fail("Expected invalid"));

        assertEquals(expected, violations.size());
    }

    @Test
    void validate_allFieldsInvalid_accumulatesAllErrors() {
        int expected = 4;

        var violations = new UserCreateRequest("bad", "x", "", "",
                null, null).validate().match(
                        ValidationError::violations,
                        __ -> fail("Expected invalid"));

        assertEquals(expected, violations.size());
    }
}
