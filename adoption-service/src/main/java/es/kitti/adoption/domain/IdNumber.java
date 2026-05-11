package es.kitti.adoption.domain;

import es.kitti.mon.either.Validation;

import java.util.regex.Pattern;

public final class IdNumber {

    private static final String CONTROL_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE";
    private static final Pattern DNI_PATTERN = Pattern.compile("^\\d{8}[A-Z]$");
    private static final Pattern NIE_PATTERN = Pattern.compile("^[XYZ]\\d{7}[A-Z]$");

    private final String value;

    private IdNumber(String value) {
        this.value = value;
    }

    public static Validation<IdNumber> of(String raw) {
        if (raw == null || raw.isBlank())
            return Validation.invalidOne("idNumber", "REQUIRED");

        String normalized = raw.toUpperCase().trim();

        if (DNI_PATTERN.matcher(normalized).matches())
            return validateDni(normalized);
        if (NIE_PATTERN.matcher(normalized).matches())
            return validateNie(normalized);

        return Validation.invalidOne("idNumber", "INVALID_FORMAT");
    }

    private static Validation<IdNumber> validateDni(String dni) {
        int number = Integer.parseInt(dni.substring(0, 8));
        return validateControlLetter(dni, number, 8);
    }

    private static Validation<IdNumber> validateNie(String nie) {
        char prefix = nie.charAt(0);
        int prefixValue = prefix == 'X' ? 0 : prefix == 'Y' ? 1 : 2;
        int number = Integer.parseInt(prefixValue + nie.substring(1, 8));
        return validateControlLetter(nie, number, 8);
    }

    private static Validation<IdNumber> validateControlLetter(String value, int number, int letterIndex) {
        char expected = CONTROL_LETTERS.charAt(number % 23);
        char actual   = value.charAt(letterIndex);
        if (actual != expected)
            return Validation.invalidOne("idNumber", "INVALID_CONTROL_DIGIT");
        return Validation.valid(new IdNumber(value));
    }

    public String value() { return value; }

    @Override
    public String toString() { return value; }
}
