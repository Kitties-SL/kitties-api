package es.kitti.chat.resource;

import es.kitti.chat.dto.BlockUserRequest;
import es.kitti.chat.dto.ConversationResponse;
import es.kitti.chat.dto.SendMessageRequest;
import es.kitti.chat.entity.SenderType;
import es.kitti.chat.service.ChatService;
import es.kitti.mon.error.ErrorResponse;
import es.kitti.mon.error.ValidationError;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

@Path("/chats")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class ChatResource {

    @Inject ChatService service;
    @Inject JsonWebToken jwt;

    @GET
    @Path("/mine")
    @RolesAllowed("User")
    public Uni<List<ConversationResponse>> findMineAsUser() {
        return service.findMineAsUser(callerId());
    }

    @GET
    @Path("/organization")
    @RolesAllowed("Organization")
    public Uni<List<ConversationResponse>> findMineAsOrganization() {
        return service.findMineAsOrganization(callerId());
    }

    @GET
    @Path("/{id}/messages")
    public Uni<Response> listMessages(@PathParam("id") Long id) {
        return service.listMessages(id, callerId(), callerType())
                .onItem().transform(either -> either.fold(
                        err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                        msgs -> Response.ok(msgs).build()
                ));
    }

    @POST
    @Path("/{id}/messages")
    public Uni<Response> sendMessage(@PathParam("id") Long id, SendMessageRequest request) {
        return request.validate().match(
                this::validationFailed,
                valid -> service.sendMessage(id, valid, callerId(), callerType())
                        .onItem().transform(either -> either.fold(
                                err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                                m -> Response.status(Response.Status.CREATED).entity(m).build()
                        ))
        );
    }

    @POST
    @Path("/{id}/block")
    @RolesAllowed("Organization")
    public Uni<Response> blockUser(@PathParam("id") Long id, BlockUserRequest request) {
        return request.validate().match(
                this::validationFailed,
                valid -> service.blockUser(id, callerId(), valid)
                        .onItem().transform(either -> either.fold(
                                err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                                v -> Response.noContent().build()
                        ))
        );
    }

    @DELETE
    @Path("/{id}/block")
    @RolesAllowed("Organization")
    public Uni<Response> unblockUser(@PathParam("id") Long id) {
        return service.unblockUser(id, callerId())
                .onItem().transform(either -> either.fold(
                        err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                        v -> Response.noContent().build()
                ));
    }

    private Long callerId() {
        return Long.parseLong(jwt.getSubject());
    }

    private SenderType callerType() {
        return jwt.getGroups().contains("Organization") ? SenderType.Organization : SenderType.User;
    }

    private Uni<Response> validationFailed(ValidationError err) {
        return Uni.createFrom().item(
                Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build()
        );
    }
}
