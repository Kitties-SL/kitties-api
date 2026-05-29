package es.kitti.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.kitti.notification.client.UserServiceClient;
import es.kitti.notification.client.UserServiceClient.UserSummary;
import es.kitti.notification.dto.NotificationResponse;
import es.kitti.notification.entity.Notification;
import es.kitti.notification.entity.NotificationType;
import es.kitti.notification.event.AdoptionRequestAcceptedEvent;
import es.kitti.notification.mapper.NotificationMapper;
import es.kitti.notification.service.NotificationWriteService;
import es.kitti.notification.sse.SseSubscriptionManager;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;
import io.quarkus.qute.Template;
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
class AdoptionRequestAcceptedConsumerUnitTest {

    @Mock ReactiveMailer mailer;
    @Mock ObjectMapper objectMapper;
    @Mock NotificationWriteService writeService;
    @Mock SseSubscriptionManager sseManager;
    @Mock NotificationMapper mapper;
    @Mock UserServiceClient userServiceClient;
    @Mock Template acceptedTemplate;

    @InjectMocks AdoptionRequestAcceptedConsumer consumer;

    private final UserSummary user = new UserSummary(42L, "adopter@test.es", "Ana");

    @BeforeEach
    void setUp() {
        consumer.internalSecret = "test-secret";
    }

    private AdoptionRequestAcceptedEvent event() {
        return new AdoptionRequestAcceptedEvent(10L, 5L, 42L, 200L);
    }

    private Notification persistedNotification() {
        Notification n = new Notification();
        n.id = 1L;
        n.userId = 42L;
        n.type = NotificationType.AdoptionDecision;
        n.code = "ADOPTION_ACCEPTED";
        n.title = "Test";
        n.createdAt = LocalDateTime.now();
        return n;
    }

    private NotificationResponse notificationResponse() {
        return new NotificationResponse(
                1L, "AdoptionDecision", "ADOPTION_ACCEPTED", "Test",
                null, null, false, null, LocalDateTime.now()
        );
    }

    private void stubHappyPath() throws Exception {
        when(objectMapper.readValue("json", AdoptionRequestAcceptedEvent.class)).thenReturn(event());
        when(userServiceClient.findById(42L, "test-secret")).thenReturn(Uni.createFrom().item(user));
        when(acceptedTemplate.render()).thenReturn("<html>accepted</html>");
        when(mailer.send(any(Mail.class))).thenReturn(Uni.createFrom().voidItem());
        var persisted = persistedNotification();
        when(writeService.create(eq(42L), eq(NotificationType.AdoptionDecision),
                anyString(), anyString(), any(), anyString()))
                .thenReturn(Uni.createFrom().item(persisted));
        when(mapper.toResponse(persisted)).thenReturn(notificationResponse());
    }

    @Test
    void accepted_sendsEmailThenPersistsAndBroadcasts() throws Exception {
        stubHappyPath();

        consumer.onAccepted("json").await().indefinitely();

        verify(mailer).send(any(Mail.class));
        verify(writeService).create(eq(42L), eq(NotificationType.AdoptionDecision),
                eq("ADOPTION_ACCEPTED"), eq("La protectora ha aceptado tu solicitud de adopción"),
                any(), contains("10"));
        verify(sseManager).broadcast(eq(42L), any(NotificationResponse.class));
    }

    @Test
    void metadata_containsAdoptionRequestIdAndCatId() throws Exception {
        stubHappyPath();

        consumer.onAccepted("json").await().indefinitely();

        ArgumentCaptor<String> metadataCaptor = ArgumentCaptor.forClass(String.class);
        verify(writeService).create(anyLong(), any(), anyString(), anyString(), any(), metadataCaptor.capture());
        assertEquals("{\"adoptionRequestId\":10,\"catId\":5}", metadataCaptor.getValue());
    }

    @Test
    void invalidJson_returnsFailure() throws Exception {
        when(objectMapper.readValue("bad", AdoptionRequestAcceptedEvent.class))
                .thenThrow(new RuntimeException("Parse error"));

        assertThrows(RuntimeException.class,
                () -> consumer.onAccepted("bad").await().indefinitely());

        verify(mailer, never()).send(any());
        verify(writeService, never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void emailFails_persistNotCalled() throws Exception {
        when(objectMapper.readValue("json", AdoptionRequestAcceptedEvent.class)).thenReturn(event());
        when(userServiceClient.findById(42L, "test-secret")).thenReturn(Uni.createFrom().item(user));
        when(acceptedTemplate.render()).thenReturn("<html>accepted</html>");
        when(mailer.send(any(Mail.class)))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("SMTP down")));

        assertThrows(RuntimeException.class,
                () -> consumer.onAccepted("json").await().indefinitely());

        verify(writeService, never()).create(any(), any(), any(), any(), any(), any());
        verify(sseManager, never()).broadcast(anyLong(), any());
    }
}
