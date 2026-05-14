package es.kitti.schedule.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.time.temporal.ChronoUnit;

@RegisterRestClient(configKey = "auth-service")
@Path("/auth/internal/purge")
public interface AuthInternalClient {

    @Retry(maxRetries = 3, delay = 2, delayUnit = ChronoUnit.SECONDS)
    @POST
    @Path("/tokens")
    Uni<Response> triggerTokenPurge(@HeaderParam("X-Internal-Token") String token);
}
