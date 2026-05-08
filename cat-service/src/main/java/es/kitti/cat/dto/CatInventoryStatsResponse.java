package es.kitti.cat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CatInventoryStatsResponse(
        @JsonProperty("available") long available,
        @JsonProperty("unavailable") long unavailable,
        @JsonProperty("deleted") long deleted,
        @JsonProperty("total") long total
) {}
