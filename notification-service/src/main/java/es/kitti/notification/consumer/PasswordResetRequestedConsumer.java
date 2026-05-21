package es.kitti.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.kitti.notification.event.PasswordResetRequestedEvent;
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

import java.time.format.DateTimeFormatter;

@ApplicationScoped
public class PasswordResetRequestedConsumer {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Inject
    ReactiveMailer mailer;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "app.frontend.url", defaultValue = "http://localhost:5173")
    String frontendUrl;

    @Inject
    @Location("emails/password-reset-requested")
    Template passwordResetRequestedTemplate;

    @Incoming("password-reset-requested")
    public Uni<Void> onPasswordResetRequested(String message) {
        try {
            PasswordResetRequestedEvent event = objectMapper.readValue(message, PasswordResetRequestedEvent.class);
            Log.infof("Sending password-reset-requested email to %s", event.email());

            String resetUrl = frontendUrl + "/account/password-reset?token=" + event.resetToken();

            String html = passwordResetRequestedTemplate
                    .data("name",           event.name())
                    .data("resetUrl",       resetUrl)
                    .data("resetExpiresAt", event.resetExpiresAt().format(DATE_FORMATTER))
                    .render();

            return mailer.send(
                    Mail.withHtml(
                            event.email(),
                            "Recupera tu contraseña en Kitties 🐾",
                            html
                    )
            );
        } catch (Exception e) {
            Log.errorf("Error processing password-reset-requested event, routing to DLQ: %s", e.getMessage());
            return Uni.createFrom().failure(e);
        }
    }
}
