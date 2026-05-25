package com.algoverse.sysdesign.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "sysdesign_problems")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SysdesignProblem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 10)
    private String difficulty;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(name = "description_md", columnDefinition = "TEXT")
    private String descriptionMd;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "TEXT[]")
    private List<String> requirements;

    @Column(name = "is_published", nullable = false)
    private boolean published = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
