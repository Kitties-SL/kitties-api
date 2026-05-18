package es.kitti.formanalysis.resource;

import es.kitti.formanalysis.service.FormAnalysisQueryService;
import es.kitti.mon.error.ErrorResponse;
import es.kitti.mon.error.ForbiddenError;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.json.JsonNumber;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/form-analysis")
@Produces(MediaType.APPLICATION_JSON)
public class FormAnalysisResource {

    @Inject
    FormAnalysisQueryService queryService;

    @Inject
    JsonWebToken jwt;

    @GET
    @Path("/request/{adoptionRequestId}")
    @RolesAllowed("OrgMember")
    public Uni<Response> findByAdoptionRequestId(@PathParam("adoptionRequestId") Long adoptionRequestId) {
        Long orgId = orgId();
        if (orgId == null)
            return Uni.createFrom().item(
                    Response.status(403).entity(ErrorResponse.of(new ForbiddenError("MISSING_ORG_CLAIM"))).build()
            );

        return queryService.findByAdoptionRequestId(adoptionRequestId, orgId)
                .onItem().transform(either -> either.fold(
                        err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                        detail -> Response.ok(detail).build()
                ));
    }

    private Long orgId() {
        Object claim = jwt.getClaim("organizationId");
        if (claim == null) return null;
        if (claim instanceof Number n) return n.longValue();
        if (claim instanceof JsonNumber jn) return jn.longValue();
        return Long.parseLong(claim.toString());
    }
}
