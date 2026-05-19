package es.kitti.formanalysis.rules;

import es.kitti.formanalysis.event.AdoptionFormSubmittedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void cleanForm_noSignals() {
        var signals = formAnalysisRules.evaluate(buildCleanEvent());
        assertTrue(signals.isEmpty());
    }

    @Test
    void rentalWithoutPermission_triggersSignal() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, null, 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, true, false,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var signals = formAnalysisRules.evaluate(event);

        assertTrue(signals.stream().anyMatch(s -> s.code().equals("RENTAL_NO_PERMISSION")));
    }

    @Test
    void allergyConfirmed_triggersSignal() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, null, 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, true, "alergia leve"
        );

        var signals = formAnalysisRules.evaluate(event);

        assertTrue(signals.stream().anyMatch(s -> s.code().equals("ALLERGY_CONFIRMED")));
    }

    @Test
    void insufficientPlayTime_triggersSignal() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, null, 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 10, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var signals = formAnalysisRules.evaluate(event);

        assertTrue(signals.stream().anyMatch(s -> s.code().equals("INSUFFICIENT_PLAY_TIME")));
    }

    @Test
    void noEnrichmentSpace_triggersSignal() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, null, 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, false, false, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var signals = formAnalysisRules.evaluate(event);

        assertTrue(signals.stream().anyMatch(s -> s.code().equals("NO_ENRICHMENT_SPACE")));
    }

    @Test
    void smallHousing_triggersSignal() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, null, 2, false, null,
                false, null, 8, true, null,
                "Apartment", 30, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var signals = formAnalysisRules.evaluate(event);

        assertTrue(signals.stream().anyMatch(s -> s.code().equals("SMALL_HOUSING")));
    }

    @Test
    void multipleStructuralSignals_allDetected() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, true, false,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, true, "alergia"
        );

        var signals = formAnalysisRules.evaluate(event);

        assertTrue(signals.stream().anyMatch(s -> s.code().equals("RENTAL_NO_PERMISSION")));
        assertTrue(signals.stream().anyMatch(s -> s.code().equals("ALLERGY_CONFIRMED")));
    }

    @Test
    void tooManyHoursAlone_noOtherPets_triggersSignal() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 11, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var signals = formAnalysisRules.evaluate(event);

        assertTrue(signals.stream().anyMatch(s -> s.code().equals("TOO_MANY_HOURS_ALONE")));
    }

    @Test
    void tooManyHoursAlone_withOtherPets_noSignal() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                true, "Un perro", 11, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var signals = formAnalysisRules.evaluate(event);

        assertTrue(signals.stream().noneMatch(s -> s.code().equals("TOO_MANY_HOURS_ALONE")));
    }

    @Test
    void tooManyHoursAlone_exactlyTen_noSignal() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 10, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var signals = formAnalysisRules.evaluate(event);

        assertTrue(signals.stream().noneMatch(s -> s.code().equals("TOO_MANY_HOURS_ALONE")));
    }

    @Test
    void youngChildren_noExperience_ageThree_triggersSignal() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                false, "Murió de vejez", 2, true, "3",
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var signals = formAnalysisRules.evaluate(event);

        assertTrue(signals.stream().anyMatch(s -> s.code().equals("YOUNG_CHILDREN_NO_EXPERIENCE")));
    }

    @Test
    void youngChildren_noExperience_ageFour_noSignal() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                false, "Murió de vejez", 2, true, "4",
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var signals = formAnalysisRules.evaluate(event);

        assertTrue(signals.stream().noneMatch(s -> s.code().equals("YOUNG_CHILDREN_NO_EXPERIENCE")));
    }

    @Test
    void youngChildren_withExperience_noSignal() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, true, "2",
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var signals = formAnalysisRules.evaluate(event);

        assertTrue(signals.stream().noneMatch(s -> s.code().equals("YOUNG_CHILDREN_NO_EXPERIENCE")));
    }

    @Test
    void unstableHousing_triggersSignal() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, false, "Mudanza prevista en 6 meses",
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var signals = formAnalysisRules.evaluate(event);

        assertTrue(signals.stream().anyMatch(s -> s.code().equals("UNSTABLE_HOUSING")));
    }

    @Test
    void noWindowView_triggersSignal() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                false, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var signals = formAnalysisRules.evaluate(event);

        assertTrue(signals.stream().anyMatch(s -> s.code().equals("NO_WINDOW_VIEW")));
    }

    @Test
    void noPreviousExperience_triggersSignal() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                false, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var signals = formAnalysisRules.evaluate(event);

        assertTrue(signals.stream().anyMatch(s -> s.code().equals("NO_PREVIOUS_EXPERIENCE")));
    }

    @Test
    void noScratchingPost_triggersSignal() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                false, true, "amor", true, true, true, false, null
        );

        var signals = formAnalysisRules.evaluate(event);

        assertTrue(signals.stream().anyMatch(s -> s.code().equals("NO_SCRATCHING_POST")));
    }

    @Test
    void playTime_exactlyFifteenMinutes_noSignal() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 15, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var signals = formAnalysisRules.evaluate(event);

        assertTrue(signals.stream().noneMatch(s -> s.code().equals("INSUFFICIENT_PLAY_TIME")));
    }

    @Test
    void playTime_fourteenMinutes_triggersSignal() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 14, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var signals = formAnalysisRules.evaluate(event);

        assertTrue(signals.stream().anyMatch(s -> s.code().equals("INSUFFICIENT_PLAY_TIME")));
    }

    @Test
    void housingSize_exactlyFortySquareMeters_noSignal() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 40, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var signals = formAnalysisRules.evaluate(event);

        assertTrue(signals.stream().noneMatch(s -> s.code().equals("SMALL_HOUSING")));
    }

    @Test
    void rentalWithPermission_noSignal() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, true, true,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var signals = formAnalysisRules.evaluate(event);

        assertTrue(signals.stream().noneMatch(s -> s.code().equals("RENTAL_NO_PERMISSION")));
    }

    @Test
    void dailyPlayMinutes_null_noSignal() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", null, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var signals = formAnalysisRules.evaluate(event);

        assertTrue(signals.stream().noneMatch(s -> s.code().equals("INSUFFICIENT_PLAY_TIME")));
    }

    @Test
    void housingSize_null_noSignal() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", null, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var signals = formAnalysisRules.evaluate(event);

        assertTrue(signals.stream().noneMatch(s -> s.code().equals("SMALL_HOUSING")));
    }

    @Test
    void rentalPetsAllowed_null_isRental_true_triggersSignal() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, true, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var signals = formAnalysisRules.evaluate(event);

        assertTrue(signals.stream().anyMatch(s -> s.code().equals("RENTAL_NO_PERMISSION")));
    }

    @Test
    void enrichmentSpace_onlyVerticalSpace_noSignal() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, false, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var signals = formAnalysisRules.evaluate(event);

        assertTrue(signals.stream().noneMatch(s -> s.code().equals("NO_ENRICHMENT_SPACE")));
    }
}
