package es.kitti.organization.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CountByOrgsRequest(
        @JsonProperty("orgIds") List<Long> orgIds
) {}
