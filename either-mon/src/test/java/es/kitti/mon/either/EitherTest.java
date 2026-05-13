package es.kitti.mon.either;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EitherTest {

    @Test
    void left_isLeft() {
        var e = Either.left("error");
        assertTrue(e.isLeft());
        assertFalse(e.isRight());
    }

    @Test
    void right_isRight() {
        var e = Either.right(42);
        assertTrue(e.isRight());
        assertFalse(e.isLeft());
    }

    @Test
    void map_onRight_transforms() {
        var result = Either.<String, Integer>right(2).map(n -> n * 3);
        assertEquals(6, result.getOrElse(0));
    }

    @Test
    void map_onLeft_passesThrough() {
        Either<String, Integer> e = Either.left("err");
        var result = e.map(n -> n * 3);
        assertTrue(result.isLeft());
        assertEquals("err", ((Either.Left<?, ?>) result).value());
    }

    @Test
    void flatMap_onRight_chains() {
        var result = Either.<String, Integer>right(5)
                .flatMap(n -> n > 3 ? Either.right("big") : Either.left("small"));
        assertEquals("big", result.getOrElse(null));
    }

    @Test
    void flatMap_onLeft_passesThrough() {
        Either<String, Integer> e = Either.left("err");
        var result = e.flatMap(n -> Either.right(n * 2));
        assertTrue(result.isLeft());
    }

    @Test
    void fold_onRight_appliesRightFn() {
        var result = Either.<String, Integer>right(10).fold(String::length, n -> n + 1);
        assertEquals(11, result);
    }

    @Test
    void fold_onLeft_appliesLeftFn() {
        var result = Either.<String, Integer>left("hello").fold(String::length, n -> n + 1);
        assertEquals(5, result);
    }

    @Test
    void getOrElse_onRight_returnsValue() {
        assertEquals(7, Either.<String, Integer>right(7).getOrElse(0));
    }

    @Test
    void getOrElse_onLeft_returnsFallback() {
        assertEquals(0, Either.<String, Integer>left("err").getOrElse(0));
    }

    @Test
    void unit_returnsRightWithUnitInstance() {
        var result = Either.<String>unit();
        assertTrue(result.isRight());
        assertSame(Unit.Instance, result.getOrElse(null));
    }
}
