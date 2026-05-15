package es.kitti.formanalysis.rules;

import es.kitti.formanalysis.entity.FlagSeverity;
import es.kitti.formanalysis.event.AdoptionFormSubmittedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class FormAnalysisRulesTest {

    @InjectMocks
    FormAnalysisRules formAnalysisRules;

    private AdoptionFormSubmittedEvent buildCleanEvent() {
        return new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "Los gatos necesitan cazar por instinto aunque tengan comida",
                30, "Caña, ratones, túneles",
                "Ignorar y redirigir con juguetes",
                true, true,
                "Quiero dar un hogar a un gato que lo necesita",
                true, true, true, false, null
        );
    }

    @Test
    void cleanForm_noFlags() {
        var flags = formAnalysisRules.evaluate(buildCleanEvent());
        assertTrue(flags.isEmpty());
    }

    @Test
    void physicalPunishment_triggersCriticalFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, null, 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes",
                "le pegaría para que aprenda",
                true, true, "amor", true, true, true, false, null
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream()
                .anyMatch(f -> f.severity() == FlagSeverity.Critical
                        && f.code().equals("PHYSICAL_PUNISHMENT")));
    }

    @Test
    void rentalWithoutPermission_triggersCriticalFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, null, 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, true, false,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream()
                .anyMatch(f -> f.severity() == FlagSeverity.Critical
                        && f.code().equals("RENTAL_NO_PERMISSION")));
    }

    @Test
    void allergyConfirmed_triggersCriticalFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, null, 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, true, "alergia leve"
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream()
                .anyMatch(f -> f.severity() == FlagSeverity.Critical
                        && f.code().equals("ALLERGY_CONFIRMED")));
    }

    @Test
    void insufficientPlayTime_triggersWarningFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, null, 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 10, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream()
                .anyMatch(f -> f.severity() == FlagSeverity.Warning
                        && f.code().equals("INSUFFICIENT_PLAY_TIME")));
    }

    @Test
    void noEnrichmentSpace_triggersWarningFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, null, 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, false, false, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream()
                .anyMatch(f -> f.severity() == FlagSeverity.Warning
                        && f.code().equals("NO_ENRICHMENT_SPACE")));
    }

    @Test
    void smallHousing_triggersNoticeFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, null, 2, false, null,
                false, null, 8, true, null,
                "Apartment", 30, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream()
                .anyMatch(f -> f.severity() == FlagSeverity.Notice
                        && f.code().equals("SMALL_HOUSING")));
    }

    @Test
    void multipleCriticalFlags_allDetected() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "abandoné a mi perro", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, true, false,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes",
                "le pegaría",
                true, true, "amor", true, true, true, true, "alergia"
        );

        var flags = formAnalysisRules.evaluate(event);

        long criticalCount = flags.stream()
                .filter(f -> f.severity() == FlagSeverity.Critical)
                .count();

        assertTrue(criticalCount >= 3);
    }

    // --- ABANDONMENT_HISTORY ---

    @Test
    void abandonmentHistory_triggersCriticalFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "abandoné a mi gato anterior", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream().anyMatch(f ->
                f.severity() == FlagSeverity.Critical && f.code().equals("ABANDONMENT_HISTORY")));
    }

    @Test
    void abandonmentHistory_soltePhrasing_triggersCriticalFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "solté al perro porque me mudé", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream().anyMatch(f ->
                f.severity() == FlagSeverity.Critical && f.code().equals("ABANDONMENT_HISTORY")));
    }

    // --- TOO_MANY_HOURS_ALONE ---

    @Test
    void tooManyHoursAlone_noOtherPets_triggersWarningFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 11, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream().anyMatch(f ->
                f.severity() == FlagSeverity.Warning && f.code().equals("TOO_MANY_HOURS_ALONE")));
    }

    @Test
    void tooManyHoursAlone_withOtherPets_noFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                true, "Un perro", 11, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream().noneMatch(f -> f.code().equals("TOO_MANY_HOURS_ALONE")));
    }

    @Test
    void tooManyHoursAlone_exactlyTen_noFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 10, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream().noneMatch(f -> f.code().equals("TOO_MANY_HOURS_ALONE")));
    }

    // --- YOUNG_CHILDREN_NO_EXPERIENCE ---

    @Test
    void youngChildren_noExperience_ageThree_triggersWarningFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                false, "Murió de vejez", 2, true, "3",
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream().anyMatch(f ->
                f.severity() == FlagSeverity.Warning && f.code().equals("YOUNG_CHILDREN_NO_EXPERIENCE")));
    }

    @Test
    void youngChildren_noExperience_ageFour_noFlag() {
        // La regla usa regex \b[0-3]\b: edad 4 no dispara
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                false, "Murió de vejez", 2, true, "4",
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream().noneMatch(f -> f.code().equals("YOUNG_CHILDREN_NO_EXPERIENCE")));
    }

    @Test
    void youngChildren_withExperience_noFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, true, "2",
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream().noneMatch(f -> f.code().equals("YOUNG_CHILDREN_NO_EXPERIENCE")));
    }

    // --- UNSTABLE_HOUSING ---

    @Test
    void unstableHousing_triggersWarningFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, false, "Mudanza prevista en 6 meses",
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream().anyMatch(f ->
                f.severity() == FlagSeverity.Warning && f.code().equals("UNSTABLE_HOUSING")));
    }

    // --- SUPERFICIAL_MOTIVATION ---

    @Test
    void superficialMotivation_deRegalo_triggersWarningFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "Lo quiero de regalo para mi pareja", true, true, true, false, null
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream().anyMatch(f ->
                f.severity() == FlagSeverity.Warning && f.code().equals("SUPERFICIAL_MOTIVATION")));
    }

    @Test
    void superficialMotivation_paraLosNinos_triggersWarningFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "Lo quiero para los niños", true, true, true, false, null
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream().anyMatch(f ->
                f.severity() == FlagSeverity.Warning && f.code().equals("SUPERFICIAL_MOTIVATION")));
    }

    // --- Notice flags ---

    @Test
    void noWindowView_triggersNoticeFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                false, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream().anyMatch(f ->
                f.severity() == FlagSeverity.Notice && f.code().equals("NO_WINDOW_VIEW")));
    }

    @Test
    void noPreviousExperience_triggersNoticeFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                false, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream().anyMatch(f ->
                f.severity() == FlagSeverity.Notice && f.code().equals("NO_PREVIOUS_EXPERIENCE")));
    }

    @Test
    void noScratchingPost_triggersNoticeFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                false, true, "amor", true, true, true, false, null
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream().anyMatch(f ->
                f.severity() == FlagSeverity.Notice && f.code().equals("NO_SCRATCHING_POST")));
    }

    // --- Valores límite ---

    @Test
    void playTime_exactlyFifteenMinutes_noFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 15, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream().noneMatch(f -> f.code().equals("INSUFFICIENT_PLAY_TIME")));
    }

    @Test
    void playTime_fourteenMinutes_triggersFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 14, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream().anyMatch(f -> f.code().equals("INSUFFICIENT_PLAY_TIME")));
    }

    @Test
    void housingSize_exactlyFortySquareMeters_noFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 40, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream().noneMatch(f -> f.code().equals("SMALL_HOUSING")));
    }

    @Test
    void rentalWithPermission_noFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, true, true,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream().noneMatch(f -> f.code().equals("RENTAL_NO_PERMISSION")));
    }

    // --- Campos null (paths sin dato) ---

    @Test
    void dailyPlayMinutes_null_noFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", null, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );
        var flags = formAnalysisRules.evaluate(event);
        assertTrue(flags.stream().noneMatch(f -> f.code().equals("INSUFFICIENT_PLAY_TIME")));
    }

    @Test
    void housingSize_null_noFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", null, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );
        var flags = formAnalysisRules.evaluate(event);
        assertTrue(flags.stream().noneMatch(f -> f.code().equals("SMALL_HOUSING")));
    }

    @Test
    void rentalPetsAllowed_null_isRental_true_triggersCriticalFlag() {
        // rentalPetsAllowed=null equivale a "sin permiso explícito" → dispara RENTAL_NO_PERMISSION
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, true, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );
        var flags = formAnalysisRules.evaluate(event);
        assertTrue(flags.stream().anyMatch(f ->
                f.severity() == FlagSeverity.Critical && f.code().equals("RENTAL_NO_PERMISSION")));
    }

    // --- Keywords adicionales ---

    @Test
    void physicalPunishment_bofetadaKeyword_triggersCriticalFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "le daría una bofetada",
                true, true, "amor", true, true, true, false, null
        );
        var flags = formAnalysisRules.evaluate(event);
        assertTrue(flags.stream().anyMatch(f ->
                f.severity() == FlagSeverity.Critical && f.code().equals("PHYSICAL_PUNISHMENT")));
    }

    @Test
    void abandonmentHistory_tireKeyword_triggersCriticalFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "lo tiré a la calle cuando me mudé", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );
        var flags = formAnalysisRules.evaluate(event);
        assertTrue(flags.stream().anyMatch(f ->
                f.severity() == FlagSeverity.Critical && f.code().equals("ABANDONMENT_HISTORY")));
    }

    @Test
    void superficialMotivation_caprichoKeyword_triggersWarningFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "Fue un capricho del momento", true, true, true, false, null
        );
        var flags = formAnalysisRules.evaluate(event);
        assertTrue(flags.stream().anyMatch(f ->
                f.severity() == FlagSeverity.Warning && f.code().equals("SUPERFICIAL_MOTIVATION")));
    }

    // --- Enriquecimiento parcial ---

    @Test
    void enrichmentSpace_onlyVerticalSpace_noFlag() {
        // Ambas condiciones deben ser false para disparar; solo una false → no flag
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, false, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );
        var flags = formAnalysisRules.evaluate(event);
        assertTrue(flags.stream().noneMatch(f -> f.code().equals("NO_ENRICHMENT_SPACE")));
    }

    @Test
    void physicalPunishment_englishKeyword_triggersCriticalFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "I would smack it if it scratches me",
                true, true, "amor", true, true, true, false, null
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream().anyMatch(f ->
                f.severity() == FlagSeverity.Critical && f.code().equals("PHYSICAL_PUNISHMENT")));
    }
}