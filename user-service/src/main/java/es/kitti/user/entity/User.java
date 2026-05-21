package es.kitti.user.entity;


import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users", schema = "users")
public class User extends PanacheEntity {
    @Column(nullable = false, unique = true)
    public String email;
    @Column(name = "password_hash", nullable = false)
    public String passwordHash;
    @Column(nullable = false)
    public String name;
    @Column(nullable = false)
    public String surname;
    @Column
    public LocalDate birthdate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public UserStatus status;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public UserRole role;
    @Column(name = "activation_token", unique = true)
    public String activationToken;
    @Column(name = "activation_token_expires_at")
    public LocalDateTime activationTokenExpiresAt;
    @Column(name = "legal_hold_until")
    public LocalDateTime legalHoldUntil;
    @Column(name = "deleted_at")
    public LocalDateTime deletedAt;
    @Column(name = "scheduled_purge_at")
    public LocalDateTime scheduledPurgeAt;
    @Column(name = "previous_password_hash")
    public String previousPasswordHash;
    @Column(name = "password_reset_jti")
    public String passwordResetJti;
    @Column(name = "strict_password_policy", nullable = false)
    @ColumnDefault("false")
    public boolean strictPasswordPolicy;
    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
