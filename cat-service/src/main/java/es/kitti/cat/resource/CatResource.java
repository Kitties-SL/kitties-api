package es.kitti.cat.resource;

import es.kitti.mon.error.ErrorResponse;
import es.kitti.mon.error.ValidationError;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import es.kitti.cat.dto.CatCreateRequest;
import es.kitti.cat.dto.CatInventoryStatsResponse;
import es.kitti.cat.dto.CatSummaryResponse;
import es.kitti.cat.dto.CatUpdateRequest;
import es.kitti.cat.dto.PageResponse;

import java.util.List;
import es.kitti.cat.service.CatService;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Path("/cats")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class CatResource {

    @Inject CatService catService;
    @Inject JsonWebToken jwt;

    @GET
    @PermitAll
    public Uni<Response> search(
            @QueryParam("city") String city,
            @QueryParam("name") String name,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        return catService.search(city, name, page, size)
                .onItem().transform(result -> Response.ok(result).build());
    }

    @GET
    @Path("/{id}")
    @PermitAll
    public Uni<Response> findById(@PathParam("id") Long id) {
        return catService.findById(id)
                .onItem().transform(either -> either.fold(
                        err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                        cat -> Response.ok(cat).build()
                ));
    }

    @POST
    @RolesAllowed("Organization")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> createCat(CatCreateRequest request) {
        Long callerId = Long.parseLong(jwt.getSubject());
        return request.validate().match(
                this::validationFailed,
                valid -> catService.createCat(valid, callerId)
                        .onItem().transform(cat -> Response.status(Response.Status.CREATED).entity(cat).build())
        );
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed("Organization")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> updateCat(@PathParam("id") Long id, CatUpdateRequest request) {
        Long callerId = Long.parseLong(jwt.getSubject());
        return request.validate().match(
                this::validationFailed,
                valid -> catService.updateCat(id, valid, callerId)
                        .onItem().transform(either -> either.fold(
                                err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                                cat -> Response.ok(cat).build()
                        ))
        );
    }

    @GET
    @Path("/mine")
    @RolesAllowed("Organization")
    public Uni<List<CatSummaryResponse>> findMine() {
        Long callerId = Long.parseLong(jwt.getSubject());
        return catService.findMine(callerId);
    }

    @GET
    @Path("/mine/stats")
    @RolesAllowed("Organization")
    public Uni<CatInventoryStatsResponse> getInventoryStats() {
        Long callerId = Long.parseLong(jwt.getSubject());
        return catService.getInventoryStats(callerId);
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("Organization")
    public Uni<Response> deleteCat(@PathParam("id") Long id) {
        Long callerId = Long.parseLong(jwt.getSubject());
        return catService.deleteCat(id, callerId)
                .onItem().transform(either -> either.fold(
                        err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                        v   -> Response.noContent().build()
                ));
    }

    @POST
    @Path("/{id}/images")
    @RolesAllowed("Organization")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Uni<Response> uploadImage(@PathParam("id") Long id, @RestForm("file") FileUpload file) {
        Long callerId = Long.parseLong(jwt.getSubject());
        return catService.uploadImage(id, file, callerId)
                .onItem().transform(either -> either.fold(
                        err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                        cat -> Response.ok(cat).build()
                ));
    }

    @DELETE
    @Path("/{catId}/images/{imageId}")
    @RolesAllowed("Organization")
    public Uni<Response> deleteImage(@PathParam("catId") Long catId, @PathParam("imageId") Long imageId) {
        Long callerId = Long.parseLong(jwt.getSubject());
        return catService.deleteImage(catId, imageId, callerId)
                .onItem().transform(either -> either.fold(
                        err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                        v   -> Response.noContent().build()
                ));
    }

    private Uni<Response> validationFailed(ValidationError err) {
        return Uni.createFrom().item(
                Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build()
        );
    }
}
