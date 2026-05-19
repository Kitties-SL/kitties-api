package es.kitti.schedule.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import es.kitti.schedule.test.WireMockTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
@QuarkusTestResource(WireMockTestResource.class)
class UserInternalClientRetryTest {

    @Inject
    @RestClient
    UserInternalClient client;

    @ConfigProperty(name = "kitties.internal.secret")
    String secret;

    WireMockServer wireMock;

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
    }

    @Test
    void triggerErasurePurge_retriesOnConnectionError_succeedsOnThirdAttempt() {
        wireMock.stubFor(post(urlEqualTo("/users/internal/purge/erasure"))
                .inScenario("retry")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
                .willSetStateTo("retry-1"));

        wireMock.stubFor(post(urlEqualTo("/users/internal/purge/erasure"))
                .inScenario("retry")
                .whenScenarioStateIs("retry-1")
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
                .willSetStateTo("retry-2"));

        wireMock.stubFor(post(urlEqualTo("/users/internal/purge/erasure"))
                .inScenario("retry")
                .whenScenarioStateIs("retry-2")
                .willReturn(aResponse().withStatus(200)));

        var response = client.triggerErasurePurge(secret).await().indefinitely();

        assertEquals(200, response.getStatus());
        wireMock.verify(3, postRequestedFor(urlEqualTo("/users/internal/purge/erasure")));
    }

    @Test
    void triggerErasurePurge_exhaustsRetries_throwsProcessingException() {
        wireMock.stubFor(post(urlEqualTo("/users/internal/purge/erasure"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        // Mutiny envuelve el fallo de red en CompletionException al hacer await
        assertThrows(Exception.class,
                () -> client.triggerErasurePurge(secret).await().indefinitely());

        wireMock.verify(4, postRequestedFor(urlEqualTo("/users/internal/purge/erasure")));
    }
}
