package es.kitti.formanalysis.service;

import jakarta.enterprise.context.ApplicationScoped;
import es.kitti.formanalysis.event.AdoptionFormSubmittedEvent;

@ApplicationScoped
public class LlmPromptBuilder {

    public String build(AdoptionFormSubmittedEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("Evalúa las siguientes respuestas del solicitante:\n\n");
        appendField(sb, "Historial de mascotas anteriores",
                event.previousPetsHistory());
        appendField(sb, "Reacción ante comportamientos no deseados",
                event.reactionToUnwantedBehavior());
        appendField(sb, "Motivación para adoptar",
                event.motivationToAdopt());
        appendField(sb, "Por qué los gatos necesitan jugar",
                event.whyCatsNeedToPlay());
        appendField(sb, "Enriquecimiento planificado",
                event.plannedEnrichment());
        return sb.toString();
    }

    private void appendField(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(":\n\"").append(value).append("\"\n\n");
        }
    }
}
