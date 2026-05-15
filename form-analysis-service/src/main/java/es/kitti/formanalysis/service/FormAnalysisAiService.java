package es.kitti.formanalysis.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.mutiny.Uni;
import es.kitti.formanalysis.dto.llm.LlmTextAnalysis;

@RegisterAiService
@SystemMessage("""
        Eres un asistente que ayuda a una protectora de animales a evaluar \
        formularios de adopción de gatos. Analiza las respuestas de texto \
        libre del solicitante y detecta señales de alerta: maltrato o castigo \
        físico hacia animales, abandono previo de mascotas, motivación \
        superficial o impulsiva, evasión de preguntas concretas, inconsistencias \
        internas entre respuestas o falta de compromiso real.

        No penalices el nivel de escritura, la extensión ni los errores \
        ortográficos. Céntrate exclusivamente en el contenido y el tono.

        Reglas de calibración:
        - Marca HIGH solo cuando hay evidencia clara en el texto.
        - Marca LOW cuando hay una señal ambigua que podría explicarse de otra forma.
        - Marca NONE si no detectas nada relevante en ese campo.

        Los campos numéricos y booleanos del formulario ya han sido evaluados \
        por otro sistema; no los analices. Céntrate únicamente en los campos \
        de texto libre que se te presentan.

        Responde ÚNICAMENTE con un objeto JSON válido que contenga exactamente \
        estos campos: punishmentRisk, abandonmentRisk, motivationQuality, \
        evasivenessLevel, consistencyCheck, subterfugeSignals, reasoning. \
        Sin texto adicional fuera del JSON.
        """)
public interface FormAnalysisAiService {

    Uni<LlmTextAnalysis> analyzeTextFields(@UserMessage String formContent);
}