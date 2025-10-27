package com.example.demo.progress.entity.checkin;

import com.example.demo.plan.entity.PlanMember;
import jakarta.persistence.*;
import lombok.*;
//import org.hibernate.annotations.CreationTimestamp; // Không cần nữa vì timestamp set thủ công

import java.time.LocalDateTime;
// *** THAY ĐỔI IMPORT ***
// import java.util.ArrayList;
// import java.util.List;
import java.util.HashSet; // Import Set
import java.util.Set;     // Import Set
// *** KẾT THÚC THAY ĐỔI IMPORT ***

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

    // *** THAY ĐỔI List -> Set ***
    @OneToMany(mappedBy = "checkInEvent", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<CheckInAttachment> attachments = new HashSet<>(); // Ảnh/file của lần check-in này

    @OneToMany(mappedBy = "checkInEvent", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<CheckInTask> completedTasks = new HashSet<>(); // Các task đã hoàn thành trong lần check-in này
    // *** KẾT THÚC THAY ĐỔI List -> Set ***

    // --- Phương thức tiện ích (cập nhật kiểu tham số nếu cần, nhưng HashSet.add vẫn hoạt động) ---
    public void addAttachment(CheckInAttachment attachment) {
        attachments.add(attachment);
        attachment.setCheckInEvent(this);
    }

    public void removeAttachment(CheckInAttachment attachment) { // Thêm hàm remove nếu cần
        attachments.remove(attachment);
        attachment.setCheckInEvent(null);
    }

    public void addTask(CheckInTask task) {
        completedTasks.add(task);
        task.setCheckInEvent(this);
    }

     public void removeTask(CheckInTask task) { // Thêm hàm remove nếu cần
        completedTasks.remove(task);
        task.setCheckInEvent(null);
    }
}