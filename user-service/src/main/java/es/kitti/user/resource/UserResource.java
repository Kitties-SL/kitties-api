package es.kitti.user.resource;

import es.kitti.mon.error.ErrorResponse;
import es.kitti.mon.error.ForbiddenError;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import es.kitti.user.dto.*;
import jakarta.annotation.security.RolesAllowed;
import es.kitti.user.service.UserService;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class UserResource {

    @Inject UserService userService;
    @Inject JsonWebToken jwt;

    @GET
    @Path("{email}")
    public Uni<Response> findByEmail(@PathParam("email") String email) {
        return requireSelf(email)
                .onItem().transform(either -> either.fold(
                        err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                        user -> Response.ok(user).build()
                ));
    }

    @POST
    @PermitAll
    public Uni<Response> createUser(UserCreateRequest request, @Context UriInfo uriInfo) {
        return request.validate().match(
                err  -> Uni.createFrom().item(Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build()),
                valid -> userService.createUser(valid)
                        .onItem().transform(either -> either.fold(
                                err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                                user -> {
                                    var location = uriInfo.getAbsolutePathBuilder().path(user.email()).build();
                                    return Response.created(location).entity(user).build();
                                }
                        ))
        );
    }

    @PUT
    @Path("/{email}")
    public Uni<Response> updateUser(@PathParam("email") String email, UserUpdateRequest request) {
        return requireSelf(email)
                .onItem().transformToUni(either -> either.fold(
                        err -> Uni.createFrom().item(Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build()),
                        __ -> userService.updateUser(email, request)
                                .onItem().transform(upd -> upd.fold(
                                        e -> Response.status(e.httpStatus()).entity(ErrorResponse.of(e)).build(),
                                        user -> Response.ok(user).build()
                                ))
                ));
    }

    @PUT
    @Path("/{email}/deactivate")
    public Uni<Response> deactivateUser(@PathParam("email") String email) {
        return requireSelf(email)
                .onItem().transformToUni(either -> either.fold(
                        err -> Uni.createFrom().item(Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build()),
                        __ -> userService.deactivateUser(email)
                                .onItem().transform(upd -> upd.fold(
                                        e -> Response.status(e.httpStatus()).entity(ErrorResponse.of(e)).build(),
                                        user -> Response.ok(user).build()
                                ))
                ));
    }

    @PUT
    @Path("/{email}/activate")
    public Uni<Response> activateUser(@PathParam("email") String email) {
        return requireSelf(email)
                .onItem().transformToUni(either -> either.fold(
                        err -> Uni.createFrom().item(Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build()),
                        __ -> userService.activateUser(email)
                                .onItem().transform(upd -> upd.fold(
                                        e -> Response.status(e.httpStatus()).entity(ErrorResponse.of(e)).build(),
                                        user -> Response.ok(user).build()
                                ))
                ));
    }

    @POST
    @Path("/activate")
    @PermitAll
    public Uni<Response> activate(@Valid ActivationRequest request) {
        return userService.activateByToken(request.token())
                .onItem().transform(either -> either.fold(
                        err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                        user -> Response.ok(user).build()
                ));
    }

    @GET
    @Path("/me/export")
    @RolesAllowed("User")
    public Uni<Response> exportMyData() {
        Long userId = Long.parseLong(jwt.getSubject());
        return userService.exportMyData(userId)
                .onItem().transform(either -> either.fold(
                        err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                        data -> Response.ok(data).build()
                ));
    }

    private Uni<es.kitti.mon.either.Either<es.kitti.mon.error.DomainError, UserResponse>> requireSelf(String email) {
        Long callerId = Long.parseLong(jwt.getSubject());
        return userService.findByEmail(email)
                .onItem().transform(either -> either.flatMap(user ->
                        user.id().equals(callerId)
                                ? es.kitti.mon.either.Either.right(user)
                                : es.kitti.mon.either.Either.left(new ForbiddenError("ACCESS_DENIED"))
                ));
    }
}
