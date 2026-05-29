package es.kitti.notification.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", schema = "notification")
public class Notification extends PanacheEntity {

    @Column(nullable = false)
    public Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public NotificationType type;

    @Column(nullable = false, length = 100)
    public String code;

    @Column(nullable = false)
    public String title;

    @Column(columnDefinition = "TEXT")
    public String body;

    @Column(columnDefinition = "TEXT")
    public String metadata;

    @Column(nullable = false)
    public boolean read;

    @Column
    public LocalDateTime readAt;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        read = false;
    }
}
