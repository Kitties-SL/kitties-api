package es.kitti.mon.either;

import es.kitti.mon.error.FieldViolation;
import es.kitti.mon.error.ValidationError;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TryTest {

    @Test
    void attempt_success_returnsSuccess() {
        var result = Try.attempt(() -> 42);
        assertTrue(result.isSuccess());
        assertEquals(42, result.getOrElse(0));
    }

    @Test
    void attempt_exception_returnsFailure() {
        var result = Try.attempt(() -> { throw new RuntimeException("boom"); });
        assertTrue(result.isFailure());
        assertInstanceOf(RuntimeException.class, ((Try.Failure<?>) result).cause());
        assertEquals("boom", ((Try.Failure<?>) result).cause().getMessage());
    }

    @Test
    void isSuccess_andIsFailure_areCoherent() {
        assertFalse(Try.success("x").isFailure());
        assertFalse(Try.failure(new Exception()).isSuccess());
    }

    @Test
    void map_onSuccess_transforms() {
        var result = Try.success(3).map(n -> n * 2);
        assertEquals(6, result.getOrElse(0));
    }

    @Test
    void map_onFailure_passesThrough() {
        Throwable cause = new RuntimeException("err");
        var result = Try.<Integer>failure(cause).map(n -> n * 2);
        assertTrue(result.isFailure());
        assertSame(cause, ((Try.Failure<?>) result).cause());
    }

    @Test
    void flatMap_onSuccess_chains() {
        var result = Try.success(5).flatMap(n -> Try.success(n + 1));
        assertEquals(6, result.getOrElse(0));
    }

    @Test
    void flatMap_onFailure_passesThrough() {
        Throwable cause = new RuntimeException("err");
        var result = Try.<Integer>failure(cause).flatMap(n -> Try.success(n + 1));
        assertTrue(result.isFailure());
        assertSame(cause, ((Try.Failure<?>) result).cause());
    }

    @Test
    void getOrElse_onSuccess_returnsValue() {
        assertEquals("ok", Try.success("ok").getOrElse("fallback"));
    }

    @Test
    void getOrElse_onFailure_returnsFallback() {
        assertEquals("fallback", Try.<String>failure(new Exception()).getOrElse("fallback"));
    }

    @Test
    void fold_onSuccess_appliesSuccessFn() {
        var result = Try.success(10).fold(e -> -1, n -> n + 5);
        assertEquals(15, result);
    }

    @Test
    void fold_onFailure_appliesFailureFn() {
        var result = Try.<Integer>failure(new RuntimeException("fail")).fold(e -> -1, n -> n + 5);
        assertEquals(-1, result);
    }

    @Test
    void toEither_onSuccess_returnsRight() {
        var result = Try.success("val").toEither(e -> "mapped");
        assertTrue(result.isRight());
        assertEquals("val", result.getOrElse(null));
    }

    @Test
    void toEither_onFailure_mapsToLeft() {
        var result = Try.<String>failure(new RuntimeException("boom"))
                .toEither(e -> "ERR_" + e.getMessage());
        assertTrue(result.isLeft());
        assertEquals("ERR_boom", ((Either.Left<?, ?>) result).value());
    }

    @Test
    void toValidation_onSuccess_returnsValid() {
        var result = Try.success("val").toValidation(e -> new ValidationError(List.of()));
        assertTrue(result instanceof Validation.Valid<?>);
    }

    @Test
    void toValidation_onFailure_mapsToInvalid() {
        var result = Try.<String>failure(new RuntimeException("boom"))
                .toValidation(e -> new ValidationError(List.of(new FieldViolation("x", "REQUIRED"))));
        assertTrue(result instanceof Validation.Invalid<?>);
        assertEquals(1, ((Validation.Invalid<?>) result).error().violations().size());
    }
}
