package com.urlshortener.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Registered user entity.
 *
 * Design:
 *  - Anonymous users can still shorten URLs (userId = null on Url)
 *  - Registered users own their links — visible in dashboard
 *  - Password is stored as BCrypt hash — never plain text
 */
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_user_email", columnList = "email", unique = true)
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;   // BCrypt hashed

    @Column(nullable = false, length = 50)
    private String name;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // One user → many URLs
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Url> urls;
}
