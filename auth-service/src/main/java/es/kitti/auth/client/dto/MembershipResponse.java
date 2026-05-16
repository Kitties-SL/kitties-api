package es.kitti.auth.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MembershipResponse(
        @JsonProperty("organizationId") Long organizationId,
        @JsonProperty("memberRole")     String memberRole
) {}
