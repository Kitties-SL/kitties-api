package es.kitti.user.client;

import com.fasterxml.jackson.databind.JsonNode;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "chat-service")
@Path("/chats/internal")
public interface ChatInternalClient {

    @DELETE
    @Path("/users/{userId}")
    Uni<Response> anonymizeUser(
            @PathParam("userId") Long userId,
            @HeaderParam("X-Internal-Token") String internalToken
    );

    @GET
    @Path("/users/{userId}/export")
    Uni<JsonNode> exportUser(
            @PathParam("userId") Long userId,
            @HeaderParam("X-Internal-Token") String internalToken
    );
}