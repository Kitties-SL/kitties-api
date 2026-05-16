package es.kitti.adoption.resource;

import es.kitti.mon.error.ErrorResponse;
import es.kitti.mon.error.ValidationError;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import es.kitti.adoption.dto.*;
import es.kitti.adoption.service.AdoptionService;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

@Path("/adoptions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class AdoptionResource {

    @Inject AdoptionService adoptionService;
    @Inject JsonWebToken jwt;

    @POST
    @RolesAllowed("User")
    public Uni<Response> createAdoptionRequest(AdoptionRequestCreateRequest request) {
        Long adopterId = Long.parseLong(jwt.getSubject());
        return request.validate().match(
                this::validationFailed,
                valid -> adoptionService.createAdoptionRequest(valid, adopterId)
                        .onItem().transform(either -> either.fold(
                                err  -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                                data -> Response.status(Response.Status.CREATED).entity(data).build()
                        ))
        );
    }

    @GET
    @Path("/{id}")
    public Uni<Response> findById(@PathParam("id") Long id) {
        Long callerId = Long.parseLong(jwt.getSubject());
        return adoptionService.findById(id, callerId)
                .onItem().transform(either -> either.fold(
                        err  -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                        data -> Response.ok(data).build()
                ));
    }

    @GET
    @Path("/my")
    @RolesAllowed("User")
    public Uni<List<AdoptionRequestResponse>> findMyAdoptions() {
        Long adopterId = Long.parseLong(jwt.getSubject());
        return adoptionService.findByAdopterId(adopterId);
    }

    @GET
    @Path("/organization")
    @RolesAllowed("Organization")
    public Uni<List<AdoptionRequestResponse>> findByOrganization() {
        return adoptionService.findByOrganizationId(orgId());
    }

    @GET
    @Path("/organization/pipeline")
    @RolesAllowed("Organization")
    public Uni<AdoptionPipelineStatsResponse> getOrgPipeline() {
        return adoptionService.getOrgPipeline(orgId());
    }

    @GET
    @Path("/organization/cats/{catId}")
    @RolesAllowed("Organization")
    public Uni<List<AdoptionRequestResponse>> findByCatId(@PathParam("catId") Long catId) {
        return adoptionService.findByCatIdForOrg(catId, orgId());
    }

    @PATCH
    @Path("/{id}/status")
    @RolesAllowed("Organization")
    public Uni<Response> updateStatus(@PathParam("id") Long id, AdoptionStatusUpdateRequest request) {
        Long organizationId = orgId();
        return request.validate().match(
                this::validationFailed,
                valid -> adoptionService.updateStatus(id, valid, organizationId)
                        .onItem().transform(either -> either.fold(
                                err  -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                                data -> Response.ok(data).build()
                        ))
        );
    }

    @POST
    @Path("/{id}/form")
    @RolesAllowed("User")
    public Uni<Response> submitRequestForm(@PathParam("id") Long id,
                                           AdoptionRequestFormCreateRequest request) {
        Long adopterId = Long.parseLong(jwt.getSubject());
        return request.validate().match(
                this::validationFailed,
                req -> adoptionService.submitRequestForm(id, req, adopterId)
                        .onItem().transform(either -> either.fold(
                                err  -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                                data -> Response.status(Response.Status.CREATED).entity(data).build()
                        )));
    }

    @POST
    @Path("/{id}/interview")
    @RolesAllowed("Organization")
    public Uni<Response> scheduleInterview(@PathParam("id") Long id, InterviewCreateRequest request) {
        Long organizationId = orgId();
        return request.validate().match(
                this::validationFailed,
                valid -> adoptionService.scheduleInterview(id, valid, organizationId)
                        .onItem().transform(either -> either.fold(
                                err  -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                                data -> Response.status(Response.Status.CREATED).entity(data).build()
                        ))
        );
    }

    @POST
    @Path("/{id}/adoption-form")
    @RolesAllowed("User")
    public Uni<Response> submitAdoptionForm(@PathParam("id") Long id, AdoptionFormCreateRequest request) {
        Long adopterId = Long.parseLong(jwt.getSubject());
        return request.validate().match(
                this::validationFailed,
                valid -> adoptionService.submitAdoptionForm(id, valid, adopterId)
                        .onItem().transform(either -> either.fold(
                                err  -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                                data -> Response.status(Response.Status.CREATED).entity(data).build()
                        ))
        );
    }

    private Long orgId() {
        return ((Number) jwt.getClaim("organizationId")).longValue();
    }

    private Uni<Response> validationFailed(ValidationError err) {
        return Uni.createFrom().item(
                Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build()
        );
    }

}
