package es.kitti.auth.service;

import es.kitti.auth.client.OrganizationInternalClient;
import es.kitti.auth.client.dto.MembershipResponse;
import es.kitti.mon.either.Either;
import es.kitti.mon.either.Unit;
import es.kitti.mon.error.DomainError;
import es.kitti.mon.error.UnauthorizedError;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import es.kitti.auth.dto.AuthRequest;
import es.kitti.auth.dto.AuthResponse;
import es.kitti.auth.dto.RefreshRequest;
import es.kitti.auth.entity.RefreshToken;
import es.kitti.auth.grpc.UserServiceClient;
import es.kitti.auth.repository.RefreshTokenRepository;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.LocalDateTime;
import java.util.UUID;

@ApplicationScoped
public class AuthService {

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    @ConfigProperty(name = "kitties.internal.secret")
    String internalSecret;

    @Inject
    UserServiceClient userServiceClient;

    @RestClient
    OrganizationInternalClient orgClient;

    @Inject
    RefreshTokenRepository refreshTokenRepository;

    @Inject
    JwtTokenService jwtTokenService;

    @WithTransaction
    public Uni<Either<DomainError, AuthResponse>> authenticate(AuthRequest request) {
        return userServiceClient.validateCredentials(request.email(), request.password())
                .onItem().transformToUni(response -> {
                    if (!response.getValid())
                        return Uni.createFrom().item(Either.left(new UnauthorizedError("INVALID_CREDENTIALS")));
                    if ("Organization".equals(response.getRole())) {
                        return orgClient.getMembership(response.getUserId(), internalSecret)
                                .onFailure().recoverWithItem((MembershipResponse) null)
                                .onItem().transformToUni(membership ->
                                        generateTokens(response.getUserId(), response.getEmail(), response.getRole(),
                                                membership != null ? membership.organizationId() : null,
                                                membership != null ? membership.memberRole() : null)
                                                .onItem().transform(Either::<DomainError, AuthResponse>right));
                    }
                    return generateTokens(response.getUserId(), response.getEmail(), response.getRole(), null, null)
                            .onItem().transform(Either::<DomainError, AuthResponse>right);
                });
    }

    @WithTransaction
    public Uni<Either<DomainError, AuthResponse>> refresh(RefreshRequest request) {
        return refreshTokenRepository.findByToken(request.refreshToken())
                .onItem().transformToUni(token -> {
                    if (token == null)
                        return Uni.createFrom().item(Either.left(new UnauthorizedError("TOKEN_NOT_FOUND")));
                    if (!token.isValid())
                        return Uni.createFrom().item(Either.left(new UnauthorizedError("TOKEN_EXPIRED_OR_REVOKED")));
                    token.revoked = true;
                    return refreshTokenRepository.persist(token)
                            .onItem().transformToUni(t -> generateTokens(t.userId, t.email, t.role, t.organizationId, t.memberRole))
                            .onItem().transform(Either::<DomainError, AuthResponse>right);
                });
    }

    @WithTransaction
    public Uni<Either<DomainError, Unit>> logout(String refreshToken) {
        return refreshTokenRepository.findByToken(refreshToken)
                .onItem().transformToUni(token -> {
                    if (token == null)
                        return Uni.createFrom().item(Either.left(new UnauthorizedError("TOKEN_NOT_FOUND")));
                    token.revoked = true;
                    return refreshTokenRepository.persist(token)
                            .onItem().transform(v -> Either.unit());
                });
    }

    private Uni<AuthResponse> generateTokens(long userId, String email, String role,
                                              Long organizationId, String memberRole) {
        String accessToken = jwtTokenService.generateAccessToken(userId, role, organizationId, memberRole);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.token = UUID.randomUUID().toString();
        refreshToken.userId = userId;
        refreshToken.email = email;
        refreshToken.role = role;
        refreshToken.organizationId = organizationId;
        refreshToken.memberRole = memberRole;
        refreshToken.expiresAt = LocalDateTime.now().plusDays(7);

        return refreshTokenRepository.persist(refreshToken)
                .onItem().transform(saved -> new AuthResponse(accessToken, saved.token, 900));
    }
}
