package es.kitti.formanalysis.rules;

import es.kitti.formanalysis.dto.llm.LlmTextAnalysis;
import es.kitti.formanalysis.dto.llm.StructuralAssessment;
import es.kitti.formanalysis.entity.FlagSeverity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LlmFlagConverterTest {

    private final LlmFlagConverter converter = new LlmFlagConverter();

    // --- Campos de análisis de texto ---

    @Test
    void punishmentRisk_HIGH_generatesWarning() {
        var result = converter.convert(analysis("HIGH", "NONE", "GENUINE", "NONE", "CONSISTENT"));
        assertEquals(1, result.size());
        assertEquals(FlagSeverity.Warning, result.get(0).severity());
        assertEquals("LLM_PUNISHMENT_RISK", result.get(0).code());
    }

    @Test
    void punishmentRisk_LOW_generatesNotice() {
        var result = converter.convert(analysis("LOW", "NONE", "GENUINE", "NONE", "CONSISTENT"));
        assertEquals(1, result.size());
        assertEquals(FlagSeverity.Notice, result.get(0).severity());
        assertEquals("LLM_PUNISHMENT_RISK", result.get(0).code());
    }

    @Test
    void abandonmentRisk_HIGH_generatesWarning() {
        var result = converter.convert(analysis("NONE", "HIGH", "GENUINE", "NONE", "CONSISTENT"));
        assertEquals(1, result.size());
        assertEquals(FlagSeverity.Warning, result.get(0).severity());
        assertEquals("LLM_ABANDONMENT_RISK", result.get(0).code());
    }

    @Test
    void abandonmentRisk_LOW_generatesNotice() {
        var result = converter.convert(analysis("NONE", "LOW", "GENUINE", "NONE", "CONSISTENT"));
        assertEquals(1, result.size());
        assertEquals(FlagSeverity.Notice, result.get(0).severity());
        assertEquals("LLM_ABANDONMENT_RISK", result.get(0).code());
    }

    @Test
    void motivationQuality_SUPERFICIAL_generatesWarning() {
        var result = converter.convert(analysis("NONE", "NONE", "SUPERFICIAL", "NONE", "CONSISTENT"));
        assertEquals(1, result.size());
        assertEquals(FlagSeverity.Warning, result.get(0).severity());
        assertEquals("LLM_SUPERFICIAL_MOTIVATION", result.get(0).code());
    }

    @Test
    void motivationQuality_UNCLEAR_generatesNotice() {
        var result = converter.convert(analysis("NONE", "NONE", "UNCLEAR", "NONE", "CONSISTENT"));
        assertEquals(1, result.size());
        assertEquals(FlagSeverity.Notice, result.get(0).severity());
        assertEquals("LLM_UNCLEAR_MOTIVATION", result.get(0).code());
    }

    @Test
    void evasivenessLevel_HIGH_generatesWarning() {
        var result = converter.convert(analysis("NONE", "NONE", "GENUINE", "HIGH", "CONSISTENT"));
        assertEquals(1, result.size());
        assertEquals(FlagSeverity.Warning, result.get(0).severity());
        assertEquals("LLM_EVASIVENESS", result.get(0).code());
    }

    @Test
    void evasivenessLevel_MODERATE_generatesNotice() {
        var result = converter.convert(analysis("NONE", "NONE", "GENUINE", "MODERATE", "CONSISTENT"));
        assertEquals(1, result.size());
        assertEquals(FlagSeverity.Notice, result.get(0).severity());
        assertEquals("LLM_EVASIVENESS", result.get(0).code());
    }

    @Test
    void consistencyCheck_INCONSISTENT_generatesWarning() {
        var result = converter.convert(analysis("NONE", "NONE", "GENUINE", "NONE", "INCONSISTENT"));
        assertEquals(1, result.size());
        assertEquals(FlagSeverity.Warning, result.get(0).severity());
        assertEquals("LLM_INCONSISTENCY", result.get(0).code());
    }

    @Test
    void consistencyCheck_UNCERTAIN_generatesNotice() {
        var result = converter.convert(analysis("NONE", "NONE", "GENUINE", "NONE", "UNCERTAIN"));
        assertEquals(1, result.size());
        assertEquals(FlagSeverity.Notice, result.get(0).severity());
        assertEquals("LLM_INCONSISTENCY", result.get(0).code());
    }

    @Test
    void multipleFlagsSimultaneous_allDetected() {
        var result = converter.convert(analysis("HIGH", "HIGH", "SUPERFICIAL", "NONE", "CONSISTENT"));
        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(f -> f.code().equals("LLM_PUNISHMENT_RISK")        && f.severity() == FlagSeverity.Warning));
        assertTrue(result.stream().anyMatch(f -> f.code().equals("LLM_ABANDONMENT_RISK")       && f.severity() == FlagSeverity.Warning));
        assertTrue(result.stream().anyMatch(f -> f.code().equals("LLM_SUPERFICIAL_MOTIVATION") && f.severity() == FlagSeverity.Warning));
    }

    @Test
    void unavailable_generatesNoFlags() {
        var result = converter.convert(LlmTextAnalysis.unavailable());
        assertTrue(result.isEmpty());
    }

    // --- Structural assessments ---

    @Test
    void structuralAssessment_HIGH_generatesWarning() {
        var assessment = new StructuralAssessment("UNSTABLE_HOUSING", "HIGH", "El solicitante no explica la mudanza");
        var result = converter.convert(analysisWithAssessments(List.of(assessment)));
        assertEquals(1, result.size());
        assertEquals(FlagSeverity.Warning, result.get(0).severity());
        assertEquals("UNSTABLE_HOUSING", result.get(0).code());
        assertEquals("El solicitante no explica la mudanza", result.get(0).description());
    }

    @Test
    void structuralAssessment_LOW_generatesNotice() {
        var assessment = new StructuralAssessment("INSUFFICIENT_PLAY_TIME", "LOW", "Pocos minutos pero demuestra conocimiento");
        var result = converter.convert(analysisWithAssessments(List.of(assessment)));
        assertEquals(1, result.size());
        assertEquals(FlagSeverity.Notice, result.get(0).severity());
        assertEquals("INSUFFICIENT_PLAY_TIME", result.get(0).code());
    }

    @Test
    void structuralAssessment_NONE_generatesNoFlag() {
        var assessment = new StructuralAssessment("ALLERGY_CONFIRMED", "NONE", "El solicitante convive con gatos sin problemas");
        var result = converter.convert(analysisWithAssessments(List.of(assessment)));
        assertTrue(result.isEmpty());
    }

    @Test
    void structuralAssessments_empty_generatesNoFlags() {
        var result = converter.convert(analysisWithAssessments(List.of()));
        assertTrue(result.isEmpty());
    }

    @Test
    void structuralAssessments_null_generatesNoFlags() {
        var analysis = new LlmTextAnalysis("NONE", "NONE", "GENUINE", "NONE", "CONSISTENT",
                List.of(), null, "sin assessments");
        var result = converter.convert(analysis);
        assertTrue(result.isEmpty());
    }

    @Test
    void structuralAssessment_nullSeverity_generatesNoFlag() {
        var assessment = new StructuralAssessment("UNSTABLE_HOUSING", null, "razonamiento");
        var result = converter.convert(analysisWithAssessments(List.of(assessment)));
        assertTrue(result.isEmpty());
    }

    @Test
    void structuralAssessment_reasoningUsedAsDescription() {
        var assessment = new StructuralAssessment("RENTAL_NO_PERMISSION", "HIGH", "Sin permiso documentado");
        var result = converter.convert(analysisWithAssessments(List.of(assessment)));
        assertEquals("Sin permiso documentado", result.get(0).description());
    }

    @Test
    void structuralAssessment_nullReasoning_usesCodeAsDescription() {
        var assessment = new StructuralAssessment("RENTAL_NO_PERMISSION", "HIGH", null);
        var result = converter.convert(analysisWithAssessments(List.of(assessment)));
        assertEquals("RENTAL_NO_PERMISSION", result.get(0).description());
    }

    @Test
    void textAndStructural_bothConverted() {
        var assessment = new StructuralAssessment("UNSTABLE_HOUSING", "HIGH", "Sin explicación");
        var analysis = new LlmTextAnalysis("HIGH", "NONE", "GENUINE", "NONE", "CONSISTENT",
                List.of(), List.of(assessment), "razonamiento");
        var result = converter.convert(analysis);
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(f -> f.code().equals("LLM_PUNISHMENT_RISK")));
        assertTrue(result.stream().anyMatch(f -> f.code().equals("UNSTABLE_HOUSING")));
    }

    // --- helpers ---

    private LlmTextAnalysis analysis(String punishment, String abandonment,
                                      String motivation, String evasiveness, String consistency) {
        return new LlmTextAnalysis(punishment, abandonment, motivation, evasiveness, consistency,
                List.of(), List.of(), "test reasoning");
    }

    private LlmTextAnalysis analysisWithAssessments(List<StructuralAssessment> assessments) {
        return new LlmTextAnalysis("NONE", "NONE", "GENUINE", "NONE", "CONSISTENT",
                List.of(), assessments, "test reasoning");
    }
}
