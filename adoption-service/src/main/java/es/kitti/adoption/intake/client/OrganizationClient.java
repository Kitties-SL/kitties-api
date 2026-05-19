package es.kitti.adoption.intake.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.time.temporal.ChronoUnit;
import java.util.List;

@RegisterRestClient(configKey = "organization-service")
@Path("/organizations/internal")
@Produces(MediaType.APPLICATION_JSON)
public interface OrganizationClient {

    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5,
                    delay = 30, delayUnit = ChronoUnit.SECONDS)
    @GET
    @Path("/by-region/{region}")
    Uni<List<OrganizationPublicMinimal>> findByRegion(
            @PathParam("region") String region,
            @HeaderParam("X-Internal-Token") String token
    );
}
