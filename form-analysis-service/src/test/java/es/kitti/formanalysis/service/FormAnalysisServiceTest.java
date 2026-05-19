package es.kitti.formanalysis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.inject.Inject;
import es.kitti.formanalysis.dto.llm.LlmTextAnalysis;
import es.kitti.formanalysis.entity.AnalysisDecision;
import es.kitti.formanalysis.entity.FormAnalysis;
import es.kitti.formanalysis.event.AdoptionFormAnalysedEvent;
import es.kitti.formanalysis.event.AdoptionFormSubmittedEvent;
import es.kitti.formanalysis.test.KafkaTestResource;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(KafkaTestResource.class)
class FormAnalysisServiceTest {

    @Inject
    @Connector("smallrye-in-memory")
    InMemoryConnector connector;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    FormAnalysisService service;

    @InjectMock
    FormAnalysisPersistenceService persistenceService;

    @InjectMock
    LlmTextAnalysisClient llmClient;

    @BeforeEach
    void setUp() throws Exception {
        InMemorySink<AdoptionFormAnalysedEvent> sink = connector.sink("adoption-form-analysed");
        sink.clear();

        FormAnalysis savedAnalysis = new FormAnalysis();
        savedAnalysis.id = 1L;

        when(persistenceService.persist(any(FormAnalysis.class), any()))
                .thenReturn(Uni.createFrom().item(savedAnalysis));

        String unavailableJson = objectMapper.writeValueAsString(LlmTextAnalysis.unavailable());
        when(llmClient.analyzeTextFields(any()))
                .thenReturn(Uni.createFrom().item(unavailableJson));
    }

    @Test
    void cleanForm_emitsApprovedDecision() throws Exception {
        InMemorySource<String> source = connector.source("adoption-form-submitted");
        InMemorySink<AdoptionFormAnalysedEvent> sink = connector.sink("adoption-form-analysed");

        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "Los gatos necesitan cazar por instinto",
                30, "Caña, ratones, túneles",
                "Ignorar y redirigir con juguetes",
                true, true,
                "Quiero dar un hogar a un gato",
                true, true, true, false, null
        );

        source.send(objectMapper.writeValueAsString(event));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertFalse(sink.received().isEmpty());
            var analysed = sink.received().get(0).getPayload();
            assertEquals(AnalysisDecision.Approved.name(), analysed.decision());
            assertEquals(0, analysed.criticalFlags());
        });
    }

    @Test
    void formWithCriticalFlag_emitsRejectedDecision() throws Exception {
        InMemorySource<String> source = connector.source("adoption-form-submitted");
        InMemorySink<AdoptionFormAnalysedEvent> sink = connector.sink("adoption-form-analysed");

        var event = new AdoptionFormSubmittedEvent(
                2L, 10L, 100L, 200L,
                true, null, 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes",
                "le pegaría para que aprenda",
                true, true, "amor", true, true, true, false, null
        );

        source.send(objectMapper.writeValueAsString(event));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertFalse(sink.received().isEmpty());
            var analysed = sink.received().get(0).getPayload();
            assertEquals(AnalysisDecision.Rejected.name(), analysed.decision());
            assertTrue(analysed.criticalFlags() >= 1);
        });
    }

    @Test
    void persistenceFailed_noMessageEmitted_failureNotSwallowed() throws Exception {
        when(persistenceService.persist(any(FormAnalysis.class), any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB down")));

        InMemorySource<String> source = connector.source("adoption-form-submitted");
        InMemorySink<AdoptionFormAnalysedEvent> sink = connector.sink("adoption-form-analysed");
        sink.clear();

        var event = new AdoptionFormSubmittedEvent(
                99L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "Los gatos necesitan cazar por instinto",
                30, "Caña, ratones, túneles",
                "Ignorar y redirigir con juguetes",
                true, true,
                "Quiero dar un hogar a un gato",
                true, true, true, false, null
        );

        source.send(objectMapper.writeValueAsString(event));

        Thread.sleep(500); // dar tiempo al consumer para procesar y fallar
        assertTrue(sink.received().isEmpty());
    }

    @Test
    void invalidJson_noEventEmitted() throws Exception {
        InMemorySource<String> source = connector.source("adoption-form-submitted");
        InMemorySink<AdoptionFormAnalysedEvent> sink = connector.sink("adoption-form-analysed");
        sink.clear();

        source.send("not-valid-json{");

        Thread.sleep(500);
        assertTrue(sink.received().isEmpty());
    }

    @Test
    void formWithExactlyOneWarning_emitsReviewRequired() throws Exception {
        InMemorySource<String> source = connector.source("adoption-form-submitted");
        InMemorySink<AdoptionFormAnalysedEvent> sink = connector.sink("adoption-form-analysed");
        sink.clear();

        // Form limpio con dailyPlayMinutes=10 (< 15) → exactamente 1 Warning (INSUFFICIENT_PLAY_TIME)
        var event = new AdoptionFormSubmittedEvent(
                5L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "Los gatos necesitan cazar por instinto",
                10, "Caña, ratones, túneles",   // ← dailyPlayMinutes=10 < 15 → Warning
                "Ignorar y redirigir con juguetes",
                true, true,
                "Quiero dar un hogar a un gato",
                true, true, true, false, null
        );

        source.send(objectMapper.writeValueAsString(event));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertFalse(sink.received().isEmpty());
            var payload = sink.received().get(0).getPayload();
            assertEquals(AnalysisDecision.ReviewRequired.name(), payload.decision());
            assertEquals(0, payload.criticalFlags());
            assertTrue(payload.warningFlags() >= 1);
        });
    }

    @Test
    void formWithWarningFlags_emitsReviewRequiredDecision() throws Exception {
        InMemorySource<String> source = connector.source("adoption-form-submitted");
        InMemorySink<AdoptionFormAnalysedEvent> sink = connector.sink("adoption-form-analysed");

        var event = new AdoptionFormSubmittedEvent(
                3L, 10L, 100L, 200L,
                false, null, 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, false, false, "Quiet",
                "instinto", 10, "juguetes",
                "ignorar",
                false, true, "amor", true, true, true, false, null
        );

        source.send(objectMapper.writeValueAsString(event));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertFalse(sink.received().isEmpty());
            var analysed = sink.received().get(0).getPayload();
            assertNotEquals(AnalysisDecision.Approved.name(), analysed.decision());
        });
    }

    @Test
    void llmUnavailable_fallbackSilencioso_approvedFormSigueSiendoApproved() throws Exception {
        when(llmClient.analyzeTextFields(any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("NVIDIA timeout")));

        InMemorySource<String> source = connector.source("adoption-form-submitted");
        InMemorySink<AdoptionFormAnalysedEvent> sink = connector.sink("adoption-form-analysed");
        sink.clear();

        var event = new AdoptionFormSubmittedEvent(
                7L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "Los gatos necesitan cazar por instinto",
                30, "Caña, ratones, túneles",
                "Ignorar y redirigir con juguetes",
                true, true,
                "Quiero dar un hogar a un gato",
                true, true, true, false, null
        );

        source.send(objectMapper.writeValueAsString(event));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertFalse(sink.received().isEmpty());
            var analysed = sink.received().get(0).getPayload();
            assertEquals(AnalysisDecision.Approved.name(), analysed.decision());
            assertEquals(0, analysed.criticalFlags());
            assertEquals(0, analysed.warningFlags());
        });
    }

    @Test
    void llmMalformedJson_fallbackSilencioso_approvedFormSigueSiendoApproved() throws Exception {
        when(llmClient.analyzeTextFields(any()))
                .thenReturn(Uni.createFrom().item("not-valid-json{{{"));

        InMemorySource<String> source = connector.source("adoption-form-submitted");
        InMemorySink<AdoptionFormAnalysedEvent> sink = connector.sink("adoption-form-analysed");
        sink.clear();

        var event = new AdoptionFormSubmittedEvent(
                10L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "Los gatos necesitan cazar por instinto",
                30, "Caña, ratones, túneles",
                "Ignorar y redirigir con juguetes",
                true, true,
                "Quiero dar un hogar a un gato",
                true, true, true, false, null
        );

        source.send(objectMapper.writeValueAsString(event));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertFalse(sink.received().isEmpty());
            var analysed = sink.received().get(0).getPayload();
            assertEquals(AnalysisDecision.Approved.name(), analysed.decision());
            assertEquals(0, analysed.criticalFlags());
            assertEquals(0, analysed.warningFlags());
        });
    }

    @Test
    void llmWarning_cleanRulesForm_emitsReviewRequired() throws Exception {
        // LLM detecta HIGH punishmentRisk → 1 Warning (LLM_PUNISHMENT_RISK); rules limpias → ReviewRequired
        when(llmClient.analyzeTextFields(any())).thenReturn(Uni.createFrom().item(
                "{\"punishmentRisk\":\"HIGH\",\"abandonmentRisk\":\"NONE\",\"motivationQuality\":\"GENUINE\"," +
                "\"evasivenessLevel\":\"NONE\",\"consistencyCheck\":\"CONSISTENT\"," +
                "\"subterfugeSignals\":[],\"structuralAssessments\":[]," +
                "\"reasoning\":\"Posible riesgo de castigo físico en el texto\"}"));

        InMemorySource<String> source = connector.source("adoption-form-submitted");
        InMemorySink<AdoptionFormAnalysedEvent> sink = connector.sink("adoption-form-analysed");
        sink.clear();

        var event = new AdoptionFormSubmittedEvent(
                8L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "Los gatos necesitan cazar por instinto",
                30, "Caña, ratones, túneles",
                "Ignorar y redirigir con juguetes",
                true, true,
                "Quiero dar un hogar a un gato",
                true, true, true, false, null
        );

        source.send(objectMapper.writeValueAsString(event));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertFalse(sink.received().isEmpty());
            var payload = sink.received().get(0).getPayload();
            assertEquals(AnalysisDecision.ReviewRequired.name(), payload.decision());
            assertEquals(0, payload.criticalFlags());
            assertEquals(1, payload.warningFlags());
        });
    }

    // RED TEST — falla hasta que FormAnalysisService añada onFailure().recoverWith*()
    // Confirma el diagnóstico: un fallo de persistencia propaga la excepción al caller
    // (SmallRye la recibe como Uni fallido, la enruta al DLQ silenciosamente y no queda rastro).
    // El contrato deseado: log del error + Uni completa normalmente → pipeline sigue vivo.
    @Test
    void persistenceFailed_uniDebeCompletarNormalmenteTrasLoguearElError() throws Exception {
        when(persistenceService.persist(any(FormAnalysis.class), any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB connection lost")));

        var event = new AdoptionFormSubmittedEvent(
                99L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "Los gatos necesitan cazar por instinto",
                30, "Caña, ratones, túneles",
                "Ignorar y redirigir con juguetes",
                true, true,
                "Quiero dar un hogar a un gato",
                true, true, true, false, null
        );
        String json = objectMapper.writeValueAsString(event);

        // Actualmente lanza RuntimeException porque el fallo de persist se propaga sin handler.
        // Tras el fix (onFailure().recoverWith*()) debe completar sin excepción.
        assertDoesNotThrow(
                () -> service.onFormSubmitted(json).await().atMost(Duration.ofSeconds(5)),
                "El fallo de persistencia debe loguearse y recuperarse, no propagarse al caller"
        );
    }

    @Test
    void llmThreeWarnings_cleanRulesForm_emitsRejected() throws Exception {
        // LLM detecta HIGH en 3 campos → 3 Warnings → Rejected (aunque ninguno es Critical)
        when(llmClient.analyzeTextFields(any())).thenReturn(Uni.createFrom().item(
                "{\"punishmentRisk\":\"HIGH\",\"abandonmentRisk\":\"HIGH\",\"motivationQuality\":\"SUPERFICIAL\"," +
                "\"evasivenessLevel\":\"NONE\",\"consistencyCheck\":\"CONSISTENT\"," +
                "\"subterfugeSignals\":[],\"structuralAssessments\":[]," +
                "\"reasoning\":\"Múltiples señales de alerta detectadas\"}"));

        InMemorySource<String> source = connector.source("adoption-form-submitted");
        InMemorySink<AdoptionFormAnalysedEvent> sink = connector.sink("adoption-form-analysed");
        sink.clear();

        var event = new AdoptionFormSubmittedEvent(
                9L, 10L, 100L, 200L,
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "Los gatos necesitan cazar por instinto",
                30, "Caña, ratones, túneles",
                "Ignorar y redirigir con juguetes",
                true, true,
                "Quiero dar un hogar a un gato",
                true, true, true, false, null
        );

        source.send(objectMapper.writeValueAsString(event));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertFalse(sink.received().isEmpty());
            var payload = sink.received().get(0).getPayload();
            assertEquals(AnalysisDecision.Rejected.name(), payload.decision());
            assertEquals(0, payload.criticalFlags());
            assertEquals(3, payload.warningFlags());
        });
    }
}
