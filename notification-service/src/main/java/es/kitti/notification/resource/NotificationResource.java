package es.kitti.notification.resource;

import es.kitti.mon.error.ErrorResponse;
import es.kitti.notification.service.NotificationService;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/notifications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class NotificationResource {

    @Inject
    NotificationService service;

    @Inject
    JsonWebToken jwt;

    @GET
    @RolesAllowed("User")
    public Uni<Response> list() {
        return service.findByUserId(callerId())
                .onItem().transform(list -> Response.ok(list).build());
    }

    @GET
    @Path("/unread-count")
    @RolesAllowed("User")
    public Uni<Response> unreadCount() {
        return service.countUnread(callerId())
                .onItem().transform(count -> Response.ok(count).build());
    }

    @PATCH
    @Path("/{id}/read")
    @RolesAllowed("User")
    public Uni<Response> markRead(@PathParam("id") Long id) {
        return service.markRead(id, callerId())
                .onItem().transform(either -> either.fold(
                        err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                        data -> Response.ok(data).build()
                ));
    }

    @PATCH
    @Path("/read-all")
    @RolesAllowed("User")
    public Uni<Response> markAllRead() {
        return service.markAllRead(callerId())
                .onItem().transform(count -> Response.ok().build());
    }

    private Long callerId() {
        return Long.parseLong(jwt.getSubject());
    }
}
