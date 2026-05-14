package es.kitti.cat.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import es.kitti.cat.client.dto.StorageResponse;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.time.temporal.ChronoUnit;

@RegisterRestClient(configKey = "storage-service")
@Path("/storage")
public interface StorageClient {

    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5,
                    delay = 30, delayUnit = ChronoUnit.SECONDS)
    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<StorageResponse> upload(@RestForm("file") FileUpload file);

    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5,
                    delay = 30, delayUnit = ChronoUnit.SECONDS)
    @DELETE
    @Path("/{key}")
    Uni<Void> delete(@PathParam("key") String key);
}