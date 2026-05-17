package es.kitti.mon.either;

import java.util.function.Function;

public sealed interface Either<L, R> permits Either.Left, Either.Right {

    record Left<L, R>(L value)  implements Either<L, R> {}
    record Right<L, R>(R value) implements Either<L, R> {}

    static <L, R> Either<L, R> left(L value)  { return new Left<>(value); }
    static <L, R> Either<L, R> right(R value) { return new Right<>(value); }
    static <L> Either<L, Unit> unit()          { return right(Unit.Instance); }

    default boolean isRight() { return this instanceof Right<L, R>; }
    default boolean isLeft()  { return this instanceof Left<L, R>; }

    default <T> Either<L, T> map(Function<? super R, ? extends T> fn) {
        return switch (this) {
            case Right<L, R> r -> Either.right(fn.apply(r.value()));
            case Left<L, R>  l -> Either.left(l.value());
        };
    }

    default <T> Either<L, T> flatMap(Function<? super R, ? extends Either<L, T>> fn) {
        return switch (this) {
            case Right<L, R> r -> fn.apply(r.value());
            case Left<L, R>  l -> Either.left(l.value());
        };
    }

    default R getOrElse(R fallback) {
        return switch (this) {
            case Right<L, R> r  -> r.value();
            case Left<L, R>  __ -> fallback;
        };
    }

    default <T> T fold(Function<? super L, ? extends T> onLeft, Function<? super R, ? extends T> onRight) {
        return switch (this) {
            case Right<L, R> r -> onRight.apply(r.value());
            case Left<L, R>  l -> onLeft.apply(l.value());
        };
    }
}
