package es.kitti.schedule.job;

import com.github.tomakehurst.wiremock.WireMockServer;
import es.kitti.schedule.test.WireMockTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@QuarkusTest
@QuarkusTestResource(WireMockTestResource.class)
class ScheduledJobsTest {

    @Inject UserActivationPurgeJob userActivationPurgeJob;
    @Inject UserErasurePurgeJob    userErasurePurgeJob;
    @Inject AdoptionRetentionJob   adoptionRetentionJob;
    @Inject ChatRetentionJob       chatRetentionJob;
    @Inject AuthTokenPurgeJob      authTokenPurgeJob;

    WireMockServer wireMock;

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
    }

    @Test
    void userActivationPurgeJob_invokesClientOnce() {
        wireMock.stubFor(post(urlEqualTo("/users/internal/purge/activations"))
                .willReturn(aResponse().withStatus(200)));

        userActivationPurgeJob.run().await().indefinitely();

        wireMock.verify(1, postRequestedFor(urlEqualTo("/users/internal/purge/activations")));
    }

    @Test
    void userErasurePurgeJob_invokesClientOnce() {
        wireMock.stubFor(post(urlEqualTo("/users/internal/purge/erasure"))
                .willReturn(aResponse().withStatus(200)));

        userErasurePurgeJob.run().await().indefinitely();

        wireMock.verify(1, postRequestedFor(urlEqualTo("/users/internal/purge/erasure")));
    }

    @Test
    void adoptionRetentionJob_invokesClientOnce() {
        wireMock.stubFor(post(urlEqualTo("/adoptions/internal/retention/run"))
                .willReturn(aResponse().withStatus(200)));

        adoptionRetentionJob.run().await().indefinitely();

        wireMock.verify(1, postRequestedFor(urlEqualTo("/adoptions/internal/retention/run")));
    }

    @Test
    void chatRetentionJob_invokesClientOnce() {
        wireMock.stubFor(post(urlEqualTo("/chats/internal/retention/run"))
                .willReturn(aResponse().withStatus(200)));

        chatRetentionJob.run().await().indefinitely();

        wireMock.verify(1, postRequestedFor(urlEqualTo("/chats/internal/retention/run")));
    }

    @Test
    void authTokenPurgeJob_invokesClientOnce() {
        wireMock.stubFor(post(urlEqualTo("/auth/internal/purge/tokens"))
                .willReturn(aResponse().withStatus(200)));

        authTokenPurgeJob.run().await().indefinitely();

        wireMock.verify(1, postRequestedFor(urlEqualTo("/auth/internal/purge/tokens")));
    }
}
