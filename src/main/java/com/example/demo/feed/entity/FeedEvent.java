package com.example.demo.feed.entity;

import com.example.demo.plan.entity.Plan;
import com.example.demo.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "feed_events", indexes = {
        @Index(name = "idx_feedevent_timestamp", columnList = "timestamp DESC"),
        @Index(name = "idx_feedevent_plan_id", columnList = "plan_id")
}) // Thêm index để tối ưu query
public class FeedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private FeedEventType eventType;

    @Builder.Default
    @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime timestamp = OffsetDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id") // Người thực hiện hành động (có thể null cho PLAN_COMPLETE)
    private User actor; // Đổi tên từ 'user' thành 'actor' để rõ nghĩa hơn

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id") // Kế hoạch liên quan (có thể null nếu không áp dụng)
    private Plan plan;

    // Lưu trữ chi tiết bổ sung dưới dạng JSON
    // Ví dụ: {"streakDays": 5, "commentId": 123, "planTitle": "ReactJS Plan"}
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String details;

    // Constructor tiện ích (tùy chọn)
    public FeedEvent(FeedEventType eventType, User actor, Plan plan, String details) {
        this.eventType = eventType;
        this.actor = actor;
        this.plan = plan;
        this.details = details;
    }
}