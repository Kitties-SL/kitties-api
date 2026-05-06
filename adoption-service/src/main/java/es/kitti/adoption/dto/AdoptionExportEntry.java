package es.kitti.adoption.dto;

import java.util.List;

public record AdoptionExportEntry(
        AdoptionRequestResponse request,
        AdoptionRequestFormResponse requestForm,
        AdoptionFormResponse adoptionForm,
        List<InterviewResponse> interviews,
        List<ExpenseResponse> expenses
) {}
