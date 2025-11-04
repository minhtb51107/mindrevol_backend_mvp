package com.example.demo.community.entity;

import com.example.demo.progress.entity.DailyProgress;
import com.example.demo.progress.entity.checkin.CheckInEvent; // <-- Import mới
import com.example.demo.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "progress_reactions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"check_in_event_id", "user_id"})
)
public class ProgressReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

   
    // === XÓA BỎ LIÊN KẾT CŨ TỚI DAILYPROGRESS ===
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_progress_id", nullable = false)
    private DailyProgress dailyProgress;
    
    
    // === THÊM MỚI LIÊN KẾT TỚI CHECKINEVENT ===
    @ManyToOne(fetch = FetchType.LAZY)
    // Sửa ở đây: Bỏ "nullable = false"
    @JoinColumn(name = "check_in_event_id") 
    private CheckInEvent checkInEvent;
    // === KẾT THÚC THÊM MỚI ===

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // Người thả reaction

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReactionType type;
}