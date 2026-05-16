package es.kitti.gateway.config;

import io.quarkus.runtime.Startup;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@Startup
@ApplicationScoped
public class WebClientConfig {

    @Inject
    Vertx vertx;

    private WebClient client;

    @PostConstruct
    void init() {
        client = WebClient.create(vertx, new WebClientOptions().setConnectTimeout(2000));
    }

    @Produces
    @ApplicationScoped
    public WebClient webClient() {
        return client;
    }
}
