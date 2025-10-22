package com.example.demo.notification.service.impl;

import com.example.demo.notification.service.NotificationService;
import com.example.demo.notification.service.ScheduledNotificationService;
import com.example.demo.plan.entity.Plan;
import com.example.demo.plan.entity.PlanMember;
import com.example.demo.plan.entity.PlanStatus;
import com.example.demo.plan.entity.Task;
import com.example.demo.plan.repository.PlanMemberRepository;
import com.example.demo.plan.repository.PlanRepository;
import com.example.demo.progress.entity.DailyProgress;
import com.example.demo.progress.repository.DailyProgressRepository;
import com.example.demo.user.entity.User;
// Không cần import UserMapper nữa
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections; // Thêm import Collections
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledNotificationServiceImpl implements ScheduledNotificationService {

    private final PlanRepository planRepository;
    private final PlanMemberRepository planMemberRepository;
    private final DailyProgressRepository dailyProgressRepository;
    private final NotificationService notificationService;
    // Bỏ UserMapper

    private static final double COMPLETION_THRESHOLD_RATIO = 0.7;
    private static final int MIN_DAYS_PASSED_FOR_ENCOURAGEMENT = 3;


    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional(readOnly = true)
    @Override
    public void sendCheckinReminders() {
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(1);
        log.info("[Checkin Reminder] Running for date: {}", yesterday);

        List<Plan> activePlans = planRepository.findAll().stream()
            .filter(plan -> plan.getStatus() == PlanStatus.ACTIVE &&
                           !plan.getStartDate().isAfter(yesterday))
            .toList();

        if (activePlans.isEmpty()) {
            log.info("[Checkin Reminder] No active plans found.");
            return;
        }

        for (Plan plan : activePlans) {
            LocalDate planEndDate = plan.getStartDate().plusDays(plan.getDurationInDays() - 1);
            if (yesterday.isAfter(planEndDate)) continue;

            List<PlanMember> members = plan.getMembers();
            if (members == null || members.isEmpty()) continue;

            List<DailyProgress> progressesYesterday = dailyProgressRepository.findAll().stream()
                 .filter(dp -> dp.getPlanMember() != null &&
                               dp.getPlanMember().getPlan() != null &&
                               dp.getPlanMember().getPlan().getId().equals(plan.getId()) &&
                               dp.getDate().equals(yesterday))
                 .toList();
            Set<Integer> membersCheckedInYesterday = progressesYesterday.stream()
                .map(dp -> dp.getPlanMember().getId())
                .collect(Collectors.toSet());

            for (PlanMember member : members) {
                if (member.getUser() == null) continue;
                if (!membersCheckedInYesterday.contains(member.getId())) {
                    User recipient = member.getUser();
                    String message = "🔔 Đừng quên ghi nhận tiến độ học tập ngày " + yesterday + " cho kế hoạch '" + plan.getTitle() + "' nhé!";
                    String link = "/plan/" + plan.getShareableLink();
                    log.info("[Checkin Reminder] Sending notification to user ID: {}", recipient.getId());
                    notificationService.createNotification(recipient, message, link);
                }
            }
        }
        log.info("[Checkin Reminder] Task finished.");
    }

    @Scheduled(cron = "0 0/30 * * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional(readOnly = true)
    public void sendDeadlineReminders() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalTime reminderThreshold = now.plusHours(1);
        log.info("[Deadline Reminder] Running for date: {}, time: {}, threshold: {}", today, now, reminderThreshold);

        List<Plan> activePlansToday = planRepository.findAll().stream()
            .filter(plan -> {
                LocalDate planEndDate = plan.getStartDate().plusDays(plan.getDurationInDays() - 1);
                return plan.getStatus() == PlanStatus.ACTIVE &&
                       !plan.getStartDate().isAfter(today) &&
                       !planEndDate.isBefore(today);
            })
            .peek(plan -> plan.getDailyTasks().size()) // Trigger lazy loading tasks
            .toList();

         if (activePlansToday.isEmpty()) {
            log.info("[Deadline Reminder] No active plans today.");
            return;
        }

         for (Plan plan : activePlansToday) {
            List<PlanMember> members = plan.getMembers();
            if (members == null || members.isEmpty()) continue;

            List<Task> tasksWithUpcomingDeadline = plan.getDailyTasks().stream()
                .filter(task -> task.getDeadlineTime() != null &&
                                task.getDeadlineTime().isAfter(now) &&
                                task.getDeadlineTime().isBefore(reminderThreshold))
                .toList();

            if (tasksWithUpcomingDeadline.isEmpty()) continue;

             Map<Integer, DailyProgress> progressTodayByMemberId = dailyProgressRepository.findAll().stream()
                 .filter(dp -> dp.getPlanMember() != null &&
                               dp.getPlanMember().getPlan() != null &&
                               dp.getPlanMember().getPlan().getId().equals(plan.getId()) &&
                               dp.getDate().equals(today))
                 .collect(Collectors.toMap(dp -> dp.getPlanMember().getId(), dp -> dp));

            for (PlanMember member : members) {
                User recipient = member.getUser();
                if (recipient == null) continue;
                DailyProgress memberProgressToday = progressTodayByMemberId.get(member.getId());
                for (Task task : tasksWithUpcomingDeadline) {
                    boolean taskCompleted = memberProgressToday != null &&
                                            memberProgressToday.getCompletedTaskIds() != null &&
                                            memberProgressToday.getCompletedTaskIds().contains(task.getId());
                    if (!taskCompleted) {
                        String taskDesc = task.getDescription().length() > 30 ? task.getDescription().substring(0, 27) + "..." : task.getDescription();
                        String message = "⏰ Nhắc nhở: Công việc '" + taskDesc + "' (" + plan.getTitle() + ") sắp đến hạn lúc " + task.getDeadlineTime() + " hôm nay!";
                        String link = "/plan/" + plan.getShareableLink();
                        log.info("[Deadline Reminder] Sending notification for task ID: {} to user ID: {}", task.getId(), recipient.getId());
                        notificationService.createNotification(recipient, message, link);
                    }
                }
            }
        }
        log.info("[Deadline Reminder] Task finished.");
    }

    @Scheduled(cron = "0 0 20 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional(readOnly = true)
    @Override
    public void sendEncouragementNotifications() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        log.info("[Encouragement] Running check for date: {}", today);

        List<Plan> activePlans = planRepository.findAll().stream()
            .filter(plan -> {
                LocalDate planEndDate = plan.getStartDate().plusDays(plan.getDurationInDays() - 1);
                long daysPassed = ChronoUnit.DAYS.between(plan.getStartDate(), today) + 1;
                return plan.getStatus() == PlanStatus.ACTIVE &&
                       !plan.getStartDate().isAfter(today) &&
                       !planEndDate.isBefore(today) &&
                       daysPassed >= MIN_DAYS_PASSED_FOR_ENCOURAGEMENT;
            })
            .toList();

        if (activePlans.isEmpty()) {
            log.info("[Encouragement] No plans eligible for encouragement check.");
            return;
        }

        for (Plan plan : activePlans) {
            List<PlanMember> members = plan.getMembers();
            if (members == null || members.size() < 2) continue;

            LocalDate startDate = plan.getStartDate();
            long totalDaysSoFar = ChronoUnit.DAYS.between(startDate, today) + 1;
            if (totalDaysSoFar <= 0) continue;

            double expectedCompletionRate = Math.min(1.0, (double) totalDaysSoFar / plan.getDurationInDays());

             List<DailyProgress> allPlanProgress = dailyProgressRepository.findAll().stream()
                 .filter(dp -> dp.getPlanMember() != null &&
                               dp.getPlanMember().getPlan() != null &&
                               dp.getPlanMember().getPlan().getId().equals(plan.getId()) &&
                               !dp.getDate().isAfter(today))
                 .toList();

            Map<Integer, List<DailyProgress>> progressByMemberId = allPlanProgress.stream()
                .collect(Collectors.groupingBy(dp -> dp.getPlanMember().getId()));

            List<PlanMember> membersFallingBehind = members.stream().filter(member -> {
                if (member.getUser() == null) return false;
                List<DailyProgress> memberProgress = progressByMemberId.getOrDefault(member.getId(), Collections.emptyList());
                long completedDays = memberProgress.stream().filter(DailyProgress::isCompleted).count();
                double actualCompletionRate = (totalDaysSoFar > 0) ? (double) completedDays / totalDaysSoFar : 0.0; // Tránh chia cho 0
                return actualCompletionRate < (expectedCompletionRate * COMPLETION_THRESHOLD_RATIO);
            }).toList();

            if (!membersFallingBehind.isEmpty()) {
                log.info("[Encouragement] Found {} members falling behind in plan '{}'", membersFallingBehind.size(), plan.getTitle());
                List<PlanMember> teammates = members.stream()
                                                .filter(m -> m.getUser() != null)
                                                .toList();

                for (PlanMember behindMember : membersFallingBehind) {
                    // *** SỬA LỖI Ở ĐÂY: Sử dụng phương thức helper ***
                    String behindMemberName = getUserFullName(behindMember.getUser());
                    // *** KẾT THÚC SỬA LỖI ***

                    String messageToTeammates = "🤝 Có vẻ " + behindMemberName + " đang gặp chút khó khăn với kế hoạch '" + plan.getTitle() + "'. Hãy hỏi thăm và động viên bạn ấy nhé!";
                    String link = "/plan/" + plan.getShareableLink();

                    for (PlanMember teammate : teammates) {
                        if (!teammate.getId().equals(behindMember.getId())) {
                             log.info("[Encouragement] Sending notification about {} to teammate {}", behindMemberName, teammate.getUser().getEmail());
                             notificationService.createNotification(teammate.getUser(), messageToTeammates, link);
                        }
                    }
                }
            }
        }
        log.info("[Encouragement] Task finished.");
    }

    // --- THÊM PHƯƠNG THỨC HELPER ĐỂ LẤY TÊN ĐẦY ĐỦ ---
    // Sao chép logic từ PlanMapper hoặc ProgressMapper
    private String getUserFullName(User user) {
        if (user == null) return "N/A";
        // Ưu tiên lấy từ Employee trước nếu là môi trường nội bộ
        if (user.getEmployee() != null && user.getEmployee().getFullname() != null && !user.getEmployee().getFullname().isBlank()) {
            return user.getEmployee().getFullname();
        }
        // Sau đó mới lấy từ Customer
        if (user.getCustomer() != null && user.getCustomer().getFullname() != null && !user.getCustomer().getFullname().isBlank()) {
            return user.getCustomer().getFullname();
        }
        // Cuối cùng trả về email nếu không có tên
        return user.getEmail();
    }
    // --- KẾT THÚC THÊM HELPER ---

}