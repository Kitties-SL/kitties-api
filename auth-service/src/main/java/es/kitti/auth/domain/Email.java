package es.kitti.auth.domain;

import es.kitti.mon.either.Validation;

import java.util.regex.Pattern;

public final class Email {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");

    private final String value;

    private Email(String value) {
        this.value = value;
    }

    public static Validation<Email> of(String raw) {
        if (raw == null || raw.isBlank())
            return Validation.invalidOne("email", "REQUIRED");
        String normalized = raw.toLowerCase().trim();
        if (!EMAIL_PATTERN.matcher(normalized).matches())
            return Validation.invalidOne("email", "INVALID_EMAIL");
        return Validation.valid(new Email(normalized));
    }

    public String value() { return value; }

    @Override
    public String toString() { return value; }
}
