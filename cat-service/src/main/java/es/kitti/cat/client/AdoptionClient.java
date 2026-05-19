package es.kitti.cat.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "adoption-service")
@Path("/adoptions/internal")
@Produces(MediaType.APPLICATION_JSON)
public interface AdoptionClient {

    @GET
    @Path("/cats/{catId}/active")
    Uni<Boolean> hasActiveRequestsForCat(
            @PathParam("catId") Long catId,
            @HeaderParam("X-Internal-Token") String token
    );
}