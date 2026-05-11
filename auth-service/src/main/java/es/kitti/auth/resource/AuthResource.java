package es.kitti.auth.resource;

import es.kitti.mon.error.ErrorResponse;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import es.kitti.auth.dto.AuthRequest;
import es.kitti.auth.dto.LogoutRequest;
import es.kitti.auth.dto.RefreshRequest;
import es.kitti.auth.service.AuthService;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    @POST
    @Path("/login")
    public Uni<Response> login(AuthRequest request) {
        return request.validate().match(
                err  -> Uni.createFrom().item(Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build()),
                valid -> authService.authenticate(valid)
                        .onItem().transform(either -> either.fold(
                                err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                                auth -> Response.ok(auth).build()
                        ))
        );
    }

    @POST
    @Path("/refresh")
    public Uni<Response> refresh(RefreshRequest request) {
        return request.validate().match(
                err  -> Uni.createFrom().item(Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build()),
                valid -> authService.refresh(valid)
                        .onItem().transform(either -> either.fold(
                                err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                                auth -> Response.ok(auth).build()
                        ))
        );
    }

    @POST
    @Path("/logout")
    public Uni<Response> logout(LogoutRequest request) {
        return request.validate().match(
                err  -> Uni.createFrom().item(Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build()),
                valid -> authService.logout(valid.refreshToken())
                        .onItem().transform(either -> either.fold(
                                err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                                v   -> Response.noContent().build()
                        ))
        );
    }
}
