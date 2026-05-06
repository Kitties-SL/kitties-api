package es.kitti.chat.resource;

import es.kitti.chat.dto.ChatDataExport;
import es.kitti.chat.dto.CreateConversationRequest;
import es.kitti.chat.security.InternalOnly;
import es.kitti.chat.service.ChatRetentionService;
import es.kitti.chat.service.ChatService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/chats/internal")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@InternalOnly
public class ChatInternalResource {

    @Inject
    ChatService service;

    @Inject
    ChatRetentionService retentionService;

    @POST
    @Path("/conversations")
    public Uni<Response> createConversation(@Valid CreateConversationRequest request) {
        return service.createConversation(request)
                .onItem().transform(c -> Response.status(Response.Status.CREATED).entity(c).build());
    }

    @DELETE
    @Path("/users/{userId}")
    public Uni<Response> anonymizeUser(@PathParam("userId") Long userId) {
        return service.anonymizeUser(userId)
                .onItem().transform(v -> Response.noContent().build());
    }

    @GET
    @Path("/users/{userId}/export")
    public Uni<ChatDataExport> exportUser(@PathParam("userId") Long userId) {
        return service.exportByUserId(userId);
    }

    @POST
    @Path("/retention/run")
    public Uni<Response> runRetention() {
        return retentionService.purgeInactiveConversations()
                .onItem().transform(v -> Response.noContent().build());
    }
}