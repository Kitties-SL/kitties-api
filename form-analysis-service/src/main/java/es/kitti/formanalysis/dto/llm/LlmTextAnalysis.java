package es.kitti.formanalysis.dto.llm;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

public record LlmTextAnalysis(
        String punishmentRisk,           // NONE | LOW | HIGH
        String abandonmentRisk,          // NONE | LOW | HIGH
        String motivationQuality,        // SUPERFICIAL | GENUINE | UNCLEAR
        String evasivenessLevel,         // NONE | MODERATE | HIGH
        String consistencyCheck,         // CONSISTENT | INCONSISTENT | UNCERTAIN
        @JsonDeserialize(using = SubterfugeSignalsDeserializer.class)
        List<String> subterfugeSignals,
        @JsonDeserialize(using = StructuralAssessmentsDeserializer.class)
        List<StructuralAssessment> structuralAssessments,
        String reasoning
) {
    public static LlmTextAnalysis unavailable() {
        return new LlmTextAnalysis(
                "NONE", "NONE", "GENUINE",
                "NONE", "CONSISTENT",
                List.of(), List.of(), "LLM unavailable"
        );
    }
}
