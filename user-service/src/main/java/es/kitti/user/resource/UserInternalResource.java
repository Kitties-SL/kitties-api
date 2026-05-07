package es.kitti.user.resource;

import es.kitti.mon.error.ErrorResponse;
import es.kitti.user.security.InternalOnly;
import es.kitti.user.service.ErasureService;
import es.kitti.user.service.UserService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/users/internal")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@InternalOnly
public class UserInternalResource {

    @Inject
    ErasureService erasureService;

    @Inject
    UserService userService;

    @GET
    @Path("/active")
    public Uni<List<es.kitti.user.dto.UserResponse>> findAllActiveUsers() {
        return userService.findAllActiveUsers();
    }

    @GET
    @Path("/{id}")
    public Uni<Response> findById(@PathParam("id") Long id) {
        return userService.findById(id)
                .onItem().transform(either -> either.fold(
                        err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                        user -> Response.ok(user).build()
                ));
    }

    @POST
    @Path("/purge/erasure")
    public Uni<Response> triggerErasurePurge() {
        return erasureService.purgeEligibleUsers()
                .onItem().transform(v -> Response.noContent().build());
    }

    @POST
    @Path("/purge/activations")
    public Uni<Response> triggerActivationPurge() {
        return erasureService.purgeExpiredUnactivatedUsers()
                .onItem().transform(v -> Response.noContent().build());
    }
}
