package es.kitti.mon.either;

import es.kitti.mon.error.DomainError;
import es.kitti.mon.error.FieldViolation;
import es.kitti.mon.error.ValidationError;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

public sealed interface Validation<T> permits Validation.Valid, Validation.Invalid {

    record Valid<T>(T value)                 implements Validation<T> {}
    record Invalid<T>(ValidationError error) implements Validation<T> {}

    static <T> Validation<T> valid(T value) {
        return new Valid<>(value);
    }

    static <T> Validation<T> invalid(ValidationError error) {
        return new Invalid<>(error);
    }

    static <T> Validation<T> invalidOne(String field, String code) {
        return new Invalid<>(new ValidationError(List.of(new FieldViolation(field, code))));
    }

    default <U> Validation<U> map(Function<T, U> fn) {
        return switch (this) {
            case Valid<T>(T t)               -> Validation.valid(fn.apply(t));
            case Invalid<T>(ValidationError e) -> Validation.invalid(e);
        };
    }

    default <U, W> Validation<W> zip(Validation<U> other, BiFunction<T, U, W> combiner) {
        return switch (this) {
            case Valid<T>(T t) -> switch (other) {
                case Valid<U>(U u)                 -> Validation.valid(combiner.apply(t, u));
                case Invalid<U>(ValidationError e)  -> Validation.invalid(e);
            };
            case Invalid<T>(ValidationError mine) -> switch (other) {
                case Valid<U>(U ignored)            -> Validation.invalid(mine);
                case Invalid<U>(ValidationError theirs) -> Validation.invalid(new ValidationError(
                        Stream.concat(mine.violations().stream(), theirs.violations().stream()).toList()
                ));
            };
        };
    }

    default <R> R match(Function<ValidationError, R> onFailure, Function<T, R> onSuccess) {
        return switch (this) {
            case Valid<T>(T t)                 -> onSuccess.apply(t);
            case Invalid<T>(ValidationError e) -> onFailure.apply(e);
        };
    }

    default Either<DomainError, T> toEither() {
        return match(Either::left, Either::right);
    }
}