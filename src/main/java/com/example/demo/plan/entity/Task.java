package com.example.demo.plan.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate; // --- THÊM IMPORT ---
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "plan_tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "task_order")
    private Integer order;

    @Column(name = "deadline_time")
    private LocalTime deadlineTime;

    // --- THÊM TRƯỜNG NÀY ---
    @Column(name = "task_date")
    private LocalDate taskDate;
    // --- KẾT THÚC THÊM ---

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<TaskComment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("uploadedAt ASC")
    @Builder.Default
    private List<TaskAttachment> attachments = new ArrayList<>();

    // ... (Các phương thức helper giữ nguyên) ...
    
    public void addComment(TaskComment comment) {
        comments.add(comment);
        comment.setTask(this);
    }

    public void removeComment(TaskComment comment) {
        comments.remove(comment);
        comment.setTask(null);
    }

    public void addAttachment(TaskAttachment attachment) {
        attachments.add(attachment);
        attachment.setTask(this);
    }

    public void removeAttachment(TaskAttachment attachment) {
        attachments.remove(attachment);
        attachment.setTask(null);
    }
}