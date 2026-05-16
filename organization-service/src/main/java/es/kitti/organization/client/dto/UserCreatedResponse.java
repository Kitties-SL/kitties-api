package es.kitti.organization.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserCreatedResponse(
        @JsonProperty("id")    Long id,
        @JsonProperty("email") String email
) {}
