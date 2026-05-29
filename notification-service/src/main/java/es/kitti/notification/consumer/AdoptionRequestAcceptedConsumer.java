package es.kitti.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.kitti.notification.client.UserServiceClient;
import es.kitti.notification.client.UserServiceClient.UserSummary;
import es.kitti.notification.entity.NotificationType;
import es.kitti.notification.event.AdoptionRequestAcceptedEvent;
import es.kitti.notification.mapper.NotificationMapper;
import es.kitti.notification.service.NotificationWriteService;
import es.kitti.notification.sse.SseSubscriptionManager;
import io.quarkus.logging.Log;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class AdoptionRequestAcceptedConsumer {

    @Inject
    ReactiveMailer mailer;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    NotificationWriteService writeService;

    @Inject
    SseSubscriptionManager sseManager;

    @Inject
    NotificationMapper mapper;

    @RestClient
    UserServiceClient userServiceClient;

    @ConfigProperty(name = "kitties.internal.secret")
    String internalSecret;

    @Inject
    @Location("emails/adoption-request-accepted")
    Template acceptedTemplate;

    @Incoming("adoption-request-accepted")
    public Uni<Void> onAccepted(String message) {
        try {
            AdoptionRequestAcceptedEvent event = objectMapper.readValue(
                    message, AdoptionRequestAcceptedEvent.class);

            Log.infof("Processing adoption-request-accepted for request: %d", event.adoptionRequestId());

            return userServiceClient.findById(event.adopterId(), internalSecret)
                    .onItem().transformToUni(this::sendAcceptedEmail)
                    .onItem().transformToUni(v -> persistAndBroadcast(event))
                    .onFailure().invoke(e ->
                            Log.errorf("Failed to process acceptance notification for request %d: %s",
                                    event.adoptionRequestId(), e.getMessage()));

        } catch (Exception e) {
            Log.errorf("Error processing adoption-request-accepted event, routing to DLQ: %s", e.getMessage());
            return Uni.createFrom().failure(e);
        }
    }

    private Uni<Void> sendAcceptedEmail(UserSummary user) {
        String html = acceptedTemplate.render();

        return mailer.send(
                Mail.withHtml(
                        user.email(),
                        "¡La protectora ha aceptado tu solicitud en Kitties! 🐱",
                        html
                )
        );
    }

    private Uni<Void> persistAndBroadcast(AdoptionRequestAcceptedEvent event) {
        String metadata = "{\"adoptionRequestId\":" + event.adoptionRequestId()
                + ",\"catId\":" + event.catId() + "}";

        return writeService.create(
                        event.adopterId(),
                        NotificationType.AdoptionDecision,
                        "ADOPTION_ACCEPTED",
                        "La protectora ha aceptado tu solicitud de adopción",
                        "Ya puedes continuar con el siguiente paso del proceso.",
                        metadata)
                .onItem().invoke(n -> sseManager.broadcast(event.adopterId(), mapper.toResponse(n)))
                .replaceWithVoid();
    }
}