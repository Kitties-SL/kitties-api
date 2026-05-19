package es.kitti.formanalysis.service;

import es.kitti.formanalysis.client.NimApiClient;
import es.kitti.formanalysis.client.NimChatRequest;
import es.kitti.formanalysis.client.NimMessage;
import es.kitti.formanalysis.client.NimResponseFormat;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;

@ApplicationScoped
public class NimLlmClient implements LlmTextAnalysisClient {

    private static final String SYSTEM_PROMPT = """
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

            Reglas de formato estrictas:
            - subterfugeSignals SIEMPRE debe ser un array JSON, aunque esté vacío: [] \
            Nunca devuelvas una string como "NONE" o "none" para ese campo.
            - Los valores de los demás campos son siempre strings, nunca arrays.
            """;

    @Inject
    @RestClient
    NimApiClient nimApiClient;

    @ConfigProperty(name = "nvidia.nim.api-key")
    String apiKey;

    @ConfigProperty(name = "nvidia.nim.model")
    String model;

    @Override
    public Uni<String> analyzeTextFields(String prompt) {
        var request = new NimChatRequest(
                model,
                List.of(
                        new NimMessage("system", SYSTEM_PROMPT),
                        new NimMessage("user", prompt)
                ),
                new NimResponseFormat("json_object")
        );
        return nimApiClient.complete("Bearer " + apiKey, request)
                .onItem().transform(r -> r.choices().get(0).message().content());
    }
}
