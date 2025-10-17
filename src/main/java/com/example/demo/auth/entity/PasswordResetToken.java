package com.example.demo.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.example.demo.user.entity.User;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    public boolean isExpired() {
        return OffsetDateTime.now().isAfter(this.expiresAt);
    }

    public PasswordResetToken(User user) {
        this.user = user;
        this.token = UUID.randomUUID().toString();
        // Token reset thường có thời gian hiệu lực ngắn hơn, ví dụ 1 giờ
        this.expiresAt = OffsetDateTime.now().plusHours(1);
    }
}