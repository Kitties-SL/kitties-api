package es.kitti.notification.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.time.temporal.ChronoUnit;

@RegisterRestClient(configKey = "user-service")
@Path("/users/internal")
@Produces(MediaType.APPLICATION_JSON)
public interface UserServiceClient {

    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5,
                    delay = 30, delayUnit = ChronoUnit.SECONDS)
    @GET
    @Path("/{id}")
    Uni<UserSummary> findById(
            @PathParam("id") Long id,
            @HeaderParam("X-Internal-Token") String internalToken);

    record UserSummary(Long id, String email, String name) {}
}
