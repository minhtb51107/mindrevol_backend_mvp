package com.example.demo.progress.service.impl;

import com.example.demo.feed.entity.FeedEventType;
import com.example.demo.feed.service.FeedService;
import com.example.demo.plan.entity.Plan;
import com.example.demo.plan.entity.PlanMember;
import com.example.demo.plan.entity.PlanStatus;
import com.example.demo.plan.entity.Task;
import com.example.demo.plan.repository.PlanMemberRepository;
import com.example.demo.plan.repository.PlanRepository;
import com.example.demo.progress.dto.request.LogProgressRequest;
import com.example.demo.progress.dto.response.DailyProgressResponse;
import com.example.demo.progress.dto.response.DailyProgressSummaryResponse;
import com.example.demo.progress.dto.response.ProgressDashboardResponse;
import com.example.demo.user.dto.response.UserStatsResponse;
import com.example.demo.progress.entity.DailyProgress;
import com.example.demo.progress.mapper.ProgressMapper;
import com.example.demo.progress.repository.DailyProgressRepository;
import com.example.demo.progress.service.ProgressService;
import com.example.demo.shared.exception.BadRequestException;
import com.example.demo.shared.exception.ResourceNotFoundException;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.plan.entity.TaskComment;
import com.example.demo.plan.entity.TaskAttachment;
import com.example.demo.plan.repository.TaskCommentRepository;
import com.example.demo.plan.repository.TaskAttachmentRepository;
import com.example.demo.plan.repository.TaskRepository;
import com.example.demo.progress.dto.request.TaskCheckinUpdateRequest;
import com.example.demo.progress.dto.response.ProgressChartDataResponse; // *** THÊM IMPORT ***

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function; // *** THÊM IMPORT ***
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProgressServiceImpl implements ProgressService {

    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final PlanMemberRepository planMemberRepository;
    private final DailyProgressRepository dailyProgressRepository;
    private final ProgressMapper progressMapper;
    private final TaskRepository taskRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final FeedService feedService;


    @Value("${file.upload-dir}")
    private String uploadDir;

    @Override
    public DailyProgressResponse logOrUpdateDailyProgress(String shareableLink, String userEmail, LogProgressRequest request) {
        Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);

        PlanMember member = planMemberRepository.findByPlanIdAndUserId(plan.getId(), user.getId())
                .orElseThrow(() -> new AccessDeniedException("Bạn không phải là thành viên của kế hoạch này."));

        validateCheckinDate(request.getDate(), plan);

        int streakBeforeUpdate = calculateStreakForUser(user);

        DailyProgress progress = dailyProgressRepository.findByPlanMemberIdAndDate(member.getId(), request.getDate())
                .orElse(new DailyProgress());

        if (progress.getId() != null && progress.getDate().isBefore(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(2))) { // Use ZoneId
             throw new BadRequestException("Không thể sửa đổi tiến độ cho ngày đã quá cũ.");
        }
        progress.setPlanMember(member);
        progress.setDate(request.getDate());
        progress.setNotes(request.getNotes());
        List<String> validEvidenceLinks = request.getEvidence() == null ? new ArrayList<>() :
                request.getEvidence().stream()
                        .filter(link -> link != null && !link.isBlank())
                        .collect(Collectors.toList());
        progress.setEvidence(validEvidenceLinks);
        Set<Long> planTaskIds = plan.getDailyTasks().stream().map(Task::getId).collect(Collectors.toSet());
        int totalTasks = planTaskIds.size();
        Set<Long> validCompletedTaskIds = new HashSet<>();
        if (request.getCompletedTaskIds() != null) {
            validCompletedTaskIds = request.getCompletedTaskIds().stream()
                .filter(taskId -> taskId != null && planTaskIds.contains(taskId))
                .collect(Collectors.toSet());
        }
        progress.setCompletedTaskIds(validCompletedTaskIds);
        if (totalTasks > 0) {
            progress.setCompleted(validCompletedTaskIds.size() == totalTasks);
        } else {
            progress.setCompleted(request.getCompleted() != null && request.getCompleted());
        }
        if (totalTasks > 0 && validCompletedTaskIds.size() == totalTasks) {
             progress.setCompleted(true);
        }

        DailyProgress savedProgress = dailyProgressRepository.save(progress);
        log.info("Saved/Updated DailyProgress ID: {} for user: {} on date: {}", savedProgress.getId(), userEmail, request.getDate());

        // Xử lý taskUpdates
        if (request.getTaskUpdates() != null && !request.getTaskUpdates().isEmpty()) {
            Path rootLocation = Paths.get(uploadDir);
            for (TaskCheckinUpdateRequest taskUpdate : request.getTaskUpdates()) {
                Long taskId = taskUpdate.getTaskId();
                Task task = plan.getDailyTasks().stream()
                                  .filter(t -> t.getId().equals(taskId))
                                  .findFirst()
                                  .orElse(null);

                if (task == null) continue;

                String commentContent = taskUpdate.getCommentContent();
                if (commentContent != null && !commentContent.isBlank()) {
                    TaskComment taskComment = TaskComment.builder()
                            .task(task).author(user).content(commentContent.trim()).build();
                    taskCommentRepository.save(taskComment);
                }

                if (taskUpdate.getAttachments() != null && !taskUpdate.getAttachments().isEmpty()) {
                    taskUpdate.getAttachments().forEach(attReq -> {
                        TaskAttachment taskAttachment = TaskAttachment.builder()
                                .task(task)
                                .originalFilename(attReq.getOriginalFilename())
                                .storedFilename(attReq.getStoredFilename())
                                .contentType(attReq.getContentType())
                                .fileSize(attReq.getFileSize())
                                .fileUrl(attReq.getFileUrl())
                                .filePath(rootLocation.resolve(attReq.getStoredFilename()).normalize().toAbsolutePath().toString())
                                .build();
                        taskAttachmentRepository.save(taskAttachment);
                    });
                }
            }
        }

        // Gửi Feed Event CHECK_IN
        Map<String, Object> details = new HashMap<>();
        details.put("date", request.getDate().toString());
        feedService.createAndPublishFeedEvent(FeedEventType.CHECK_IN, user, plan, details);

        // Kiểm tra và gửi Feed Event STREAK_ACHIEVED
        int streakAfterUpdate = calculateStreakForUser(user);
        if (streakAfterUpdate > streakBeforeUpdate && streakAfterUpdate > 0 &&
            (streakAfterUpdate == 1 || streakAfterUpdate % 3 == 0 || streakAfterUpdate % 5 == 0 || streakAfterUpdate % 7 == 0 || streakAfterUpdate % 10 == 0 )) {
            Map<String, Object> streakDetails = Map.of("streakDays", streakAfterUpdate);
            feedService.createAndPublishFeedEvent(FeedEventType.STREAK_ACHIEVED, user, plan, streakDetails);
        }

        // Gửi WebSocket Progress
        String destination = "/topic/plan/" + shareableLink + "/progress";
        DailyProgressSummaryResponse progressSummary = progressMapper.toDailyProgressSummaryResponse(savedProgress, user.getId());
        Map<String, Object> payload = Map.of(
            "type", "PROGRESS_UPDATE",
            "date", savedProgress.getDate().toString(),
            "memberEmail", userEmail,
            "memberFullName", getUserFullName(user),
            "progressSummary", progressSummary
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.info("Sent WebSocket update to {} for progress ID {}", destination, savedProgress.getId());

        return progressMapper.toDailyProgressResponse(savedProgress, user.getId());
    }


    private void validateCheckinDate(LocalDate checkinDate, Plan plan) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDate planStartDate = plan.getStartDate();
        LocalDate planEndDate = planStartDate.plusDays(plan.getDurationInDays() - 1);

        if (checkinDate.isAfter(today)) {
            throw new BadRequestException("Không thể ghi nhận tiến độ cho một ngày trong tương lai.");
        }
        if (checkinDate.isBefore(today.minusDays(2))) {
             throw new BadRequestException("Bạn chỉ có thể ghi nhận tiến độ cho hôm nay, hôm qua hoặc hôm kia.");
        }
        if (checkinDate.isBefore(planStartDate) || checkinDate.isAfter(planEndDate)) {
            throw new BadRequestException("Ngày check-in không nằm trong thời gian diễn ra kế hoạch.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ProgressDashboardResponse getProgressDashboard(String shareableLink, String userEmail) {
        Plan plan = findPlanByShareableLinkWithTasks(shareableLink);
        User user = findUserByEmail(userEmail);

        if (plan.getMembers() == null || plan.getMembers().stream().noneMatch(m -> m.getUser() != null && m.getUser().getId().equals(user.getId()))) {
            throw new AccessDeniedException("Bạn không có quyền xem tiến độ của kế hoạch này.");
        }

        // Lấy tất cả progress của plan này (có thể tối ưu chỉ lấy trong khoảng ngày)
        List<DailyProgress> allProgressForPlan = dailyProgressRepository.findAll().stream()
                .filter(dp -> dp.getPlanMember() != null && dp.getPlanMember().getPlan() != null && dp.getPlanMember().getPlan().getId().equals(plan.getId()))
                .toList();

        // Gom nhóm progress theo ID thành viên
        Map<Integer, List<DailyProgress>> progressByMemberId = allProgressForPlan.stream()
                .collect(Collectors.groupingBy(dp -> dp.getPlanMember().getId()));

        // Xây dựng response cho từng thành viên
        List<ProgressDashboardResponse.MemberProgressResponse> membersProgress = plan.getMembers().stream()
                .map(member -> buildMemberProgress(member, plan, user.getId(), progressByMemberId.getOrDefault(member.getId(), Collections.emptyList())))
                .filter(Objects::nonNull) // Lọc bỏ member null (nếu có)
                .sorted(Comparator.comparing(ProgressDashboardResponse.MemberProgressResponse::getUserFullName)) // Sắp xếp theo tên
                .collect(Collectors.toList());

        return ProgressDashboardResponse.builder()
                .planTitle(plan.getTitle())
                .membersProgress(membersProgress)
                .build();
    }

    // *** ĐÃ SỬA PHƯƠNG THỨC NÀY ***
    private ProgressDashboardResponse.MemberProgressResponse buildMemberProgress(PlanMember member, Plan plan, Integer currentUserId, List<DailyProgress> memberProgressList) {
        if (member == null || member.getUser() == null) return null;

        long completedDays = memberProgressList.stream().filter(DailyProgress::isCompleted).count();
        double completionPercentage = (plan.getDurationInDays() > 0) ? ((double) completedDays / plan.getDurationInDays() * 100) : 0;

        LocalDate planStartDate = plan.getStartDate();
        if (planStartDate == null) {
             log.warn("Plan ID {} has null start date, cannot build daily status map.", plan.getId());
             return null; // Hoặc trả về response với dailyStatus rỗng
        }


        Map<LocalDate, DailyProgress> progressByDate = memberProgressList.stream()
                .collect(Collectors.toMap(DailyProgress::getDate, p -> p));

        // Tạo map dailyStatus bằng cách gọi mapper (đã được sửa để không trả về null)
        Map<String, DailyProgressSummaryResponse> dailyStatus = IntStream.range(0, plan.getDurationInDays())
            .mapToObj(planStartDate::plusDays)
            .collect(Collectors.toMap(
                LocalDate::toString, // Key là ngày dạng String
                date -> progressMapper.toDailyProgressSummaryResponse(progressByDate.get(date), currentUserId), // Value là DTO (không null)
                (v1, v2) -> v1,       // Merge function (không cần thiết vì key là duy nhất)
                LinkedHashMap::new    // Giữ đúng thứ tự ngày
            ));

        return ProgressDashboardResponse.MemberProgressResponse.builder()
                .userEmail(member.getUser().getEmail())
                .userFullName(getUserFullName(member.getUser()))
                .completedDays((int) completedDays)
                .completionPercentage(completionPercentage)
                .dailyStatus(dailyStatus)
                .build();
    }


    // getUserStats giữ nguyên như lần cập nhật trước
    @Override
    @Transactional(readOnly = true)
    public UserStatsResponse getUserStats(String userEmail) {
        User user = findUserByEmail(userEmail);
        Integer userId = user.getId();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));

        List<PlanMember> activeMembershipsToday = planMemberRepository.findByUserIdWithPlan(userId).stream()
                .filter(pm -> {
                    Plan plan = pm.getPlan();
                    if (plan == null || plan.getStatus() != PlanStatus.ACTIVE) {
                        return false;
                    }
                    LocalDate planStartDate = plan.getStartDate();
                    // Thêm kiểm tra null cho startDate
                    if (planStartDate == null) return false;
                    LocalDate planEndDate = planStartDate.plusDays(plan.getDurationInDays() - 1);
                    return !today.isBefore(planStartDate) && !today.isAfter(planEndDate);
                })
                .peek(pm -> pm.getPlan().getDailyTasks().size())
                .collect(Collectors.toList());

        int totalTasksToday = 0;
        int completedTasksToday = 0;
        boolean allPlansCheckedInAndCompleted = !activeMembershipsToday.isEmpty();

        if (!activeMembershipsToday.isEmpty()) {
            Set<Integer> activeMemberIdsToday = activeMembershipsToday.stream()
                                                    .map(PlanMember::getId)
                                                    .collect(Collectors.toSet());

            Map<Integer, DailyProgress> progressTodayMap = dailyProgressRepository.findAll().stream()
                    .filter(dp -> dp.getPlanMember() != null &&
                                   activeMemberIdsToday.contains(dp.getPlanMember().getId()) &&
                                   dp.getDate().equals(today))
                    .collect(Collectors.toMap(dp -> dp.getPlanMember().getId(), dp -> dp));

            for (PlanMember member : activeMembershipsToday) {
                Plan plan = member.getPlan();
                // Kiểm tra null cho plan và dailyTasks
                int tasksInThisPlan = (plan != null && plan.getDailyTasks() != null) ? plan.getDailyTasks().size() : 0;
                totalTasksToday += tasksInThisPlan;

                DailyProgress progress = progressTodayMap.get(member.getId());

                if (progress == null) {
                    allPlansCheckedInAndCompleted = false;
                } else {
                    // Kiểm tra null cho completedTaskIds
                    completedTasksToday += (progress.getCompletedTaskIds() != null ? progress.getCompletedTaskIds().size() : 0);
                    boolean isThisPlanCompletedToday = progress.isCompleted() ||
                                                       (tasksInThisPlan > 0 && progress.getCompletedTaskIds() != null && progress.getCompletedTaskIds().size() == tasksInThisPlan);
                    if (!isThisPlanCompletedToday) {
                        allPlansCheckedInAndCompleted = false;
                    }
                }
            }
        } else {
             allPlansCheckedInAndCompleted = false;
        }


        int currentStreak = calculateStreakForUser(user);

        return UserStatsResponse.builder()
                .checkedInTodayComplete(allPlansCheckedInAndCompleted)
                .currentStreak(currentStreak)
                .totalTasksToday(totalTasksToday)
                .completedTasksToday(completedTasksToday)
                .build();
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<ProgressChartDataResponse> getProgressChartData(String userEmail) {
        User user = findUserByEmail(userEmail);
        Integer userId = user.getId();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDate startDate = today.minusDays(6); // 7 ngày tính cả hôm nay

        List<ProgressChartDataResponse> chartData = new ArrayList<>();

        // Lấy tất cả PlanMember của user (có thể tối ưu chỉ lấy plan active trong khoảng 7 ngày)
        List<PlanMember> allMemberships = planMemberRepository.findByUserIdWithPlan(userId);
        if (allMemberships.isEmpty()) {
            // Nếu không tham gia plan nào, trả về 7 ngày với tỷ lệ 0%
            for (int i = 0; i < 7; i++) {
                chartData.add(new ProgressChartDataResponse(startDate.plusDays(i), 0.0));
            }
            return chartData;
        }

        Set<Integer> allMemberIds = allMemberships.stream().map(PlanMember::getId).collect(Collectors.toSet());

        // Lấy tất cả DailyProgress của user trong 7 ngày gần nhất
        List<DailyProgress> recentProgress = dailyProgressRepository.findAll().stream()
                .filter(dp -> dp.getPlanMember() != null &&
                               allMemberIds.contains(dp.getPlanMember().getId()) &&
                               !dp.getDate().isBefore(startDate) && !dp.getDate().isAfter(today))
                .toList();

        // Gom nhóm progress theo ngày
        Map<LocalDate, List<DailyProgress>> progressByDate = recentProgress.stream()
                .collect(Collectors.groupingBy(DailyProgress::getDate));

        // Tính toán cho từng ngày trong 7 ngày
        for (int i = 0; i < 7; i++) {
            LocalDate currentDate = startDate.plusDays(i);
            int totalTasksForDay = 0;
            int completedTasksForDay = 0;

            // Tìm các plan active của user vào ngày currentDate
            List<PlanMember> activeMembershipsForDate = allMemberships.stream()
                    .filter(pm -> {
                        Plan plan = pm.getPlan();
                        if (plan == null || plan.getStatus() != PlanStatus.ACTIVE) return false;
                        LocalDate planStartDate = plan.getStartDate();
                        if (planStartDate == null) return false;
                        LocalDate planEndDate = planStartDate.plusDays(plan.getDurationInDays() - 1);
                        return !currentDate.isBefore(planStartDate) && !currentDate.isAfter(planEndDate);
                    })
                    .peek(pm -> pm.getPlan().getDailyTasks().size()) // Trigger lazy load tasks
                    .toList();

            if (!activeMembershipsForDate.isEmpty()) {
                 // Lấy progress của ngày currentDate
                List<DailyProgress> progressesOnDate = progressByDate.getOrDefault(currentDate, Collections.emptyList());
                Map<Integer, DailyProgress> progressByMemberIdOnDate = progressesOnDate.stream()
                        .collect(Collectors.toMap(dp -> dp.getPlanMember().getId(), Function.identity()));

                for (PlanMember member : activeMembershipsForDate) {
                    Plan plan = member.getPlan();
                     // Kiểm tra null cho plan và dailyTasks
                    int tasksInThisPlan = (plan != null && plan.getDailyTasks() != null) ? plan.getDailyTasks().size() : 0;
                    totalTasksForDay += tasksInThisPlan;

                    DailyProgress progress = progressByMemberIdOnDate.get(member.getId());
                    if (progress != null && progress.getCompletedTaskIds() != null) {
                        completedTasksForDay += progress.getCompletedTaskIds().size();
                    } else if (progress != null && tasksInThisPlan == 0 && progress.isCompleted()) {
                        // Nếu plan không có task và đã check-in completed=true, coi như hoàn thành 1 "task ảo"
                        totalTasksForDay += 1; // Thêm 1 task ảo
                        completedTasksForDay += 1; // Hoàn thành task ảo đó
                    }
                }
            }

            // Tính tỷ lệ hoàn thành
            double completionRate = (totalTasksForDay > 0) ? ((double) completedTasksForDay / totalTasksForDay * 100.0) : 0.0;
            chartData.add(new ProgressChartDataResponse(currentDate, completionRate));
        }

        return chartData;
    }

    // calculateStreakForUser giữ nguyên
     private int calculateStreakForUser(User user) {
        Integer userId = user.getId();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDate yesterday = today.minusDays(1);

        List<Integer> memberIds = planMemberRepository.findByUserIdWithPlan(userId).stream()
                                                    .map(PlanMember::getId)
                                                    .collect(Collectors.toList());
        if (memberIds.isEmpty()) return 0;

        List<LocalDate> checkinDates = dailyProgressRepository.findAll().stream() // Cân nhắc tối ưu query này
                .filter(dp -> dp.getPlanMember() != null && memberIds.contains(dp.getPlanMember().getId()))
                .map(DailyProgress::getDate)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());

        if (checkinDates.isEmpty()) return 0;

        int currentStreak = 0;
        LocalDate expectedDate;

        if (checkinDates.get(0).equals(today)) {
            expectedDate = today;
        } else if (checkinDates.get(0).equals(yesterday)) {
            expectedDate = yesterday;
        } else {
            return 0;
        }

        for (LocalDate checkinDate : checkinDates) {
            if (checkinDate.equals(expectedDate)) {
                currentStreak++;
                expectedDate = expectedDate.minusDays(1);
            } else if (checkinDate.isBefore(expectedDate)) {
                break;
            }
        }
        return currentStreak;
     }

    // Các helper find... và getUserFullName giữ nguyên
    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + email));
    }
    private Plan findPlanByShareableLink(String link) {
        return planRepository.findByShareableLink(link)
                .map(plan -> { plan.getMembers().size(); return plan; })
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kế hoạch với link: " + link));
    }
     private Plan findPlanByShareableLinkWithTasks(String link) {
        return planRepository.findByShareableLink(link)
                .map(plan -> {
                    plan.getMembers().size();
                    plan.getDailyTasks().size();
                    // Sắp xếp task ở đây nếu cần thiết và nếu @OrderBy không hoạt động như mong đợi
                    // plan.getDailyTasks().sort(Comparator.comparing(Task::getOrder, Comparator.nullsLast(Integer::compareTo)));
                    return plan;
                })
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kế hoạch với link: " + link));
    }
    private String getUserFullName(User user) {
        if (user == null) return "N/A";
        // Ưu tiên Customer trước vì đây là user cuối
        if (user.getCustomer() != null && user.getCustomer().getFullname() != null && !user.getCustomer().getFullname().isBlank()) {
            return user.getCustomer().getFullname();
        }
        // Sau đó Employee
        if (user.getEmployee() != null && user.getEmployee().getFullname() != null && !user.getEmployee().getFullname().isBlank()) {
            return user.getEmployee().getFullname();
        }
        return user.getEmail(); // Fallback về email
    }
}