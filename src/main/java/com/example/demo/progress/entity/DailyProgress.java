package com.example.demo.progress.entity;

import com.example.demo.plan.entity.PlanMember;
// Bỏ import EvidenceAttachment nếu không dùng nữa
import jakarta.persistence.*;
import lombok.*;
import com.example.demo.community.entity.ProgressComment; // Giữ lại vì vẫn dùng comment cho ngày
import com.example.demo.community.entity.ProgressReaction; // Giữ lại vì vẫn dùng reaction cho ngày

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


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
    private LocalDate date;

    @Builder.Default
    @Column(nullable = false)
    private boolean completed = false;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // Giữ lại trường này cho các link bằng chứng chung của ngày
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "daily_progress_evidence", joinColumns = @JoinColumn(name = "daily_progress_id"))
    @Column(name = "evidence_link", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> evidence = new ArrayList<>(); // Đổi tên thành evidenceLinks nếu muốn rõ ràng hơn

    // --- BỎ PHẦN NÀY ---
    // @OneToMany(mappedBy = "dailyProgress", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    // @Builder.Default
    // private List<EvidenceAttachment> attachments = new ArrayList<>();
    // --- KẾT THÚC BỎ ---

    // Giữ lại comment và reaction cho cả ngày
    @OneToMany(mappedBy = "dailyProgress", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProgressComment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "dailyProgress", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProgressReaction> reactions = new ArrayList<>();

    // Giữ lại completedTaskIds để biết task nào hoàn thành trong ngày
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "daily_progress_completed_tasks", joinColumns = @JoinColumn(name = "daily_progress_id"))
    @Column(name = "task_id")
    @Builder.Default
    private Set<Long> completedTaskIds = new HashSet<>();

    // Bỏ các phương thức add/removeAttachment nếu đã xóa field attachments
    // public void addAttachment(EvidenceAttachment attachment) { ... }
    // public void removeAttachment(EvidenceAttachment attachment) { ... }
}