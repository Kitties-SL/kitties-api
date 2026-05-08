package es.kitti.cat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public record CatResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("name") String name,
        @JsonProperty("age") Integer age,
        @JsonProperty("sex") String sex,
        @JsonProperty("description") String description,
        @JsonProperty("neutered") Boolean neutered,
        @JsonProperty("status") String status,
        @JsonProperty("city") String city,
        @JsonProperty("region") String region,
        @JsonProperty("country") String country,
        @JsonProperty("latitude") Double latitude,
        @JsonProperty("longitude") Double longitude,
        @JsonProperty("organizationId") Long organizationId,
        @JsonProperty("images") List<CatImageResponse> images,
        @JsonProperty("createdAt") LocalDateTime createdAt,
        @JsonProperty("updatedAt") LocalDateTime updatedAt
) {}
