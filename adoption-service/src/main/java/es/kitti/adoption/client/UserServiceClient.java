package es.kitti.adoption.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "user-service")
@Path("/users/internal")
@Produces(MediaType.APPLICATION_JSON)
public interface UserServiceClient {

    @GET
    @Path("/{id}")
    Uni<UserSummary> findById(
            @PathParam("id") Long id,
            @HeaderParam("X-Internal-Token") String internalToken);

    record UserSummary(Long id, String email, String name) {}
}