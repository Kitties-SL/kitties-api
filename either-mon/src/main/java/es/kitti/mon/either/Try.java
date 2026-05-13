package es.kitti.mon.either;

import es.kitti.mon.error.ValidationError;

import java.util.function.Function;

public sealed interface Try<T> permits Try.Success, Try.Failure {

    record Success<T>(T value)         implements Try<T> {}
    record Failure<T>(Throwable cause) implements Try<T> {}

    @FunctionalInterface
    interface ThrowingSupplier<T> { T get() throws Exception; }

    static <T> Try<T> attempt(ThrowingSupplier<T> supplier) {
        try   { return new Success<>(supplier.get()); }
        catch (Exception e) { return new Failure<>(e); }
    }

    static <T> Try<T> success(T value)        { return new Success<>(value); }
    static <T> Try<T> failure(Throwable cause) { return new Failure<>(cause); }

    default boolean isSuccess() { return this instanceof Success<T>; }
    default boolean isFailure() { return this instanceof Failure<T>; }

    default <U> Try<U> map(Function<T, U> fn) {
        return switch (this) {
            case Success<T> s -> Try.success(fn.apply(s.value()));
            case Failure<T> f -> Try.failure(f.cause());
        };
    }

    default <U> Try<U> flatMap(Function<T, Try<U>> fn) {
        return switch (this) {
            case Success<T> s -> fn.apply(s.value());
            case Failure<T> f -> Try.failure(f.cause());
        };
    }

    default T getOrElse(T fallback) {
        return switch (this) {
            case Success<T> s  -> s.value();
            case Failure<T> __ -> fallback;
        };
    }

    default <U> U fold(Function<Throwable, U> onFailure, Function<T, U> onSuccess) {
        return switch (this) {
            case Success<T> s -> onSuccess.apply(s.value());
            case Failure<T> f -> onFailure.apply(f.cause());
        };
    }

    default <L> Either<L, T> toEither(Function<Throwable, L> onFailure) {
        return switch (this) {
            case Success<T> s -> Either.right(s.value());
            case Failure<T> f -> Either.left(onFailure.apply(f.cause()));
        };
    }

    default Validation<T> toValidation(Function<Throwable, ValidationError> onFailure) {
        return switch (this) {
            case Success<T> s -> Validation.valid(s.value());
            case Failure<T> f -> Validation.invalid(onFailure.apply(f.cause()));
        };
    }
}
