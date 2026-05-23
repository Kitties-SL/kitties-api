package es.kitti.adoption.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CatResponse(
        @JsonProperty("id")             Long id,
        @JsonProperty("name")           String name,
        @JsonProperty("age")            Integer age,
        @JsonProperty("sex")            String sex,
        @JsonProperty("description")    String description,
        @JsonProperty("neutered")       Boolean neutered,
        @JsonProperty("status")         String status,
        @JsonProperty("city")           String city,
        @JsonProperty("region")         String region,
        @JsonProperty("country")        String country,
        @JsonProperty("organizationId") Long organizationId,
        @JsonProperty("createdAt")      LocalDateTime createdAt
) {}
