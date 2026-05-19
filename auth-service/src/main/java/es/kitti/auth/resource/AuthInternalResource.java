package es.kitti.auth.resource;

import es.kitti.auth.dto.PasswordResetTokenRequest;
import es.kitti.auth.dto.PasswordResetTokenResponse;
import es.kitti.auth.repository.RefreshTokenRepository;
import es.kitti.auth.security.InternalOnly;
import es.kitti.auth.service.JwtTokenService;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/auth/internal")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@InternalOnly
public class AuthInternalResource {

    @Inject
    RefreshTokenRepository refreshTokenRepository;

    @Inject
    JwtTokenService jwtTokenService;

    @DELETE
    @Path("/tokens/user/{userId}")
    @WithTransaction
    public Uni<Response> deleteTokensByUser(@PathParam("userId") Long userId) {
        return refreshTokenRepository.deleteAllByUserId(userId)
                .onItem().transform(count -> Response.noContent().build());
    }

    @POST
    @Path("/purge/tokens")
    @WithTransaction
    public Uni<Response> purgeExpiredTokens() {
        return refreshTokenRepository.deleteExpiredOrRevoked()
                .onItem().transform(count -> Response.noContent().build());
    }

    @POST
    @Path("/password-reset-token")
    public Uni<PasswordResetTokenResponse> issuePasswordResetToken(PasswordResetTokenRequest request) {
        return Uni.createFrom().item(jwtTokenService.generatePasswordResetToken(request.userId()));
    }
}