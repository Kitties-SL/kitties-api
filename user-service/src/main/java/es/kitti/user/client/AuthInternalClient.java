package es.kitti.user.client;

import es.kitti.user.dto.PasswordResetTokenIssueRequest;
import es.kitti.user.dto.PasswordResetTokenIssueResponse;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "auth-service")
@Path("/auth/internal")
public interface AuthInternalClient {

    @DELETE
    @Path("/tokens/user/{userId}")
    Uni<Response> deleteTokensByUser(
            @PathParam("userId") Long userId,
            @HeaderParam("X-Internal-Token") String internalToken
    );

    @POST
    @Path("/password-reset-token")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<PasswordResetTokenIssueResponse> requestPasswordResetToken(
            PasswordResetTokenIssueRequest request,
            @HeaderParam("X-Internal-Token") String internalToken
    );
}
