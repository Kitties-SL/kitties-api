package es.kitti.formanalysis.rules;

import jakarta.enterprise.context.ApplicationScoped;
import es.kitti.formanalysis.event.AdoptionFormSubmittedEvent;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class FormAnalysisRules {

    public List<StructuralSignal> evaluate(AdoptionFormSubmittedEvent event) {
        List<StructuralSignal> signals = new ArrayList<>();

        if (Boolean.TRUE.equals(event.isRental()) &&
                !Boolean.TRUE.equals(event.rentalPetsAllowed())) {
            signals.add(new StructuralSignal(
                    "RENTAL_NO_PERMISSION",
                    "La vivienda es de alquiler y no tiene permiso explícito para mascotas"
            ));
        }

        if (Boolean.TRUE.equals(event.anyoneHasAllergies())) {
            signals.add(new StructuralSignal(
                    "ALLERGY_CONFIRMED",
                    "Algún conviviente tiene alergia a los gatos"
            ));
        }

        if (event.dailyPlayMinutes() != null && event.dailyPlayMinutes() < 15) {
            signals.add(new StructuralSignal(
                    "INSUFFICIENT_PLAY_TIME",
                    "Menos de 15 minutos de juego interactivo diario"
            ));
        }

        if (event.hoursAlonePerDay() != null && event.hoursAlonePerDay() > 10
                && !Boolean.TRUE.equals(event.hasOtherPets())) {
            signals.add(new StructuralSignal(
                    "TOO_MANY_HOURS_ALONE",
                    "El gato estaría solo más de 10 horas al día sin otra mascota"
            ));
        }

        if (!Boolean.TRUE.equals(event.hasVerticalSpace()) &&
                !Boolean.TRUE.equals(event.hasHidingSpots())) {
            signals.add(new StructuralSignal(
                    "NO_ENRICHMENT_SPACE",
                    "La vivienda no tiene alturas accesibles ni zonas de escondite"
            ));
        }

        if (Boolean.TRUE.equals(event.hasChildren()) &&
                !Boolean.TRUE.equals(event.hasPreviousCatExperience()) &&
                containsYoungChildren(event.childrenAges())) {
            signals.add(new StructuralSignal(
                    "YOUNG_CHILDREN_NO_EXPERIENCE",
                    "Niños menores de 4 años en casa sin experiencia previa con gatos"
            ));
        }

        if (!Boolean.TRUE.equals(event.stableHousing())) {
            signals.add(new StructuralSignal(
                    "UNSTABLE_HOUSING",
                    "La vivienda no es estable o hay mudanza prevista"
            ));
        }

        if (!Boolean.TRUE.equals(event.hasWindowsWithView())) {
            signals.add(new StructuralSignal(
                    "NO_WINDOW_VIEW",
                    "Sin ventanas con vistas accesibles para el gato"
            ));
        }

        if (event.housingSize() != null && event.housingSize() < 40) {
            signals.add(new StructuralSignal(
                    "SMALL_HOUSING",
                    "La vivienda tiene menos de 40m²"
            ));
        }

        if (!Boolean.TRUE.equals(event.hasPreviousCatExperience())) {
            signals.add(new StructuralSignal(
                    "NO_PREVIOUS_EXPERIENCE",
                    "El adoptante no ha tenido gatos anteriormente"
            ));
        }

        if (!Boolean.TRUE.equals(event.hasScratchingPost())) {
            signals.add(new StructuralSignal(
                    "NO_SCRATCHING_POST",
                    "No tiene rascador ni planes de comprar uno"
            ));
        }

        return signals;
    }

    private boolean containsYoungChildren(String childrenAges) {
        if (childrenAges == null) return false;
        return childrenAges.matches(".*\\b[0-3]\\b.*");
    }
}
