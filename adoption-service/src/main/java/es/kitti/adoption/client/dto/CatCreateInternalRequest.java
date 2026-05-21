package es.kitti.adoption.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CatCreateInternalRequest(
        @JsonProperty("name")           String name,
        @JsonProperty("age")            Integer age,
        @JsonProperty("sex")            String sex,
        @JsonProperty("description")    String description,
        @JsonProperty("neutered")       Boolean neutered,
        @JsonProperty("city")           String city,
        @JsonProperty("region")         String region,
        @JsonProperty("country")        String country,
        @JsonProperty("latitude")       Double latitude,
        @JsonProperty("longitude")      Double longitude,
        @JsonProperty("organizationId") Long organizationId
) {}
