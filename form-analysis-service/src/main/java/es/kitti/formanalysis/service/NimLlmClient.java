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
            formularios de adopción de gatos. Tu trabajo tiene dos partes:

            PARTE 1 — Análisis de texto libre
            Analiza las respuestas escritas por el solicitante y detecta señales \
            de alerta: maltrato o castigo físico hacia animales, abandono previo \
            de mascotas, motivación superficial o impulsiva, evasión de preguntas \
            concretas, inconsistencias internas entre respuestas o falta de \
            compromiso real.

            PARTE 2 — Evaluación contextual de señales estructurales
            Si el mensaje incluye una sección "señales estructurales", el sistema \
            de reglas ha detectado condiciones objetivas que podrían ser \
            preocupantes (mudanza prevista, alergia confirmada, poco tiempo de \
            juego, etc.). Para cada señal, usa las respuestas de texto para \
            determinar si la situación está justificada, atenuada o es \
            genuinamente problemática. No penalices automáticamente: una mudanza \
            puede ser a una vivienda estable, una alergia puede estar controlada. \
            Razona con el contexto real que aporta el solicitante.

            Reglas de calibración (aplican a ambas partes):
            - Marca HIGH cuando hay evidencia clara de riesgo en el texto.
            - Marca LOW cuando hay una señal ambigua que podría explicarse \
            de otra forma.
            - Marca NONE si el texto no confirma el riesgo o lo descarta \
            activamente.

            No penalices el nivel de escritura, la extensión ni los errores \
            ortográficos. Céntrate exclusivamente en el contenido y el tono.

            Responde ÚNICAMENTE con un objeto JSON válido que contenga exactamente \
            estos campos: punishmentRisk, abandonmentRisk, motivationQuality, \
            evasivenessLevel, consistencyCheck, subterfugeSignals, \
            structuralAssessments, reasoning. \
            Sin texto adicional fuera del JSON.

            Reglas de formato estrictas:
            - subterfugeSignals SIEMPRE debe ser un array JSON, aunque esté vacío: [] \
            Nunca devuelvas una string como "NONE" o "none" para ese campo.
            - structuralAssessments SIEMPRE debe ser un array JSON. Devuelve una \
            entrada por cada señal estructural recibida con el formato exacto: \
            {"code":"MISMO_CODIGO_RECIBIDO","severity":"NONE|LOW|HIGH","reasoning":"..."}. \
            Si no hay señales estructurales, devuelve [].
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
