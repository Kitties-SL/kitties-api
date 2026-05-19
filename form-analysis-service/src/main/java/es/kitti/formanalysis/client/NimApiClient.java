package es.kitti.formanalysis.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "nvidia-nim")
@Path("/chat/completions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface NimApiClient {

    @POST
    Uni<NimChatResponse> complete(
            @HeaderParam("Authorization") String bearer,
            NimChatRequest request
    );
}
