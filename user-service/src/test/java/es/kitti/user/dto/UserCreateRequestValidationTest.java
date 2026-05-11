package es.kitti.user.dto;

import es.kitti.mon.either.Validation;
import es.kitti.user.entity.UserRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserCreateRequestValidationTest {

    private UserCreateRequest validRequest() {
        return new UserCreateRequest(
                "user@kitti.es", "password123", "Francisco", "Domínguez",
                null, null, UserRole.User);
    }

    @Test
    void validate_allValid_returnsValid() {
        var result = validRequest().validate();
        assertTrue(result instanceof Validation.Valid<?>);
    }

    @Test
    void validate_emailNormalized_inResult() {
        var request = new UserCreateRequest(
                "USER@KITTI.ES", "password123", "Francisco", "Domínguez",
                null, null, UserRole.User);
        var result = (Validation.Valid<UserCreateRequest>) request.validate();
        assertEquals("user@kitti.es", result.value().email());
    }

    @Test
    void validate_invalidEmail_returnsInvalid() {
        var request = new UserCreateRequest(
                "notanemail", "password123", "Francisco", "Domínguez",
                null, null, UserRole.User);
        var result = request.validate();
        assertTrue(result instanceof Validation.Invalid<?>);
        var violations = ((Validation.Invalid<UserCreateRequest>) result).error().violations();
        assertEquals(1, violations.size());
        assertEquals("email", violations.getFirst().field());
    }

    @Test
    void validate_invalidPassword_returnsInvalid() {
        var request = new UserCreateRequest(
                "user@kitti.es", "short", "Francisco", "Domínguez",
                null, null, UserRole.User);
        var result = request.validate();
        assertTrue(result instanceof Validation.Invalid<?>);
        var violations = ((Validation.Invalid<UserCreateRequest>) result).error().violations();
        assertEquals(1, violations.size());
        assertEquals("password", violations.getFirst().field());
    }

    @Test
    void validate_invalidEmailAndPassword_accumulatesBothErrors() {
        var request = new UserCreateRequest(
                "notanemail", "short", "Francisco", "Domínguez",
                null, null, UserRole.User);
        var result = request.validate();
        assertTrue(result instanceof Validation.Invalid<?>);
        var violations = ((Validation.Invalid<UserCreateRequest>) result).error().violations();
        assertEquals(2, violations.size());
    }

    @Test
    void validate_blankName_returnsInvalid() {
        var request = new UserCreateRequest(
                "user@kitti.es", "password123", "", "Domínguez",
                null, null, UserRole.User);
        var result = request.validate();
        assertTrue(result instanceof Validation.Invalid<?>);
        var violations = ((Validation.Invalid<UserCreateRequest>) result).error().violations();
        assertEquals(1, violations.size());
        assertEquals("name", violations.getFirst().field());
    }

    @Test
    void validate_multipleErrors_allAccumulated() {
        var request = new UserCreateRequest(
                "bad", "x", "", "",
                null, null, UserRole.User);
        var result = request.validate();
        assertTrue(result instanceof Validation.Invalid<?>);
        var violations = ((Validation.Invalid<UserCreateRequest>) result).error().violations();
        assertEquals(4, violations.size());
    }
}
