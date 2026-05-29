package es.kitti.notification.service;

import es.kitti.mon.error.ForbiddenError;
import es.kitti.mon.error.NotFoundError;
import es.kitti.notification.entity.Notification;
import es.kitti.notification.entity.NotificationType;
import es.kitti.notification.repository.NotificationRepository;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationWriteServiceTest {

    @Mock NotificationRepository notificationRepository;

    @InjectMocks NotificationWriteService writeService;

    private Notification existingNotification(Long userId, boolean read) {
        Notification n = new Notification();
        n.id = 1L;
        n.userId = userId;
        n.type = NotificationType.AdoptionDecision;
        n.code = "ADOPTION_APPROVED";
        n.title = "Test";
        n.read = read;
        n.readAt = read ? LocalDateTime.of(2026, 5, 27, 10, 0) : null;
        n.createdAt = LocalDateTime.now();
        return n;
    }

    // --- create ---

    @Test
    void create_persistsWithCorrectFields() {
        when(notificationRepository.persist(any(Notification.class)))
                .thenAnswer(inv -> {
                    Notification n = inv.getArgument(0);
                    n.id = 1L;
                    return Uni.createFrom().item(n);
                });

        var result = writeService.create(
                100L, NotificationType.AdoptionDecision,
                "ADOPTION_APPROVED", "Aprobada", "Body", "{\"id\":1}"
        ).await().indefinitely();

        assertEquals(100L, result.userId);
        assertEquals(NotificationType.AdoptionDecision, result.type);
        assertEquals("ADOPTION_APPROVED", result.code);
        assertEquals("Aprobada", result.title);
        assertEquals("Body", result.body);
        assertEquals("{\"id\":1}", result.metadata);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).persist(captor.capture());
        assertEquals(100L, captor.getValue().userId);
    }

    @Test
    void create_withNullBody_persists() {
        when(notificationRepository.persist(any(Notification.class)))
                .thenAnswer(inv -> {
                    Notification n = inv.getArgument(0);
                    n.id = 2L;
                    return Uni.createFrom().item(n);
                });

        var result = writeService.create(
                100L, NotificationType.AdoptionDecision,
                "ADOPTION_REJECTED", "Rechazada", null, null
        ).await().indefinitely();

        assertNull(result.body);
        assertNull(result.metadata);
    }

    // --- markRead ---

    @Test
    void markRead_success_setsReadAndReadAt() {
        var notification = existingNotification(100L, false);
        when(notificationRepository.findById(1L))
                .thenReturn(Uni.createFrom().item(notification));

        var result = writeService.markRead(1L, 100L).await().indefinitely();

        assertTrue(result.isRight());
        var n = result.getOrElse(null);
        assertTrue(n.read);
        assertNotNull(n.readAt);
    }

    @Test
    void markRead_notFound_returnsLeft404() {
        when(notificationRepository.findById(999L))
                .thenReturn(Uni.createFrom().nullItem());

        var result = writeService.markRead(999L, 100L).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(NotFoundError.class, ((es.kitti.mon.either.Either.Left<?, ?>) result).value());
    }

    @Test
    void markRead_wrongUser_returnsLeft403() {
        var notification = existingNotification(100L, false);
        when(notificationRepository.findById(1L))
                .thenReturn(Uni.createFrom().item(notification));

        var result = writeService.markRead(1L, 200L).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(ForbiddenError.class, ((es.kitti.mon.either.Either.Left<?, ?>) result).value());
    }

    @Test
    void markRead_alreadyRead_returnsRightWithoutChangingReadAt() {
        var originalReadAt = LocalDateTime.of(2026, 5, 27, 10, 0);
        var notification = existingNotification(100L, true);

        when(notificationRepository.findById(1L))
                .thenReturn(Uni.createFrom().item(notification));

        var result = writeService.markRead(1L, 100L).await().indefinitely();

        assertTrue(result.isRight());
        var n = result.getOrElse(null);
        assertTrue(n.read);
        assertEquals(originalReadAt, n.readAt);
    }

    // --- markAllRead ---

    @Test
    void markAllRead_delegatesToRepository() {
        when(notificationRepository.markAllReadByUserId(100L))
                .thenReturn(Uni.createFrom().item(3));

        var result = writeService.markAllRead(100L).await().indefinitely();

        assertEquals(3, result);
        verify(notificationRepository).markAllReadByUserId(100L);
    }

    @Test
    void markAllRead_noneUnread_returnsZero() {
        when(notificationRepository.markAllReadByUserId(100L))
                .thenReturn(Uni.createFrom().item(0));

        var result = writeService.markAllRead(100L).await().indefinitely();

        assertEquals(0, result);
    }
}
