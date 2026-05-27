package es.kitti.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.kitti.notification.client.UserServiceClient;
import es.kitti.notification.client.UserServiceClient.UserSummary;
import es.kitti.notification.dto.NotificationResponse;
import es.kitti.notification.entity.Notification;
import es.kitti.notification.entity.NotificationType;
import es.kitti.notification.event.AdoptionFormAnalysedEvent;
import es.kitti.notification.mapper.NotificationMapper;
import es.kitti.notification.service.NotificationWriteService;
import es.kitti.notification.sse.SseSubscriptionManager;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdoptionFormAnalysedConsumerUnitTest {

    @Mock ReactiveMailer mailer;
    @Mock ObjectMapper objectMapper;
    @Mock NotificationWriteService writeService;
    @Mock SseSubscriptionManager sseManager;
    @Mock NotificationMapper mapper;
    @Mock UserServiceClient userServiceClient;
    @Mock Template rejectionTemplate;
    @Mock Template reviewRequiredTemplate;
    @Mock Template approvedTemplate;
    @Mock TemplateInstance templateInstance;

    @InjectMocks AdoptionFormAnalysedConsumer consumer;

    private final UserSummary user = new UserSummary(42L, "adopter@test.es", "Ana");

    @BeforeEach
    void setUp() {
        consumer.internalSecret = "test-secret";
    }

    private AdoptionFormAnalysedEvent event(String decision) {
        return new AdoptionFormAnalysedEvent(10L, decision, "Motivo", 1, 0, 0, 42L);
    }

    private Notification persistedNotification() {
        Notification n = new Notification();
        n.id = 1L;
        n.userId = 42L;
        n.type = NotificationType.AdoptionDecision;
        n.code = "ADOPTION_APPROVED";
        n.title = "Test";
        n.createdAt = LocalDateTime.now();
        return n;
    }

    private NotificationResponse notificationResponse() {
        return new NotificationResponse(
                1L, "AdoptionDecision", "ADOPTION_APPROVED", "Test",
                null, null, false, null, LocalDateTime.now()
        );
    }

    private void stubEmailAndPersist(String decision) throws Exception {
        var evt = event(decision);
        var json = "json";
        when(objectMapper.readValue(json, AdoptionFormAnalysedEvent.class)).thenReturn(evt);
        when(userServiceClient.findById(42L, "test-secret"))
                .thenReturn(Uni.createFrom().item(user));

        switch (decision) {
            case "Rejected" -> {
                when(rejectionTemplate.data(anyString(), any())).thenReturn(templateInstance);
                when(templateInstance.render()).thenReturn("<html>rejected</html>");
            }
            case "ReviewRequired" -> {
                when(reviewRequiredTemplate.render()).thenReturn("<html>review</html>");
            }
            case "Approved" -> {
                when(approvedTemplate.render()).thenReturn("<html>approved</html>");
            }
        }
        when(mailer.send(any(Mail.class))).thenReturn(Uni.createFrom().voidItem());

        var persisted = persistedNotification();
        when(writeService.create(eq(42L), eq(NotificationType.AdoptionDecision),
                anyString(), anyString(), any(), anyString()))
                .thenReturn(Uni.createFrom().item(persisted));
        when(mapper.toResponse(persisted)).thenReturn(notificationResponse());
    }

    // --- Email + persist + broadcast flow ---

    @Test
    void approved_sendsEmailThenPersistsAndBroadcasts() throws Exception {
        stubEmailAndPersist("Approved");

        consumer.onFormAnalysed("json").await().indefinitely();

        verify(mailer).send(any(Mail.class));
        verify(writeService).create(eq(42L), eq(NotificationType.AdoptionDecision),
                eq("ADOPTION_APPROVED"), eq("¡Tu solicitud de adopción ha sido aprobada!"),
                eq("Motivo"), contains("10"));
        verify(sseManager).broadcast(eq(42L), any(NotificationResponse.class));
    }

    @Test
    void rejected_sendsEmailThenPersistsWithCorrectCode() throws Exception {
        stubEmailAndPersist("Rejected");

        consumer.onFormAnalysed("json").await().indefinitely();

        verify(writeService).create(eq(42L), eq(NotificationType.AdoptionDecision),
                eq("ADOPTION_REJECTED"), eq("Tu solicitud de adopción ha sido rechazada"),
                eq("Motivo"), contains("10"));
    }

    @Test
    void reviewRequired_sendsEmailThenPersistsWithCorrectCode() throws Exception {
        stubEmailAndPersist("ReviewRequired");

        consumer.onFormAnalysed("json").await().indefinitely();

        verify(writeService).create(eq(42L), eq(NotificationType.AdoptionDecision),
                eq("ADOPTION_REVIEW_REQUIRED"), eq("Tu solicitud está siendo revisada"),
                eq("Motivo"), contains("10"));
    }

    @Test
    void broadcast_receivesMapperOutput() throws Exception {
        stubEmailAndPersist("Approved");
        var expectedResponse = notificationResponse();

        consumer.onFormAnalysed("json").await().indefinitely();

        ArgumentCaptor<NotificationResponse> captor = ArgumentCaptor.forClass(NotificationResponse.class);
        verify(sseManager).broadcast(eq(42L), captor.capture());
        assertEquals("ADOPTION_APPROVED", captor.getValue().code());
    }

    @Test
    void metadata_containsAdoptionRequestId() throws Exception {
        stubEmailAndPersist("Approved");

        consumer.onFormAnalysed("json").await().indefinitely();

        ArgumentCaptor<String> metadataCaptor = ArgumentCaptor.forClass(String.class);
        verify(writeService).create(anyLong(), any(), anyString(), anyString(), any(), metadataCaptor.capture());
        assertEquals("{\"adoptionRequestId\":10}", metadataCaptor.getValue());
    }

    // --- Error paths ---

    @Test
    void invalidJson_returnsFailure() throws Exception {
        when(objectMapper.readValue("bad", AdoptionFormAnalysedEvent.class))
                .thenThrow(new RuntimeException("Parse error"));

        assertThrows(RuntimeException.class,
                () -> consumer.onFormAnalysed("bad").await().indefinitely());

        verify(mailer, never()).send(any());
        verify(writeService, never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void emailFails_persistNotCalled() throws Exception {
        var evt = event("Approved");
        when(objectMapper.readValue("json", AdoptionFormAnalysedEvent.class)).thenReturn(evt);
        when(userServiceClient.findById(42L, "test-secret"))
                .thenReturn(Uni.createFrom().item(user));
        when(approvedTemplate.render()).thenReturn("<html>approved</html>");
        when(mailer.send(any(Mail.class)))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("SMTP down")));

        assertThrows(RuntimeException.class,
                () -> consumer.onFormAnalysed("json").await().indefinitely());

        verify(writeService, never()).create(any(), any(), any(), any(), any(), any());
        verify(sseManager, never()).broadcast(anyLong(), any());
    }

    @Test
    void persistFails_emailAlreadySent_broadcastNotCalled() throws Exception {
        var evt = event("Approved");
        when(objectMapper.readValue("json", AdoptionFormAnalysedEvent.class)).thenReturn(evt);
        when(userServiceClient.findById(42L, "test-secret"))
                .thenReturn(Uni.createFrom().item(user));
        when(approvedTemplate.render()).thenReturn("<html>approved</html>");
        when(mailer.send(any(Mail.class))).thenReturn(Uni.createFrom().voidItem());
        when(writeService.create(anyLong(), any(), anyString(), anyString(), any(), anyString()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB down")));

        assertThrows(RuntimeException.class,
                () -> consumer.onFormAnalysed("json").await().indefinitely());

        verify(mailer).send(any(Mail.class));
        verify(sseManager, never()).broadcast(anyLong(), any());
    }
}
