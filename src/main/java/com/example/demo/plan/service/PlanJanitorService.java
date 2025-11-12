package com.example.demo.plan.service;

import com.example.demo.notification.service.NotificationService;
import com.example.demo.plan.entity.MemberRole;
import com.example.demo.plan.entity.Plan;
import com.example.demo.plan.entity.PlanMember;
import com.example.demo.plan.entity.PlanStatus;
import com.example.demo.plan.repository.PlanMemberRepository;
import com.example.demo.plan.repository.PlanRepository;
import com.example.demo.progress.repository.CheckInEventRepository;
import com.example.demo.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanJanitorService {

    private static final int INACTIVITY_DAYS_LIMIT = 3; // Số ngày giới hạn không active

    private final PlanRepository planRepository;
    private final PlanMemberRepository planMemberRepository;
    private final CheckInEventRepository checkInEventRepository;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    // --- BỔ SUNG START: Job tự động kết thúc kế hoạch ---
    /**
     * Scheduled Job: Chạy vào 0:30 AM mỗi ngày.
     * Nhiệm vụ: Kiểm tra các kế hoạch ACTIVE đã quá hạn (ngày kết thúc < hôm nay) và chuyển sang COMPLETED.
     */
    @Scheduled(cron = "0 30 0 * * ?")
    @Transactional
    public void autoCompleteExpiredPlans() {
        log.info("Bắt đầu Job kiểm tra và kết thúc các kế hoạch đã quá hạn...");
        LocalDate today = LocalDate.now();

        // 1. Lấy tất cả các kế hoạch đang ACTIVE
        List<Plan> activePlans = planRepository.findByStatus(PlanStatus.ACTIVE);
        int completedCount = 0;

        for (Plan plan : activePlans) {
            // Tính ngày kết thúc: startDate + duration - 1
            LocalDate endDate = plan.getStartDate().plusDays(plan.getDurationInDays() - 1);

            // Nếu ngày kết thúc nhỏ hơn ngày hiện tại -> Đã quá hạn
            if (endDate.isBefore(today)) {
                plan.setStatus(PlanStatus.COMPLETED);
                // Plan được lưu tự động nhờ @Transactional khi kết thúc method,
                // hoặc có thể gọi explicit: planRepository.save(plan);
                completedCount++;
                log.info("-> Kế hoạch '{}' (ID: {}) đã hết hạn vào ngày {}. Chuyển trạng thái sang COMPLETED.",
                        plan.getTitle(), plan.getId(), endDate);

                // Gửi thông báo và WebSocket cập nhật cho các thành viên
                notifyPlanCompletion(plan);
            }
        }

        log.info("Kết thúc Job. Đã tự động hoàn thành {} kế hoạch.", completedCount);
    }

    private void notifyPlanCompletion(Plan plan) {
        // 1. Gửi Notification cho tất cả thành viên
        for (PlanMember member : plan.getMembers()) {
            // Tùy chọn: Có thể chỉ gửi cho Owner nếu muốn ít thông báo hơn,
            // nhưng thường thì nên gửi cho tất cả để họ biết hành trình đã kết thúc.
             String title = "Hành trình đã kết thúc!";
             String message = String.format("Chúc mừng! Kế hoạch '%s' đã chính thức hoàn thành. Hãy cùng nhìn lại chặng đường vừa qua nhé.", plan.getTitle());
             // Link dẫn đến trang chi tiết kế hoạch (có thể thêm param ?tab=summary nếu sau này làm tab tổng kết)
             String link = "/plan/" + plan.getShareableLink();
             
             // Giả định method createNotification có sẵn
             notificationService.createNotification(member.getUser(), message, link);
        }

        // 2. Gửi WebSocket để frontend cập nhật trạng thái ngay lập tức (nếu ai đó đang treo máy xem plan lúc 0:30 sáng)
        String destination = "/topic/plan/" + plan.getShareableLink() + "/details";
        Map<String, Object> payload = Map.of(
                "type", "PLAN_COMPLETED",
                "planId", plan.getId(),
                "status", "COMPLETED"
        );
        messagingTemplate.convertAndSend(destination, payload);
    }
    // --- BỔ SUNG END ---

    /**
     * Scheduled Job: Chạy vào 3:00 AM mỗi ngày.
     * Nhiệm vụ: Quét và xóa các thành viên "khán giả" (không check-in quá 3 ngày).
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void removeInactiveMembers() {
        log.info("Bắt đầu Job dọn dẹp thành viên không hoạt động...");
        LocalDateTime threshold = LocalDateTime.now().minusDays(INACTIVITY_DAYS_LIMIT);

        // 1. Chỉ quét các kế hoạch đang hoạt động (ACTIVE)
        // Lưu ý: Đảm bảo PlanRepository có phương thức findByStatus(PlanStatus status)
        List<Plan> activePlans = planRepository.findByStatus(PlanStatus.ACTIVE);

        int removedCount = 0;
        for (Plan plan : activePlans) {
            // Tạo bản sao danh sách thành viên để tránh lỗi ConcurrentModification khi vừa duyệt vừa xóa
            List<PlanMember> membersSnapshot = new ArrayList<>(plan.getMembers());

            for (PlanMember member : membersSnapshot) {
                // KHÔNG BAO GIỜ xóa chủ sở hữu (Owner)
                if (member.getRole() == MemberRole.OWNER) {
                    continue;
                }

                // Kiểm tra xem có check-in nào sau thời điểm threshold không
                boolean hasRecentCheckIn = checkInEventRepository
                        .existsByPlanMemberAndCheckInTimestampAfter(member, threshold);

                // Nếu không có check-in gần đây -> XÓA
                if (!hasRecentCheckIn) {
                    User userToRemove = member.getUser();
                    log.info("-> Xóa thành viên {} khỏi plan '{}' do không hoạt động.", userToRemove.getEmail(), plan.getTitle());

                    // 1. Gửi thông báo cho người bị xóa
                    notifyUserBeenRemoved(userToRemove, plan);

                    // 2. Gửi WebSocket cập nhật giao diện ngay lập tức cho những người còn lại
                    notifyPlanUpdate(plan.getShareableLink(), userToRemove.getId());

                    // 3. Thực hiện xóa khỏi DB
                    plan.getMembers().remove(member); // Cập nhật phía Plan để đồng bộ JPA
                    planMemberRepository.delete(member);
                    removedCount++;
                }
            }
        }
        log.info("Kết thúc Job dọn dẹp. Đã xóa tổng cộng {} thành viên không hoạt động.", removedCount);
    }

    private void notifyUserBeenRemoved(User user, Plan plan) {
        String title = "Thông báo rời kế hoạch";
        String message = String.format("Bạn đã bị xóa khỏi kế hoạch '%s' vì không check-in trong %d ngày liên tiếp. Hãy tham gia lại khi bạn sẵn sàng nhé!",
                plan.getTitle(), INACTIVITY_DAYS_LIMIT);
        
        // Giả định NotificationService có phương thức này. Bạn cần điều chỉnh nếu tên hàm khác.
        notificationService.createNotification(user, message, "/");
        log.info("[NOTIFICATION] To {}: {}", user.getEmail(), message);
    }

    private void notifyPlanUpdate(String shareableLink, Integer removedUserId) {
        String destination = "/topic/plan/" + shareableLink + "/details";
        Map<String, Object> payload = Map.of(
                "type", "MEMBER_REMOVED",
                "userId", removedUserId
        );
        messagingTemplate.convertAndSend(destination, payload);
    }
}