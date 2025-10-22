package com.example.demo.plan.entity;

import com.example.demo.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
    private int durationInDays;

    @Column(columnDefinition = "TEXT")
    private String dailyGoal;

    @Column(nullable = false, unique = true)
    private String shareableLink;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default // Giữ lại Builder.Default
    private List<PlanMember> members = new ArrayList<>(); // Khởi tạo members

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanStatus status = PlanStatus.ACTIVE;

    // --- THAY ĐỔI Ở ĐÂY ---
    // Bỏ @ElementCollection và @CollectionTable
    // Thay List<String> bằng List<Task>
    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("order ASC") // Sắp xếp task theo thứ tự 'order'
    @Builder.Default
    private List<Task> dailyTasks = new ArrayList<>(); // Đổi tên biến nếu muốn, ví dụ tasks
    // --- KẾT THÚC THAY ĐỔI ---


    @PrePersist
    protected void onCreate() {
        if (this.shareableLink == null) {
            this.shareableLink = UUID.randomUUID().toString().substring(0, 8);
        }
    }

    // --- THÊM PHƯƠNG THỨC TIỆN ÍCH (OPTIONAL) ---
    public void addTask(Task task) {
        dailyTasks.add(task);
        task.setPlan(this); // Thiết lập quan hệ 2 chiều
    }

    public void removeTask(Task task) {
        dailyTasks.remove(task);
        task.setPlan(null);
    }
    // --- KẾT THÚC THÊM ---
}