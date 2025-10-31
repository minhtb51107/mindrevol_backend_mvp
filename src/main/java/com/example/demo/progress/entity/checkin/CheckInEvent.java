package com.example.demo.progress.entity.checkin;

import com.example.demo.community.entity.ProgressComment; // <-- THÊM IMPORT NÀY
import com.example.demo.community.entity.ProgressReaction; // <-- THÊM IMPORT NÀY
import com.example.demo.plan.entity.PlanMember;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList; // <-- THÊM IMPORT NÀY
import java.util.HashSet;
import java.util.List; // <-- THÊM IMPORT NÀY
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "check_in_events")
public class CheckInEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_member_id", nullable = false)
    private PlanMember planMember; // Người check-in

    @Column(nullable = false, updatable = false)
    private LocalDateTime checkInTimestamp; // Thời điểm check-in chính xác

    @Column(columnDefinition = "TEXT")
    private String notes; // Ghi chú cho lần check-in này

    @OneToMany(mappedBy = "checkInEvent", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<CheckInAttachment> attachments = new HashSet<>(); // Ảnh/file của lần check-in này

    @OneToMany(mappedBy = "checkInEvent", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<CheckInTask> completedTasks = new HashSet<>(); // Các task đã hoàn thành trong lần check-in này

    // === THÊM MỚI CÁC TRƯỜNG DƯỚI ĐÂY ===

    // 1. Trường lưu LINKS (từ Tệp 3/7)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "check_in_links", joinColumns = @JoinColumn(name = "check_in_event_id"))
    @Column(name = "link_url", length = 2048)
    @Builder.Default
    private List<String> links = new ArrayList<>();

    // 2. Trường liên kết tới COMMENTS (sửa lỗi crash từ Tệp 1/7)
    // "mappedBy" phải khớp với tên trường trong ProgressComment.java (mà chúng ta đã đặt là "checkInEvent")
    @OneToMany(mappedBy = "checkInEvent", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private Set<ProgressComment> comments = new HashSet<>();

    // 3. Trường liên kết tới REACTIONS (sửa lỗi crash từ Tệp 2/7)
    // "mappedBy" phải khớp với tên trường trong ProgressReaction.java (mà chúng ta đã đặt là "checkInEvent")
    @OneToMany(mappedBy = "checkInEvent", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<ProgressReaction> reactions = new HashSet<>();

    // === KẾT THÚC PHẦN THÊM MỚI ===


    // --- Phương thức tiện ích (helpers) ---
    public void addAttachment(CheckInAttachment attachment) {
        attachments.add(attachment);
        attachment.setCheckInEvent(this);
    }

    public void removeAttachment(CheckInAttachment attachment) { 
        attachments.remove(attachment);
        attachment.setCheckInEvent(null);
    }

    public void addTask(CheckInTask task) {
        completedTasks.add(task);
        task.setCheckInEvent(this);
    }

     public void removeTask(CheckInTask task) { 
        completedTasks.remove(task);
        task.setCheckInEvent(null);
    }
}