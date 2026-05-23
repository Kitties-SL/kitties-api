package es.kitti.organization.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrgCatCount(
        @JsonProperty("orgId") Long orgId,
        @JsonProperty("count") long count
) {}
