package es.kitti.adoption.domain;

import es.kitti.mon.error.ValidationError;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class FutureDateTimeTest {

    @Test
    void of_null_returnsRequired() {
        String expected = "REQUIRED";

        var violations = FutureDateTime.of("scheduledAt", null).match(
                ValidationError::violations,
                __ -> fail("Expected invalid"));

        assertEquals("scheduledAt", violations.getFirst().field());
        assertEquals(expected,      violations.getFirst().code());
    }

    @Test
    void of_pastDate_returnsMustBeFuture() {
        LocalDateTime input   = LocalDateTime.now().minusDays(1);
        String        expected = "MUST_BE_FUTURE";

        var violations = FutureDateTime.of("scheduledAt", input).match(
                ValidationError::violations,
                __ -> fail("Expected invalid"));

        assertEquals(expected, violations.getFirst().code());
    }

    @Test
    void of_now_returnsMustBeFuture() {
        LocalDateTime input    = LocalDateTime.now();
        String        expected = "MUST_BE_FUTURE";

        var violations = FutureDateTime.of("scheduledAt", input).match(
                ValidationError::violations,
                __ -> fail("Expected invalid"));

        assertEquals(expected, violations.getFirst().code());
    }

    @Test
    void of_futureDate_returnsValid() {
        LocalDateTime input    = LocalDateTime.now().plusDays(1);
        LocalDateTime expected = input;

        var dt = FutureDateTime.of("scheduledAt", input).match(
                err -> fail("Expected valid: " + err.violations()),
                d   -> d);

        assertEquals(expected, dt.value());
    }

    @Test
    void of_propagatesFieldName() {
        var violations = FutureDateTime.of("interviewDate", null).match(
                ValidationError::violations,
                __ -> fail("Expected invalid"));

        assertEquals("interviewDate", violations.getFirst().field());
    }
}
