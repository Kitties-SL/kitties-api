package es.kitti.adoption.resource;

import es.kitti.adoption.dto.AdoptionDataExport;
import es.kitti.adoption.repository.AdoptionRequestRepository;
import es.kitti.adoption.security.InternalOnly;
import es.kitti.adoption.service.AdoptionAnonymizationWriteService;
import es.kitti.adoption.service.AdoptionService;
import es.kitti.adoption.service.RetentionPurgeService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/adoptions/internal")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@InternalOnly
public class AdoptionInternalResource {

    @Inject
    AdoptionRequestRepository adoptionRequestRepository;

    @Inject
    AdoptionAnonymizationWriteService anonymizationWriteService;

    @Inject
    RetentionPurgeService retentionPurgeService;

    @Inject
    AdoptionService adoptionService;

    @GET
    @Path("/cats/{catId}/active")
    public Uni<Boolean> hasActiveRequestsForCat(@PathParam("catId") Long catId) {
        return adoptionRequestRepository.existsActiveByCatId(catId);
    }

    @DELETE
    @Path("/users/{userId}")
    public Uni<Response> anonymizeUser(@PathParam("userId") Long userId) {
        return anonymizationWriteService.anonymizeUser(userId)
                .onItem().transform(v -> Response.noContent().build());
    }

    @GET
    @Path("/users/{userId}/export")
    public Uni<AdoptionDataExport> exportUser(@PathParam("userId") Long userId) {
        return adoptionService.exportByAdopterId(userId);
    }

    @POST
    @Path("/retention/run")
    public Uni<Response> runRetention() {
        return retentionPurgeService.purgeRejected()
                .chain(() -> retentionPurgeService.anonymizeCompleted())
                .onItem().transform(v -> Response.noContent().build());
    }
}
