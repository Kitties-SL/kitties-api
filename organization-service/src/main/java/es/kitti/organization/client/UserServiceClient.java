package es.kitti.organization.client;

import es.kitti.organization.client.dto.CreateUserRequest;
import es.kitti.organization.client.dto.UserCreatedResponse;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "user-service")
@Path("/users")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface UserServiceClient {

    @HEAD
    @Path("/internal/by-email/{email}")
    Uni<Response> checkEmailExists(
            @PathParam("email") String email,
            @HeaderParam("X-Internal-Token") String internalToken);

    @POST
    Uni<UserCreatedResponse> createUser(CreateUserRequest request);

    @POST
    @Path("/internal/{id}/promote-to-organization")
    Uni<Response> promoteToOrganization(
            @PathParam("id") Long id,
            @HeaderParam("X-Internal-Token") String internalToken);
}
