package es.kitti.adoption.dto;

import java.util.List;

public record AdoptionDataExport(List<AdoptionExportEntry> adoptionRequests) {}
