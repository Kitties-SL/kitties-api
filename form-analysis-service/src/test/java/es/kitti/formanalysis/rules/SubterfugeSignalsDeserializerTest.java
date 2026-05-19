package es.kitti.formanalysis.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.kitti.formanalysis.dto.llm.LlmTextAnalysis;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SubterfugeSignalsDeserializerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void array_valido_deserializaCorrectamente() throws Exception {
        var json = """
                {"punishmentRisk":"NONE","abandonmentRisk":"NONE","motivationQuality":"GENUINE",
                 "evasivenessLevel":"NONE","consistencyCheck":"CONSISTENT",
                 "subterfugeSignals":["señal1","señal2"],"reasoning":"ok"}
                """;

        var result = mapper.readValue(json, LlmTextAnalysis.class);

        assertEquals(List.of("señal1", "señal2"), result.subterfugeSignals());
    }

    @Test
    void array_vacio_deserializaEnListaVacia() throws Exception {
        var json = """
                {"punishmentRisk":"NONE","abandonmentRisk":"NONE","motivationQuality":"GENUINE",
                 "evasivenessLevel":"NONE","consistencyCheck":"CONSISTENT",
                 "subterfugeSignals":[],"reasoning":"ok"}
                """;

        var result = mapper.readValue(json, LlmTextAnalysis.class);

        assertTrue(result.subterfugeSignals().isEmpty());
    }

    @Test
    void string_NONE_deserializaEnListaVacia() throws Exception {
        var json = """
                {"punishmentRisk":"NONE","abandonmentRisk":"NONE","motivationQuality":"GENUINE",
                 "evasivenessLevel":"NONE","consistencyCheck":"CONSISTENT",
                 "subterfugeSignals":"NONE","reasoning":"ok"}
                """;

        var result = mapper.readValue(json, LlmTextAnalysis.class);

        assertNotNull(result.subterfugeSignals());
        assertTrue(result.subterfugeSignals().isEmpty());
    }

    @Test
    void string_arbitrario_deserializaEnListaVacia() throws Exception {
        var json = """
                {"punishmentRisk":"NONE","abandonmentRisk":"NONE","motivationQuality":"GENUINE",
                 "evasivenessLevel":"NONE","consistencyCheck":"CONSISTENT",
                 "subterfugeSignals":"ninguna señal detectada","reasoning":"ok"}
                """;

        var result = mapper.readValue(json, LlmTextAnalysis.class);

        assertTrue(result.subterfugeSignals().isEmpty());
    }
}
