package com.example.demo.community.entity;

import com.example.demo.progress.entity.DailyProgress;
import com.example.demo.progress.entity.checkin.CheckInEvent; // <-- Import mới
import com.example.demo.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "progress_comments")
public class ProgressComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    
    // === XÓA BỎ LIÊN KẾT CŨ TỚI DAILYPROGRESS ===
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_progress_id", nullable = false)
    private DailyProgress dailyProgress;
    

    // === THÊM MỚI LIÊN KẾT TỚI CHECKINEVENT ===
    @ManyToOne(fetch = FetchType.LAZY)
    // Sửa ở đây: Bỏ "nullable = false" (hoặc ghi rõ nullable = true)
    // Điều này cho phép database thêm cột mới vào các hàng đã có
    @JoinColumn(name = "check_in_event_id") 
    private CheckInEvent checkInEvent;
    // === KẾT THÚC THÊM MỚI ===

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User author; // Người viết bình luận

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt = OffsetDateTime.now();
}