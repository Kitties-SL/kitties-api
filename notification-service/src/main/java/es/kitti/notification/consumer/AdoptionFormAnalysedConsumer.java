package es.kitti.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.kitti.notification.client.UserServiceClient;
import es.kitti.notification.client.UserServiceClient.UserSummary;
import es.kitti.notification.entity.NotificationType;
import es.kitti.notification.event.AdoptionFormAnalysedEvent;
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
public class AdoptionFormAnalysedConsumer {

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
    @Location("emails/adoption-rejected")
    Template rejectionTemplate;

    @Inject
    @Location("emails/adoption-review-required")
    Template reviewRequiredTemplate;

    @Inject
    @Location("emails/adoption-approved")
    Template approvedTemplate;

    @Incoming("adoption-form-analysed")
    public Uni<Void> onFormAnalysed(String message) {
        try {
            AdoptionFormAnalysedEvent event = objectMapper.readValue(
                    message, AdoptionFormAnalysedEvent.class);

            Log.infof("Processing adoption-form-analysed for request: %d, decision: %s",
                    event.adoptionRequestId(), event.decision());

            return userServiceClient.findById(event.adopterId(), internalSecret)
                    .onItem().transformToUni(user -> switch (event.decision()) {
                        case "Rejected" -> sendRejectionEmail(event, user);
                        case "ReviewRequired" -> sendReviewRequiredEmail(user);
                        case "Approved" -> sendApprovedEmail(user);
                        default -> {
                            Log.warnf("Unknown decision: %s", event.decision());
                            yield Uni.createFrom().voidItem();
                        }
                    })
                    .onItem().transformToUni(v -> persistAndBroadcast(event))
                    .onFailure().invoke(e ->
                            Log.errorf("Failed to process notification for request %d: %s",
                                    event.adoptionRequestId(), e.getMessage()));

        } catch (Exception e) {
            Log.errorf("Error processing adoption-form-analysed event, routing to DLQ: %s", e.getMessage());
            return Uni.createFrom().failure(e);
        }
    }

    private Uni<Void> sendRejectionEmail(AdoptionFormAnalysedEvent event, UserSummary user) {
        String html = rejectionTemplate
                .data("rejectionReason", event.rejectionReason())
                .render();

        return mailer.send(
                Mail.withHtml(
                        user.email(),
                        "Actualización sobre tu solicitud de adopción en Kitties 🐱",
                        html
                )
        );
    }

    private Uni<Void> sendReviewRequiredEmail(UserSummary user) {
        String html = reviewRequiredTemplate.render();

        return mailer.send(
                Mail.withHtml(
                        user.email(),
                        "Tu solicitud está siendo revisada en Kitties 🐱",
                        html
                )
        );
    }

    private Uni<Void> sendApprovedEmail(UserSummary user) {
        String html = approvedTemplate.render();

        return mailer.send(
                Mail.withHtml(
                        user.email(),
                        "¡Buenas noticias sobre tu solicitud en Kitties! 🐱",
                        html
                )
        );
    }

    private Uni<Void> persistAndBroadcast(AdoptionFormAnalysedEvent event) {
        String code = switch (event.decision()) {
            case "Rejected" -> "FORM_AI_REJECTED";
            case "ReviewRequired" -> "FORM_AI_REVIEW_REQUIRED";
            case "Approved" -> "FORM_AI_APPROVED";
            default -> "FORM_AI_UNKNOWN";
        };
        String title = switch (event.decision()) {
            case "Rejected" -> "Nuestra IA ha revisado tu solicitud y no cumple los requisitos";
            case "ReviewRequired" -> "Nuestra IA ha revisado tu solicitud y la protectora la evaluará en detalle";
            case "Approved" -> "Nuestra IA ha revisado tu solicitud y todo tiene buena pinta";
            default -> "Actualización sobre tu solicitud";
        };
        String metadata = "{\"adoptionRequestId\":" + event.adoptionRequestId() + "}";

        return writeService.create(
                        event.adopterId(),
                        NotificationType.AdoptionDecision,
                        code, title, event.rejectionReason(), metadata)
                .onItem().invoke(n -> sseManager.broadcast(event.adopterId(), mapper.toResponse(n)))
                .replaceWithVoid();
    }
}
