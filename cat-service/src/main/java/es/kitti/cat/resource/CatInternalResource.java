package es.kitti.cat.resource;

import es.kitti.cat.dto.CatCreateInternalRequest;
import es.kitti.cat.dto.CatResponse;
import es.kitti.cat.dto.CountByOrgsRequest;
import es.kitti.cat.dto.OrgCatCountResponse;
import es.kitti.cat.security.InternalOnly;
import es.kitti.cat.service.CatService;
import es.kitti.mon.error.ErrorResponse;
import es.kitti.mon.error.ValidationError;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/cats/internal")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@InternalOnly
public class CatInternalResource {

    @Inject CatService catService;

    @POST
    public Uni<Response> create(CatCreateInternalRequest request) {
        return request.validate().match(
                this::validationFailed,
                valid -> catService.createForOrganization(valid)
                        .onItem().transform(cat -> Response.status(Response.Status.CREATED).entity(cat).build())
        );
    }

    @POST
    @Path("/count-by-orgs")
    public Uni<List<OrgCatCountResponse>> countByOrgs(CountByOrgsRequest request) {
        List<Long> orgIds = request == null ? List.of() : request.orgIds();
        return catService.countActiveByOrgIds(orgIds);
    }

    private Uni<Response> validationFailed(ValidationError err) {
        return Uni.createFrom().item(
                Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build()
        );
    }
}
