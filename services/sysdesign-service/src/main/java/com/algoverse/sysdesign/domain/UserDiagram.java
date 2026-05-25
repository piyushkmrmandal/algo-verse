package com.algoverse.sysdesign.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_diagrams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDiagram {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "problem_id", nullable = false)
    private UUID problemId;

    @Column(nullable = false)
    @Builder.Default
    private String title = "My Solution";

    @Column(nullable = false)
    @Builder.Default
    private int version = 1;

    @Column(name = "is_submitted", nullable = false)
    @Builder.Default
    private boolean submitted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
