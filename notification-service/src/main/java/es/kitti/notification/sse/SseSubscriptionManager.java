package es.kitti.notification.sse;

import es.kitti.notification.dto.NotificationResponse;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@ApplicationScoped
public class SseSubscriptionManager {

    private final ConcurrentHashMap<Long, List<MultiEmitter<? super NotificationResponse>>> emitters =
            new ConcurrentHashMap<>();

    public Multi<NotificationResponse> subscribe(Long userId) {
        return Multi.createFrom().emitter(emitter -> {
            emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
            emitter.onTermination(() ->
                    emitters.computeIfPresent(userId, (k, list) -> {
                        list.remove(emitter);
                        return list.isEmpty() ? null : list;
                    })
            );
        });
    }

    public void broadcast(Long userId, NotificationResponse notification) {
        List<MultiEmitter<? super NotificationResponse>> userEmitters = emitters.get(userId);
        if (userEmitters != null) {
            for (var emitter : userEmitters) {
                emitter.emit(notification);
            }
        }
    }
}
