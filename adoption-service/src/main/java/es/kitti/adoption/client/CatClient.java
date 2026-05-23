package es.kitti.adoption.client;

import es.kitti.adoption.client.dto.CatCreateInternalRequest;
import es.kitti.adoption.client.dto.CatResponse;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.time.temporal.ChronoUnit;

@RegisterRestClient(configKey = "cat-service")
@Path("/cats")
@Produces(MediaType.APPLICATION_JSON)
public interface CatClient {

    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5,
                    delay = 30, delayUnit = ChronoUnit.SECONDS)
    @GET
    @Path("/{id}")
    Uni<Response> findById(@PathParam("id") Long id);

    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5,
                    delay = 30, delayUnit = ChronoUnit.SECONDS)
    @POST
    @Path("/internal")
    @Consumes(MediaType.APPLICATION_JSON)
    Uni<CatResponse> createInternal(
            CatCreateInternalRequest body,
            @HeaderParam("X-Internal-Token") String token
    );
}
