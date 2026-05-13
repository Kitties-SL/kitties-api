package es.kitti.chat.domain;

import es.kitti.mon.either.Validation;

public final class MessageContent {

    private static final int MAX_LENGTH = 4000;

    private final String value;

    private MessageContent(String value) {
        this.value = value;
    }

    public static Validation<MessageContent> of(String raw) {
        if (raw == null || raw.isBlank()) return Validation.invalidOne("content", "REQUIRED");
        if (raw.length() > MAX_LENGTH)    return Validation.invalidOne("content", "INVALID_SIZE");
        return Validation.valid(new MessageContent(raw));
    }

    public String value() { return value; }
}
