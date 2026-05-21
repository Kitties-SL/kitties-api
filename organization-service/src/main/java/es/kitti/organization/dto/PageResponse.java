package es.kitti.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PageResponse<T>(
        @JsonProperty("content") List<T> content,
        @JsonProperty("page") int page,
        @JsonProperty("size") int size,
        @JsonProperty("total") long total,
        @JsonProperty("totalPages") int totalPages
) {
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long total) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResponse<>(content, page, size, total, totalPages);
    }
}
