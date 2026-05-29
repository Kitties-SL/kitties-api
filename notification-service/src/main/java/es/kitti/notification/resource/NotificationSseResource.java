package es.kitti.notification.resource;

import es.kitti.notification.dto.NotificationResponse;
import es.kitti.notification.sse.SseSubscriptionManager;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.RestStreamElementType;

@Path("/notifications")
@Authenticated
public class NotificationSseResource {

    @Inject
    SseSubscriptionManager sseManager;

    @Inject
    JsonWebToken jwt;

    @GET
    @Path("/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    @RolesAllowed("User")
    public Multi<NotificationResponse> stream() {
        Long userId = Long.parseLong(jwt.getSubject());
        return sseManager.subscribe(userId);
    }
}
