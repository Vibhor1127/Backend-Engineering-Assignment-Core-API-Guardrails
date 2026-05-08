package com.grid07.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Represents a comment on a post, made by either a user or a bot
@Entity
@Table(name = "comments")
@Getter
@Setter
@NoArgsConstructor
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "author_type", nullable = false)
    private String authorType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "depth_level")
    private int depthLevel = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // Automatically sets the creation timestamp before saving
    @PrePersist
    public void setCreatedAt() {
        this.createdAt = LocalDateTime.now();
    }
}
