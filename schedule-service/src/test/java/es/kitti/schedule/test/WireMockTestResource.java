package es.kitti.schedule.test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

public class WireMockTestResource implements QuarkusTestResourceLifecycleManager {

    private WireMockServer server;

    @Override
    public Map<String, String> start() {
        server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        server.start();
        String url = server.baseUrl();
        return Map.of(
                "quarkus.rest-client.user-service.url", url,
                "quarkus.rest-client.auth-service.url", url,
                "quarkus.rest-client.adoption-service.url", url,
                "quarkus.rest-client.chat-service.url", url
        );
    }

    @Override
    public void stop() {
        if (server != null) server.stop();
    }

    @Override
    public void inject(TestInjector injector) {
        injector.injectIntoFields(server,
                field -> field.getType().isAssignableFrom(WireMockServer.class));
    }
}
