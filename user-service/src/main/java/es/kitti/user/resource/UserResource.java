package es.kitti.user.resource;

import es.kitti.mon.error.ErrorResponse;
import es.kitti.mon.error.ForbiddenError;
import es.kitti.mon.error.ValidationError;
import es.kitti.user.dto.*;
import es.kitti.user.service.UserService;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
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
                this::validationFailed,
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
        return request.validate().match(
                this::validationFailed,
                valid -> requireSelf(email)
                        .onItem().transformToUni(either -> either.fold(
                                err -> Uni.createFrom().item(Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build()),
                                __ -> userService.updateUser(email, valid)
                                        .onItem().transform(upd -> upd.fold(
                                                e -> Response.status(e.httpStatus()).entity(ErrorResponse.of(e)).build(),
                                                user -> Response.ok(user).build()
                                        ))
                        ))
        );
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
    public Uni<Response> activate(ActivationRequest request) {
        return request.validate().match(
                this::validationFailed,
                valid -> userService.activateByToken(valid.token())
                        .onItem().transform(either -> either.fold(
                                e -> Response.status(e.httpStatus()).entity(ErrorResponse.of(e)).build(),
                                user -> Response.ok(user).build()
                        ))
        );
    }

    @PATCH
    @Path("/{id}/role")
    @RolesAllowed("Organization")
    public Uni<Response> changeRole(@PathParam("id") Long id, ChangeRoleRequest request) {
        return request.validate().match(
                this::validationFailed,
                valid -> userService.changeRole(id, valid.role())
                        .onItem().transform(either -> either.fold(
                                err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                                user -> Response.ok(user).build()
                        ))
        );
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

    @POST
    @Path("/me/password")
    public Uni<Response> changePassword(ChangePasswordRequest request, @Context HttpHeaders headers) {
        Long userId = Long.parseLong(jwt.getSubject());
        String ip = extractIp(headers);
        return request.validate().match(
                this::validationFailed,
                valid -> userService.changePassword(userId, valid, ip)
                        .onItem().transform(either -> either.fold(
                                err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                                __  -> Response.noContent().build()
                        ))
        );
    }

    @POST
    @Path("/password/forgot")
    @PermitAll
    public Uni<Response> forgotPassword(PasswordForgotRequest request) {
        return request.validate().match(
                this::validationFailed,
                valid -> userService.requestPasswordReset(valid.email())
                        .onItem().transform(either -> either.fold(
                                err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                                __  -> Response.noContent().build()
                        ))
        );
    }

    @POST
    @Path("/password/reset")
    @PermitAll
    public Uni<Response> resetPassword(PasswordResetRequest request) {
        return request.validate().match(
                this::validationFailed,
                valid -> userService.resetPassword(valid.token(), valid.newPassword())
                        .onItem().transform(either -> either.fold(
                                err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                                __  -> Response.noContent().build()
                        ))
        );
    }

    @PUT
    @Path("/me/password-policy")
    public Uni<Response> updatePasswordPolicy(PasswordPolicyUpdateRequest request) {
        Long userId = Long.parseLong(jwt.getSubject());
        return request.validate().match(
                this::validationFailed,
                valid -> userService.setPasswordPolicy(userId, valid.strict())
                        .onItem().transform(either -> either.fold(
                                err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                                __  -> Response.noContent().build()
                        ))
        );
    }

    private String extractIp(HttpHeaders headers) {
        String forwarded = headers.getHeaderString("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0].trim() : null;
    }

    private Uni<Response> validationFailed(ValidationError err) {
        return Uni.createFrom().item(
                Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build()
        );
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
