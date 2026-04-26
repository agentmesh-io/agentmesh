package com.therighthandapp.agentmesh.security;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Local-IdP user record (M13.2). Backed by the {@code users} table provisioned
 * in Flyway migration V9. Roles are stored as a comma-separated string for
 * portability (avoids creating a join table for a 3-value enum).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAccount {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @Column(nullable = false, length = 190)
    private String username;

    @Column(length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 255)
    @Builder.Default
    private String roles = "viewer";

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "mfa_enabled", nullable = false)
    @Builder.Default
    private boolean mfaEnabled = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean locked = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** Split the comma-separated {@link #roles} into a list. */
    public List<String> rolesAsList() {
        if (roles == null || roles.isBlank()) return List.of();
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}

