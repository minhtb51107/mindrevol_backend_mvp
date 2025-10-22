package com.example.demo.plan.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;
import java.util.ArrayList; // Thêm import
import java.util.List; // Thêm import

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

    // --- THÊM CÁC MỐI QUAN HỆ NÀY ---
    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC") // Sắp xếp comment theo thời gian tạo
    @Builder.Default
    private List<TaskComment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("uploadedAt ASC") // Sắp xếp attachment theo thời gian upload
    @Builder.Default
    private List<TaskAttachment> attachments = new ArrayList<>();
    // --- KẾT THÚC THÊM ---

    // --- THÊM PHƯƠNG THỨC TIỆN ÍCH (OPTIONAL) ---
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
    // --- KẾT THÚC THÊM ---
}