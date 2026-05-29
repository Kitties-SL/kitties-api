package es.kitti.notification.sse;

import es.kitti.notification.dto.NotificationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SseSubscriptionManagerTest {

    SseSubscriptionManager manager;

    @BeforeEach
    void setUp() {
        manager = new SseSubscriptionManager();
    }

    private NotificationResponse sampleNotification(String code) {
        return new NotificationResponse(
                1L, "AdoptionDecision", code, "Test title",
                null, null, false, null, LocalDateTime.now()
        );
    }

    @Test
    void broadcast_toSubscribedUser_receivesNotification() {
        List<NotificationResponse> received = new ArrayList<>();

        var subscription = manager.subscribe(100L)
                .subscribe().with(received::add);

        manager.broadcast(100L, sampleNotification("ADOPTION_APPROVED"));

        assertEquals(1, received.size());
        assertEquals("ADOPTION_APPROVED", received.getFirst().code());

        subscription.cancel();
    }

    @Test
    void broadcast_toDifferentUser_doesNotReceive() {
        List<NotificationResponse> received = new ArrayList<>();

        var subscription = manager.subscribe(100L)
                .subscribe().with(received::add);

        manager.broadcast(200L, sampleNotification("ADOPTION_APPROVED"));

        assertEquals(0, received.size());

        subscription.cancel();
    }

    @Test
    void broadcast_noSubscribers_doesNotThrow() {
        assertDoesNotThrow(() ->
                manager.broadcast(999L, sampleNotification("ADOPTION_APPROVED")));
    }

    @Test
    void subscribe_multipleTabsSameUser_allReceive() {
        List<NotificationResponse> tab1 = new ArrayList<>();
        List<NotificationResponse> tab2 = new ArrayList<>();

        var sub1 = manager.subscribe(100L).subscribe().with(tab1::add);
        var sub2 = manager.subscribe(100L).subscribe().with(tab2::add);

        manager.broadcast(100L, sampleNotification("ADOPTION_REJECTED"));

        assertEquals(1, tab1.size());
        assertEquals(1, tab2.size());

        sub1.cancel();
        sub2.cancel();
    }

    @Test
    void cancel_subscription_cleansUp() {
        List<NotificationResponse> received = new ArrayList<>();

        var subscription = manager.subscribe(100L)
                .subscribe().with(received::add);

        subscription.cancel();

        manager.broadcast(100L, sampleNotification("ADOPTION_APPROVED"));

        assertEquals(0, received.size());
    }
}
