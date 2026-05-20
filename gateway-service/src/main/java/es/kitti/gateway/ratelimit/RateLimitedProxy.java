package es.kitti.gateway.ratelimit;

import es.kitti.gateway.proxy.ProxyService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class RateLimitedProxy {

    private static final long MINUTE_MS = 60_000L;

    @Inject
    ProxyService proxyService;

    @Inject
    IpRateLimiter rateLimiter;

    @ConfigProperty(name = "rate-limit.auth.login", defaultValue = "10")
    int loginLimit;

    @ConfigProperty(name = "rate-limit.auth.refresh", defaultValue = "20")
    int refreshLimit;

    @ConfigProperty(name = "rate-limit.storage.upload", defaultValue = "5")
    int uploadLimit;

    public Uni<Response> proxyLogin(String rateLimitKey, String clientIp,
                                    byte[] body, String authHeader, String contentType) {
        if (!rateLimiter.tryAcquire(rateLimitKey, loginLimit, MINUTE_MS)) {
            throw new RateLimitExceededException();
        }
        return proxyService.proxy("POST", "/api/auth/login", body, authHeader, contentType, clientIp);
    }

    public Uni<Response> proxyRefresh(String clientIp, byte[] body, String authHeader, String contentType) {
        if (!rateLimiter.tryAcquire(clientIp, refreshLimit, MINUTE_MS)) {
            throw new RateLimitExceededException();
        }
        return proxyService.proxy("POST", "/api/auth/refresh", body, authHeader, contentType, clientIp);
    }

    public Uni<Response> proxyStorageUpload(String clientIp, byte[] body, String authHeader, String contentType) {
        if (!rateLimiter.tryAcquire(clientIp, uploadLimit, MINUTE_MS)) {
            throw new RateLimitExceededException();
        }
        return proxyService.proxy("POST", "/api/storage/upload", body, authHeader, contentType, clientIp);
    }
}
