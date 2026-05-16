package es.kitti.auth.client;

import es.kitti.auth.client.dto.MembershipResponse;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "organization-service")
@Path("/organizations/internal")
@Produces(MediaType.APPLICATION_JSON)
public interface OrganizationInternalClient {

    @GET
    @Path("/membership/{userId}")
    Uni<MembershipResponse> getMembership(
            @PathParam("userId") Long userId,
            @HeaderParam("X-Internal-Token") String internalToken);
}
