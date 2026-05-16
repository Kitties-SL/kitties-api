package es.kitti.formanalysis.service;

import es.kitti.formanalysis.event.AdoptionFormSubmittedEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LlmPromptBuilderTest {

    private final LlmPromptBuilder builder = new LlmPromptBuilder();

    private AdoptionFormSubmittedEvent event(
            String previousPetsHistory,
            String reactionToUnwantedBehavior,
            String motivationToAdopt,
            String whyCatsNeedToPlay,
            String plannedEnrichment) {
        return new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, previousPetsHistory, 2, false, null,
                false, null, 30, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                whyCatsNeedToPlay, 30, plannedEnrichment,
                reactionToUnwantedBehavior,
                true, true, motivationToAdopt,
                true, true, true, false, null
        );
    }

    @Test
    void build_allFields_includesAllLabels() {
        var e = event("Tuve un gato 5 años", "Lo ignoro", "Quiero darle un hogar",
                "Necesitan estimulación", "Juguetes y rascadores");

        String prompt = builder.build(e);

        assertTrue(prompt.contains("Historial de mascotas anteriores"));
        assertTrue(prompt.contains("Reacción ante comportamientos no deseados"));
        assertTrue(prompt.contains("Motivación para adoptar"));
        assertTrue(prompt.contains("Por qué los gatos necesitan jugar"));
        assertTrue(prompt.contains("Enriquecimiento planificado"));
        assertTrue(prompt.contains("Tuve un gato 5 años"));
        assertTrue(prompt.contains("Quiero darle un hogar"));
    }

    @Test
    void build_nullFields_skipsNullFields() {
        var e = event(null, "Lo ignoro", null, null, "Juguetes");

        String prompt = builder.build(e);

        assertFalse(prompt.contains("Historial de mascotas anteriores"));
        assertFalse(prompt.contains("Motivación para adoptar"));
        assertFalse(prompt.contains("Por qué los gatos necesitan jugar"));
        assertTrue(prompt.contains("Reacción ante comportamientos no deseados"));
        assertTrue(prompt.contains("Enriquecimiento planificado"));
    }

    @Test
    void build_blankFields_skipsBlankFields() {
        var e = event("  ", "Lo ignoro", "", null, "Juguetes");

        String prompt = builder.build(e);

        assertFalse(prompt.contains("Historial de mascotas anteriores"));
        assertFalse(prompt.contains("Motivación para adoptar"));
        assertTrue(prompt.contains("Reacción ante comportamientos no deseados"));
    }

    @Test
    void build_allNullFields_returnsOnlyHeader() {
        var e = event(null, null, null, null, null);

        String prompt = builder.build(e);

        assertTrue(prompt.contains("Evalúa las siguientes respuestas"));
        assertFalse(prompt.contains("\""));
    }

    @Test
    void build_fieldValuesWrappedInQuotes() {
        var e = event(null, "Le daría un cachete", null, null, null);

        String prompt = builder.build(e);

        assertTrue(prompt.contains("\"Le daría un cachete\""));
    }
}