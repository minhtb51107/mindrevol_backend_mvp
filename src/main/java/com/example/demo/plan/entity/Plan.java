package com.example.demo.plan.entity;

import com.example.demo.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private int durationInDays; // Ví dụ: 7 (ngày)

    @Column(columnDefinition = "TEXT")
    private String dailyGoal; // Mục tiêu mỗi ngày

    @Column(nullable = false, unique = true)
    private String shareableLink;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlanMember> members;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate; // Ngày kế hoạch chính thức bắt đầu

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt = OffsetDateTime.now();
    
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanStatus status = PlanStatus.ACTIVE;

    @PrePersist
    protected void onCreate() {
        if (this.shareableLink == null) {
            this.shareableLink = UUID.randomUUID().toString().substring(0, 8);
        }
    }
}