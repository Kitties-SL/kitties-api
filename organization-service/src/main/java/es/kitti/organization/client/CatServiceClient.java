package es.kitti.organization.client;

import es.kitti.organization.client.dto.CountByOrgsRequest;
import es.kitti.organization.client.dto.OrgCatCount;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.time.temporal.ChronoUnit;
import java.util.List;

@RegisterRestClient(configKey = "cat-service")
@Path("/cats/internal")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface CatServiceClient {

    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5,
                    delay = 30, delayUnit = ChronoUnit.SECONDS)
    @POST
    @Path("/count-by-orgs")
    Uni<List<OrgCatCount>> countByOrgs(
            CountByOrgsRequest body,
            @HeaderParam("X-Internal-Token") String token
    );
}
