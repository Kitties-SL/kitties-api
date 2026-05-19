package es.kitti.formanalysis.service;

import es.kitti.formanalysis.event.AdoptionFormSubmittedEvent;
import es.kitti.formanalysis.rules.StructuralSignal;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class LlmPromptBuilder {

    public String build(AdoptionFormSubmittedEvent event, List<StructuralSignal> structuralSignals) {
        StringBuilder sb = new StringBuilder();
        sb.append("Evalúa las siguientes respuestas del solicitante:\n\n");
        appendField(sb, "Historial de mascotas anteriores", event.previousPetsHistory());
        appendField(sb, "Reacción ante comportamientos no deseados", event.reactionToUnwantedBehavior());
        appendField(sb, "Motivación para adoptar", event.motivationToAdopt());
        appendField(sb, "Por qué los gatos necesitan jugar", event.whyCatsNeedToPlay());
        appendField(sb, "Enriquecimiento planificado", event.plannedEnrichment());

        if (!structuralSignals.isEmpty()) {
            sb.append("---\n");
            sb.append("El sistema de reglas ha detectado las siguientes señales estructurales. ");
            sb.append("Para cada una, evalúa su gravedad real considerando el contexto de las ");
            sb.append("respuestas anteriores y determina si se confirma, atenúa o descarta:\n");
            for (StructuralSignal signal : structuralSignals) {
                sb.append("- ").append(signal.code()).append(": ").append(signal.description()).append("\n");
            }
        }

        return sb.toString();
    }

    private void appendField(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(":\n\"").append(value).append("\"\n\n");
        }
    }
}
