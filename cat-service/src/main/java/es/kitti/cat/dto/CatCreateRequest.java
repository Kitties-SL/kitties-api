package es.kitti.cat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CatCreateRequest(
        @JsonProperty("name") @NotBlank String name,
        @JsonProperty("age") @NotNull @Min(0) @Max(30) Integer age,
        @JsonProperty("sex") @NotBlank String sex,
        @JsonProperty("description") String description,
        @JsonProperty("neutered") Boolean neutered,
        @JsonProperty("city") @NotBlank String city,
        @JsonProperty("region") String region,
        @JsonProperty("country") @NotBlank String country,
        @JsonProperty("latitude") Double latitude,
        @JsonProperty("longitude") Double longitude
) {}
