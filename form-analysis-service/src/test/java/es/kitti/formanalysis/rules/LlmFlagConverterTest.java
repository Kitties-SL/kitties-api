package es.kitti.formanalysis.rules;

import es.kitti.formanalysis.dto.llm.LlmTextAnalysis;
import es.kitti.formanalysis.entity.FlagSeverity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LlmFlagConverterTest {

    private final LlmFlagConverter converter = new LlmFlagConverter();

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
    void unavailable_generatesNoFlags() {
        var result = converter.convert(LlmTextAnalysis.unavailable());
        assertTrue(result.isEmpty());
    }

    private LlmTextAnalysis analysis(String punishment, String abandonment,
                                      String motivation, String evasiveness, String consistency) {
        return new LlmTextAnalysis(punishment, abandonment, motivation, evasiveness, consistency,
                List.of(), "test reasoning");
    }
}
