package es.kitti.cat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrgCatCountResponse(
        @JsonProperty("orgId") Long orgId,
        @JsonProperty("count") long count
) {}
