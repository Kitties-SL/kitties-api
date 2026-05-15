package es.kitti.formanalysis.dto.llm;

import java.util.List;

public record LlmTextAnalysis(
        String punishmentRisk,           // NONE | LOW | HIGH
        String abandonmentRisk,          // NONE | LOW | HIGH
        String motivationQuality,        // SUPERFICIAL | GENUINE | UNCLEAR
        String evasivenessLevel,         // NONE | MODERATE | HIGH
        String consistencyCheck,         // CONSISTENT | INCONSISTENT | UNCERTAIN
        List<String> subterfugeSignals,  // señales concretas detectadas (puede estar vacío)
        String reasoning                 // explicación para el revisor humano
) {
    public static LlmTextAnalysis unavailable() {
        return new LlmTextAnalysis(
                "NONE", "NONE", "GENUINE",
                "NONE", "CONSISTENT",
                List.of(), "LLM unavailable"
        );
    }
}