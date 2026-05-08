package es.kitti.adoption.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AdoptionExportEntry(
        @JsonProperty("request") AdoptionRequestResponse request,
        @JsonProperty("requestForm") AdoptionRequestFormResponse requestForm,
        @JsonProperty("adoptionForm") AdoptionFormResponse adoptionForm,
        @JsonProperty("interviews") List<InterviewResponse> interviews,
        @JsonProperty("expenses") List<ExpenseResponse> expenses
) {}
