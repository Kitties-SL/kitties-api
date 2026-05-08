package es.kitti.cat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record CatSummaryResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("name") String name,
        @JsonProperty("profileImageUrl") String profileImageUrl,
        @JsonProperty("age") Integer age,
        @JsonProperty("sex") String sex,
        @JsonProperty("neutered") Boolean neutered,
        @JsonProperty("status") String status,
        @JsonProperty("city") String city,
        @JsonProperty("region") String region,
        @JsonProperty("country") String country,
        @JsonProperty("createdAt") LocalDateTime createdAt
) {}
