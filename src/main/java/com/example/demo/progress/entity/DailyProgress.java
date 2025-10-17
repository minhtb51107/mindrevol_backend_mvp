package com.example.demo.progress.entity;

import com.example.demo.plan.entity.PlanMember;
import jakarta.persistence.*;
import lombok.*;
import com.example.demo.community.entity.ProgressComment;
import com.example.demo.community.entity.ProgressReaction;
import java.util.List;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "daily_progress")
public class DailyProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_member_id", nullable = false)
    private PlanMember planMember;

    @Column(nullable = false)
    private LocalDate date; // Ngày thực hiện tiến độ

    @Builder.Default
    @Column(nullable = false)
    private boolean completed = false; // Trạng thái hoàn thành trong ngày

    @Column(columnDefinition = "TEXT")
    private String notes; // Ghi chú hoặc cảm nhận của người học

    @Column(columnDefinition = "TEXT")
    private String evidence; // Link bằng chứng (ảnh, file, github...)
    
    @OneToMany(mappedBy = "dailyProgress", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProgressComment> comments;

    @OneToMany(mappedBy = "dailyProgress", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProgressReaction> reactions;
}