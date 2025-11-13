package com.example.demo.user.entity;

import jakarta.persistence.*;
import lombok.Data; // Giả sử bạn dùng Lombok như các entity khác
import java.time.LocalDateTime;

@Entity
@Table(name = "friendships")
@Data
public class Friendship {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester; // Người gửi lời mời

    @ManyToOne
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver; // Người nhận

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FriendshipStatus status; // PENDING, ACCEPTED, DECLINED

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}