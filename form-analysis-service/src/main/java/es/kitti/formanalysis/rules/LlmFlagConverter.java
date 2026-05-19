package es.kitti.formanalysis.rules;

import jakarta.enterprise.context.ApplicationScoped;
import es.kitti.formanalysis.dto.llm.LlmTextAnalysis;
import es.kitti.formanalysis.dto.llm.StructuralAssessment;
import es.kitti.formanalysis.entity.FlagSeverity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@ApplicationScoped
public class LlmFlagConverter {

    public List<FlagResult> convert(LlmTextAnalysis analysis) {
        List<FlagResult> flags = new ArrayList<>();

        switch (Objects.toString(analysis.punishmentRisk(), "")) {
            case "HIGH" -> flags.add(new FlagResult(FlagSeverity.Warning, "LLM_PUNISHMENT_RISK",
                    "El LLM detectó indicios claros de castigo físico en el texto"));
            case "LOW"  -> flags.add(new FlagResult(FlagSeverity.Notice,  "LLM_PUNISHMENT_RISK",
                    "El LLM detectó una señal ambigua relacionada con castigo físico"));
        }

        switch (Objects.toString(analysis.abandonmentRisk(), "")) {
            case "HIGH" -> flags.add(new FlagResult(FlagSeverity.Warning, "LLM_ABANDONMENT_RISK",
                    "El LLM detectó indicios claros de abandono previo de animales"));
            case "LOW"  -> flags.add(new FlagResult(FlagSeverity.Notice,  "LLM_ABANDONMENT_RISK",
                    "El LLM detectó una señal ambigua relacionada con abandono previo"));
        }

        switch (Objects.toString(analysis.motivationQuality(), "")) {
            case "SUPERFICIAL" -> flags.add(new FlagResult(FlagSeverity.Warning, "LLM_SUPERFICIAL_MOTIVATION",
                    "El LLM evaluó la motivación para adoptar como superficial o impulsiva"));
            case "UNCLEAR"     -> flags.add(new FlagResult(FlagSeverity.Notice,  "LLM_UNCLEAR_MOTIVATION",
                    "El LLM no pudo determinar con claridad la motivación para adoptar"));
        }

        switch (Objects.toString(analysis.evasivenessLevel(), "")) {
            case "HIGH"     -> flags.add(new FlagResult(FlagSeverity.Warning, "LLM_EVASIVENESS",
                    "El LLM detectó evasión clara de preguntas concretas"));
            case "MODERATE" -> flags.add(new FlagResult(FlagSeverity.Notice,  "LLM_EVASIVENESS",
                    "El LLM detectó cierta evasión moderada en las respuestas"));
        }

        switch (Objects.toString(analysis.consistencyCheck(), "")) {
            case "INCONSISTENT" -> flags.add(new FlagResult(FlagSeverity.Warning, "LLM_INCONSISTENCY",
                    "El LLM detectó inconsistencias internas entre las respuestas del solicitante"));
            case "UNCERTAIN"    -> flags.add(new FlagResult(FlagSeverity.Notice,  "LLM_INCONSISTENCY",
                    "El LLM no pudo confirmar la consistencia entre las respuestas del solicitante"));
        }

        if (analysis.structuralAssessments() != null) {
            for (StructuralAssessment assessment : analysis.structuralAssessments()) {
                String description = assessment.reasoning() != null && !assessment.reasoning().isBlank()
                        ? assessment.reasoning()
                        : assessment.code();
                switch (Objects.toString(assessment.severity(), "")) {
                    case "HIGH" -> flags.add(new FlagResult(FlagSeverity.Warning, assessment.code(), description));
                    case "LOW"  -> flags.add(new FlagResult(FlagSeverity.Notice,  assessment.code(), description));
                }
            }
        }

        return flags;
    }
}
