package es.kitti.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import es.kitti.notification.event.PasswordChangedEvent;
import es.kitti.notification.service.IpGeoLookupService;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordChangedConsumerUnitTest {

    @Mock ReactiveMailer mailer;
    @Mock ObjectMapper objectMapper;
    @Mock Template passwordChangedTemplate;
    @Mock TemplateInstance templateInstance;
    @Mock IpGeoLookupService ipGeoLookup;

    @InjectMocks
    PasswordChangedConsumer consumer;

    @BeforeEach
    void stubGeoLookup() {
        lenient().when(ipGeoLookup.describe(any()))
                .thenReturn(Uni.createFrom().item("Madrid, España"));
    }

    private PasswordChangedEvent sampleEvent() {
        return new PasswordChangedEvent(
                1L,
                "test@kitti.es",
                "Test",
                "9.9.9.9",
                LocalDateTime.of(2026, 5, 19, 14, 30),
                "jwt-reset-xyz",
                LocalDateTime.of(2026, 5, 20, 14, 30)
        );
    }

    @Test
    void onPasswordChanged_validMessage_sendsEmail() throws Exception {
        var event = sampleEvent();
        var json = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(event);

        when(objectMapper.readValue(json, PasswordChangedEvent.class)).thenReturn(event);
        when(passwordChangedTemplate.data(anyString(), any())).thenReturn(templateInstance);
        when(templateInstance.data(anyString(), any())).thenReturn(templateInstance);
        when(templateInstance.render()).thenReturn("<html>jwt-reset-xyz</html>");
        when(mailer.send(any(Mail.class))).thenReturn(Uni.createFrom().voidItem());

        consumer.onPasswordChanged(json).await().indefinitely();

        verify(mailer).send(any(Mail.class));
    }

    @Test
    void onPasswordChanged_validMessage_sendsToCorrectRecipientWithResetToken() throws Exception {
        var event = sampleEvent();
        var json = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(event);

        when(objectMapper.readValue(json, PasswordChangedEvent.class)).thenReturn(event);
        when(passwordChangedTemplate.data(anyString(), any())).thenReturn(templateInstance);
        when(templateInstance.data(anyString(), any())).thenReturn(templateInstance);
        when(templateInstance.render()).thenReturn("<html>jwt-reset-xyz</html>");
        when(mailer.send(any(Mail.class))).thenReturn(Uni.createFrom().voidItem());

        consumer.onPasswordChanged(json).await().indefinitely();

        ArgumentCaptor<Mail> captor = ArgumentCaptor.forClass(Mail.class);
        verify(mailer).send(captor.capture());
        assertEquals("test@kitti.es", captor.getValue().getTo().get(0));
        assertEquals("Tu contraseña en Kitties ha sido cambiada 🐾", captor.getValue().getSubject());
        assertTrue(captor.getValue().getHtml().contains("jwt-reset-xyz"));
    }

    @Test
    void onPasswordChanged_nullRequestIp_rendersAsDesconocida() throws Exception {
        var event = new PasswordChangedEvent(
                1L, "test@kitti.es", "Test", null,
                LocalDateTime.of(2026, 5, 19, 14, 30),
                "jwt-reset-xyz",
                LocalDateTime.of(2026, 5, 20, 14, 30));
        var json = "{\"any\": \"json\"}";

        when(objectMapper.readValue(json, PasswordChangedEvent.class)).thenReturn(event);
        when(passwordChangedTemplate.data(anyString(), any())).thenReturn(templateInstance);
        when(templateInstance.data(anyString(), any())).thenReturn(templateInstance);
        when(templateInstance.render()).thenReturn("<html>desconocida</html>");
        when(mailer.send(any(Mail.class))).thenReturn(Uni.createFrom().voidItem());

        consumer.onPasswordChanged(json).await().indefinitely();

        verify(templateInstance).data("requestIp", "desconocida");
    }

    @Test
    void onPasswordChanged_invalidJson_returnsFailure_notSwallowed() throws Exception {
        when(objectMapper.readValue("invalid-json", PasswordChangedEvent.class))
                .thenThrow(new RuntimeException("Parse error"));

        var result = consumer.onPasswordChanged("invalid-json");

        assertThrows(RuntimeException.class, () -> result.await().indefinitely());
        verify(mailer, never()).send(any(Mail.class));
    }

    @Test
    void onPasswordChanged_mailerFails_returnsFailure_notSwallowed() throws Exception {
        var event = sampleEvent();
        var json = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(event);

        when(objectMapper.readValue(json, PasswordChangedEvent.class)).thenReturn(event);
        when(passwordChangedTemplate.data(anyString(), any())).thenReturn(templateInstance);
        when(templateInstance.data(anyString(), any())).thenReturn(templateInstance);
        when(templateInstance.render()).thenReturn("<html>jwt-reset-xyz</html>");
        when(mailer.send(any(Mail.class)))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("SMTP down")));

        var result = consumer.onPasswordChanged(json);

        assertThrows(RuntimeException.class, () -> result.await().indefinitely());
    }
}
