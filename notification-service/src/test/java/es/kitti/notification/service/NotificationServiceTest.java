package es.kitti.notification.service;

import es.kitti.mon.either.Either;
import es.kitti.mon.error.DomainError;
import es.kitti.mon.error.ForbiddenError;
import es.kitti.mon.error.NotFoundError;
import es.kitti.notification.dto.NotificationResponse;
import es.kitti.notification.entity.Notification;
import es.kitti.notification.entity.NotificationType;
import es.kitti.notification.mapper.NotificationMapper;
import es.kitti.notification.repository.NotificationRepository;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock NotificationWriteService writeService;
    @Mock NotificationMapper mapper;

    @InjectMocks NotificationService service;

    private Notification notification;
    private NotificationResponse response;

    @BeforeEach
    void setUp() {
        notification = new Notification();
        notification.id = 1L;
        notification.userId = 100L;
        notification.type = NotificationType.AdoptionDecision;
        notification.code = "ADOPTION_APPROVED";
        notification.title = "Test";
        notification.read = false;
        notification.createdAt = LocalDateTime.now();

        response = new NotificationResponse(
                1L, "AdoptionDecision", "ADOPTION_APPROVED", "Test",
                null, null, false, null, notification.createdAt
        );
    }

    @Test
    void findByUserId_returnsMappedList() {
        when(notificationRepository.findByUserId(100L))
                .thenReturn(Uni.createFrom().item(List.of(notification)));
        when(mapper.toResponse(any(Notification.class)))
                .thenReturn(response);

        var result = service.findByUserId(100L).await().indefinitely();

        assertEquals(1, result.size());
        assertEquals("ADOPTION_APPROVED", result.getFirst().code());
    }

    @Test
    void findByUserId_emptyList_returnsEmpty() {
        when(notificationRepository.findByUserId(100L))
                .thenReturn(Uni.createFrom().item(List.of()));

        var result = service.findByUserId(100L).await().indefinitely();

        assertTrue(result.isEmpty());
    }

    @Test
    void countUnread_returnsCount() {
        when(notificationRepository.countUnreadByUserId(100L))
                .thenReturn(Uni.createFrom().item(3L));

        var result = service.countUnread(100L).await().indefinitely();

        assertEquals(3, result.count());
    }

    @Test
    void markRead_success_returnsRight() {
        when(writeService.markRead(1L, 100L))
                .thenReturn(Uni.createFrom().item(Either.right(notification)));
        when(mapper.toResponse(notification)).thenReturn(response);

        var result = service.markRead(1L, 100L).await().indefinitely();

        assertTrue(result.isRight());
        assertEquals("ADOPTION_APPROVED", result.getOrElse(null).code());
    }

    @Test
    void markRead_notFound_returnsLeft404() {
        when(writeService.markRead(999L, 100L))
                .thenReturn(Uni.createFrom().item(Either.left(new NotFoundError("NOTIFICATION_NOT_FOUND"))));

        var result = service.markRead(999L, 100L).await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(404, result.fold(DomainError::httpStatus, r -> 0));
    }

    @Test
    void markRead_wrongUser_returnsLeft403() {
        when(writeService.markRead(1L, 200L))
                .thenReturn(Uni.createFrom().item(Either.left(new ForbiddenError("ACCESS_DENIED"))));

        var result = service.markRead(1L, 200L).await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(403, result.fold(DomainError::httpStatus, r -> 0));
        assertInstanceOf(ForbiddenError.class, ((Either.Left<?, ?>) result).value());
    }

    @Test
    void markAllRead_delegatesToWriteService() {
        when(writeService.markAllRead(100L))
                .thenReturn(Uni.createFrom().item(5));

        var result = service.markAllRead(100L).await().indefinitely();

        assertEquals(5, result);
    }
}
