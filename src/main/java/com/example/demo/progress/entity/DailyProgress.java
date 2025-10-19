package com.example.demo.progress.entity;

import com.example.demo.plan.entity.PlanMember;
import jakarta.persistence.*;
import lombok.*;
import com.example.demo.community.entity.ProgressComment;
import com.example.demo.community.entity.ProgressReaction;

import java.time.LocalDate;
import java.util.ArrayList; // Thêm import
import java.util.HashSet; // Thêm import
import java.util.List;
import java.util.Set; // Thêm import


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

    @Column(columnDefinition = "TEXT")
    private String evidence;

    @OneToMany(mappedBy = "dailyProgress", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default // Thêm default
    private List<ProgressComment> comments = new ArrayList<>(); // Khởi tạo

    @OneToMany(mappedBy = "dailyProgress", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default // Thêm default
    private List<ProgressReaction> reactions = new ArrayList<>(); // Khởi tạo

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "daily_progress_completed_tasks", joinColumns = @JoinColumn(name = "daily_progress_id"))
    @Column(name = "task_index") // Lưu chỉ số (index) của task đã hoàn thành
    @Builder.Default
    private Set<Integer> completedTaskIndices = new HashSet<>(); // Sử dụng Set để tránh trùng lặp
}