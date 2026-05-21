package es.kitti.adoption.intake.resource;

import es.kitti.adoption.intake.dto.IntakeApproveRequest;
import es.kitti.adoption.intake.dto.IntakeDecisionRequest;
import es.kitti.adoption.intake.dto.IntakePipelineStatsResponse;
import es.kitti.adoption.intake.dto.IntakeRequestCreateRequest;
import es.kitti.adoption.intake.dto.IntakeRequestResponse;
import es.kitti.adoption.intake.service.IntakeRequestService;
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

@Path("/intake-requests")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class IntakeRequestResource {

    @Inject IntakeRequestService service;
    @Inject JsonWebToken jwt;

    @POST
    @RolesAllowed("User")
    public Uni<Response> create(IntakeRequestCreateRequest request) {
        Long userId = Long.parseLong(jwt.getSubject());
        return request.validate().match(
                this::validationFailed,
                valid -> service.create(valid, userId)
                        .onItem().transform(r -> Response.status(Response.Status.CREATED).entity(r).build())
        );
    }

    @GET
    @Path("/mine")
    @RolesAllowed("User")
    public Uni<List<IntakeRequestResponse>> findMine() {
        Long userId = Long.parseLong(jwt.getSubject());
        return service.findMine(userId);
    }

    @GET
    @Path("/organization")
    @RolesAllowed("Organization")
    public Uni<List<IntakeRequestResponse>> findByOrganization() {
        Long callerOrgId = Long.parseLong(jwt.getSubject());
        return service.findByOrganization(callerOrgId);
    }

    @GET
    @Path("/organization/stats")
    @RolesAllowed("Organization")
    public Uni<IntakePipelineStatsResponse> getOrgStats() {
        Long callerOrgId = Long.parseLong(jwt.getSubject());
        return service.getOrgStats(callerOrgId);
    }

    @PATCH
    @Path("/{id}/approve")
    @RolesAllowed("Organization")
    public Uni<Response> approve(@PathParam("id") Long id, IntakeApproveRequest request) {
        Long callerOrgId = Long.parseLong(jwt.getSubject());
        return request.validate().match(
                this::validationFailed,
                valid -> service.approve(id, valid, callerOrgId)
                        .onItem().transform(either -> either.fold(
                                err  -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                                data -> Response.ok(data).build()
                        ))
        );
    }

    @PATCH
    @Path("/{id}/reject")
    @RolesAllowed("Organization")
    public Uni<Response> reject(@PathParam("id") Long id, IntakeDecisionRequest decision) {
        Long callerOrgId = Long.parseLong(jwt.getSubject());
        return decision.validate().match(
                this::validationFailed,
                valid -> service.reject(id, valid, callerOrgId)
                        .onItem().transform(either -> either.fold(
                                err  -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                                data -> Response.ok(data).build()
                        ))
        );
    }

    private Uni<Response> validationFailed(ValidationError err) {
        return Uni.createFrom().item(
                Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build()
        );
    }
}
