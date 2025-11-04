package com.example.demo.progress.entity;

import com.example.demo.plan.entity.PlanMember;
 import com.example.demo.community.entity.ProgressComment; // <-- ĐÃ XÓA
 import com.example.demo.community.entity.ProgressReaction; // <-- ĐÃ XÓA
import jakarta.persistence.*;
import lombok.*;

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
    private List<String> evidence = new ArrayList<>(); 

    // === XÓA BỎ CÁC LIÊN KẾT GÂY LỖI ===
    
    @OneToMany(mappedBy = "dailyProgress", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC") 
    @Builder.Default
    private List<ProgressComment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "dailyProgress", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<ProgressReaction> reactions = new HashSet<>();
    
    // === KẾT THÚC XÓA BỎ ===

    // Giữ lại completedTaskIds là Set
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "daily_progress_completed_tasks", joinColumns = @JoinColumn(name = "daily_progress_id"))
    @Column(name = "task_id")
    @Builder.Default
    private Set<Long> completedTaskIds = new HashSet<>();

}