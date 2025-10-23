package com.example.demo.progress.entity;

import com.example.demo.plan.entity.PlanMember;
// Bỏ import EvidenceAttachment nếu không dùng nữa
import jakarta.persistence.*;
import lombok.*;
import com.example.demo.community.entity.ProgressComment;
import com.example.demo.community.entity.ProgressReaction;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet; // *** THAY ĐỔI IMPORT TỪ List SANG Set ***
import java.util.List;
import java.util.Set; // *** THAY ĐỔI IMPORT TỪ List SANG Set ***


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
    private List<String> evidence = new ArrayList<>(); // Giữ là List vì @ElementCollection thường dùng với List

    // Giữ lại comment là List (thứ tự comment quan trọng)
    @OneToMany(mappedBy = "dailyProgress", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC") // Thêm OrderBy để đảm bảo thứ tự
    @Builder.Default
    private List<ProgressComment> comments = new ArrayList<>();

    // *** THAY ĐỔI List<ProgressReaction> thành Set<ProgressReaction> ***
    @OneToMany(mappedBy = "dailyProgress", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<ProgressReaction> reactions = new HashSet<>();
    // *** KẾT THÚC THAY ĐỔI ***

    // Giữ lại completedTaskIds là Set
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "daily_progress_completed_tasks", joinColumns = @JoinColumn(name = "daily_progress_id"))
    @Column(name = "task_id")
    @Builder.Default
    private Set<Long> completedTaskIds = new HashSet<>();

    // Bỏ các phương thức add/removeAttachment nếu đã xóa field attachments
}