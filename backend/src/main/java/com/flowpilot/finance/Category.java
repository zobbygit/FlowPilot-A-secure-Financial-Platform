package com.flowpilot.finance;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "categories")
public class Category {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "user_id") private UUID userId;          // null = platform default
    @Column(nullable = false) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private CategoryKind kind;
    private String color;
    @Column(name = "archived_at") private java.time.Instant archivedAt;
    @Column(name = "sort_order", nullable = false) private int sortOrder;

    protected Category() {}

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public CategoryKind getKind() { return kind; }
    public String getColor() { return color; }
    public java.time.Instant getArchivedAt() { return archivedAt; }
}
