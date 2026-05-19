package es.kitti.formanalysis.service;

import es.kitti.formanalysis.client.*;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NimLlmClientTest {

    @Mock
    NimApiClient nimApiClient;

    @InjectMocks
    NimLlmClient nimLlmClient;

    @BeforeEach
    void setUp() {
        nimLlmClient.apiKey = "test-api-key";
        nimLlmClient.model = "test-model";
    }

    @Test
    void analyzeTextFields_extractsContentFromFirstChoice() {
        var content = "{\"punishmentRisk\":\"NONE\",\"reasoning\":\"Sin señales\"}";
        when(nimApiClient.complete(any(), any()))
                .thenReturn(Uni.createFrom().item(response(content)));

        var result = nimLlmClient.analyzeTextFields("prompt de prueba").await().indefinitely();

        assertEquals(content, result);
    }

    @Test
    void analyzeTextFields_sendsCorrectRequestToApi() {
        when(nimApiClient.complete(any(), any()))
                .thenReturn(Uni.createFrom().item(response("{}}")));

        nimLlmClient.analyzeTextFields("el prompt del usuario").await().indefinitely();

        var bearerCaptor = ArgumentCaptor.forClass(String.class);
        var requestCaptor = ArgumentCaptor.forClass(NimChatRequest.class);
        verify(nimApiClient).complete(bearerCaptor.capture(), requestCaptor.capture());

        assertEquals("Bearer test-api-key", bearerCaptor.getValue());

        var request = requestCaptor.getValue();
        assertEquals("test-model", request.model());
        assertEquals("json_object", request.responseFormat().type());

        assertEquals(2, request.messages().size());
        assertEquals("system", request.messages().get(0).role());
        assertFalse(request.messages().get(0).content().isBlank());
        assertEquals("user", request.messages().get(1).role());
        assertEquals("el prompt del usuario", request.messages().get(1).content());
    }

    @Test
    void analyzeTextFields_apiFailure_propagatesException() {
        when(nimApiClient.complete(any(), any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("HTTP 503")));

        var uni = nimLlmClient.analyzeTextFields("prompt");

        var ex = assertThrows(RuntimeException.class, () -> uni.await().indefinitely());
        assertEquals("HTTP 503", ex.getMessage());
    }

    @Test
    void analyzeTextFields_emptyChoices_propagatesError() {
        when(nimApiClient.complete(any(), any()))
                .thenReturn(Uni.createFrom().item(new NimChatResponse(List.of())));

        var uni = nimLlmClient.analyzeTextFields("prompt");

        assertThrows(Exception.class, () -> uni.await().indefinitely());
    }

    private static NimChatResponse response(String content) {
        return new NimChatResponse(List.of(new NimChoice(new NimMessage("assistant", content))));
    }
}
