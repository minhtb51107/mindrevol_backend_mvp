package com.example.demo.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.example.demo.user.entity.User;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_activation_tokens")
public class UserActivationToken {

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

    // Hàm tiện ích để kiểm tra token đã hết hạn chưa
    public boolean isExpired() {
        return OffsetDateTime.now().isAfter(this.expiresAt);
    }

    public UserActivationToken(User user) {
        this.user = user;
        this.token = UUID.randomUUID().toString(); // Tạo token ngẫu nhiên
        this.expiresAt = OffsetDateTime.now().plusHours(24); // Token có hiệu lực trong 24 giờ
    }
}