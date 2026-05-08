package es.kitti.adoption.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AdoptionDataExport(@JsonProperty("adoptionRequests") List<AdoptionExportEntry> adoptionRequests) {}
