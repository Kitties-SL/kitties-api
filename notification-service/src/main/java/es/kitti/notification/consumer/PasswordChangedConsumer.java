package es.kitti.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.kitti.notification.event.PasswordChangedEvent;
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
public class PasswordChangedConsumer {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Inject
    ReactiveMailer mailer;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "app.frontend.url", defaultValue = "http://localhost:5173")
    String frontendUrl;

    @Inject
    @Location("emails/password-changed")
    Template passwordChangedTemplate;

    @Incoming("password-changed")
    public Uni<Void> onPasswordChanged(String message) {
        try {
            PasswordChangedEvent event = objectMapper.readValue(message, PasswordChangedEvent.class);
            Log.infof("Sending password-changed notification to %s", event.email());

            String resetUrl = frontendUrl + "/account/password-reset?token=" + event.resetToken();

            String html = passwordChangedTemplate
                    .data("name",           event.name())
                    .data("changedAt",      event.changedAt().format(DATE_FORMATTER))
                    .data("requestIp",      event.requestIp() != null ? event.requestIp() : "desconocida")
                    .data("resetUrl",       resetUrl)
                    .data("resetExpiresAt", event.resetExpiresAt().format(DATE_FORMATTER))
                    .render();

            return mailer.send(
                    Mail.withHtml(
                            event.email(),
                            "Tu contraseña en Kitties ha sido cambiada 🐾",
                            html
                    )
            );
        } catch (Exception e) {
            Log.errorf("Error processing password-changed event, routing to DLQ: %s", e.getMessage());
            return Uni.createFrom().failure(e);
        }
    }
}