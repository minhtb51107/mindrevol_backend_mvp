package com.example.demo.progress.service.impl;

import com.example.demo.feed.service.FeedService; // Vẫn giữ để gửi feed
import com.example.demo.plan.entity.Plan;
import com.example.demo.plan.entity.PlanMember;
import com.example.demo.plan.entity.Task;
import com.example.demo.plan.repository.PlanMemberRepository;
import com.example.demo.plan.repository.PlanRepository;
import com.example.demo.plan.repository.TaskRepository;
import com.example.demo.progress.dto.request.CheckInRequest;
import com.example.demo.progress.dto.response.TimelineResponse;
import com.example.demo.progress.dto.response.ProgressChartDataResponse;
import com.example.demo.progress.entity.checkin.CheckInAttachment;
import com.example.demo.progress.entity.checkin.CheckInEvent;
import com.example.demo.progress.entity.checkin.CheckInTask;
import com.example.demo.progress.mapper.ProgressMapper;
import com.example.demo.progress.repository.CheckInEventRepository;
import com.example.demo.progress.service.ProgressService;
import com.example.demo.shared.exception.BadRequestException;
import com.example.demo.shared.exception.ResourceNotFoundException;
import com.example.demo.user.dto.response.UserStatsResponse;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProgressServiceImpl implements ProgressService {

    // --- REPOSITORIES CŨ (DailyProgressRepository) ĐÃ BỊ XÓA ---
    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final PlanMemberRepository planMemberRepository;
    private final TaskRepository taskRepository; // Thêm
    private final ProgressMapper progressMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final FeedService feedService;

    // --- REPOSITORIES MỚI ---
    private final CheckInEventRepository checkInEventRepository;
    
    // Giữ lại Timezone
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");


    @Override
    public TimelineResponse.CheckInEventResponse createCheckIn(String shareableLink, String userEmail, CheckInRequest request) {
        Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);
        PlanMember member = findMemberByUserAndPlan(user, plan);
        
        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);
        
        // Tạo sự kiện Check-in
        CheckInEvent checkInEvent = CheckInEvent.builder()
                .planMember(member)
                .checkInTimestamp(now)
                .notes(request.getNotes())
                .build();

        // 1. Xử lý Attachments
        if (request.getAttachments() != null) {
            for (CheckInRequest.AttachmentRequest attReq : request.getAttachments()) {
                CheckInAttachment attachment = CheckInAttachment.builder()
                        .fileUrl(attReq.getFileUrl())
                        .storedFilename(attReq.getStoredFilename())
                        .originalFilename(attReq.getOriginalFilename())
                        .contentType(attReq.getContentType())
                        .fileSize(attReq.getFileSize())
                        .build();
                checkInEvent.addAttachment(attachment);
            }
        }

        // 2. Xử lý Task đã hoàn thành
        if (request.getCompletedTaskIds() != null && !request.getCompletedTaskIds().isEmpty()) {
            // Lấy các task hợp lệ từ DB
            Set<Long> taskIds = request.getCompletedTaskIds();
            List<Task> validTasks = taskRepository.findAllById(taskIds).stream()
                    .filter(task -> task.getPlan().getId().equals(plan.getId())) // Đảm bảo task thuộc plan này
                    .collect(Collectors.toList());

            if (validTasks.size() != taskIds.size()) {
                log.warn("User {} tried to check in tasks not belonging to plan {}", userEmail, shareableLink);
                // (Optional: có thể throw lỗi nếu muốn)
            }
            
            for (Task task : validTasks) {
                CheckInTask checkInTask = CheckInTask.builder()
                        .task(task)
                        .build();
                checkInEvent.addTask(checkInTask);
            }
        }
        
        // Lưu sự kiện
        CheckInEvent savedEvent = checkInEventRepository.save(checkInEvent);
        log.info("User {} created CheckInEvent ID {} for plan {}", userEmail, savedEvent.getId(), shareableLink);

        // Map sang Response DTO
        TimelineResponse.CheckInEventResponse response = progressMapper.toCheckInEventResponse(savedEvent);
        
        // Gửi WebSocket (LUỒNG QUAN TRỌNG)
        String destination = "/topic/plan/" + shareableLink + "/progress";
        Map<String, Object> payload = Map.of(
            "type", "NEW_CHECK_IN",
            "checkInEvent", response
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.info("Sent WebSocket update to {} for new CheckInEvent ID {}", destination, savedEvent.getId());
        
        // (Optional: Gửi Feed Event CHECK_IN nếu muốn)
        // feedService.createAndPublishFeedEvent(FeedEventType.CHECK_IN, user, plan, null);

        return response;
    }


    @Override
    @Transactional(readOnly = true)
    public TimelineResponse getDailyTimeline(String shareableLink, String userEmail, LocalDate date) {
        Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);
        findMemberByUserAndPlan(user, plan); // Chỉ để check quyền

        // 1. Xác định khoảng thời gian truy vấn (00:00:00 -> 23:59:59 của ngày)
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
        
        // 2. Lấy tất cả sự kiện trong ngày của plan đó
        List<CheckInEvent> events = checkInEventRepository.findByPlanIdAndTimestampBetweenWithDetails(plan.getId().longValue(), startOfDay, endOfDay);
        
        // 3. Gom nhóm các sự kiện theo ID thành viên
        Map<Integer, List<CheckInEvent>> eventsByMemberId = events.stream()
                .collect(Collectors.groupingBy(event -> event.getPlanMember().getId()));
        
        // 4. Tạo cấu trúc Response (Swimlane)
        TimelineResponse timelineResponse = new TimelineResponse();
        
        for (PlanMember member : plan.getMembers()) {
            List<CheckInEvent> memberEvents = eventsByMemberId.getOrDefault(member.getId(), Collections.emptyList());
            
            // Map thông tin thành viên
            TimelineResponse.MemberInfo memberInfo = progressMapper.toMemberInfo(member);
            
            // Map các sự kiện check-in của thành viên đó
            List<TimelineResponse.CheckInEventResponse> checkInResponses = memberEvents.stream()
                    .map(progressMapper::toCheckInEventResponse)
                    .collect(Collectors.toList()); // Đã sort bởi query
            
            TimelineResponse.MemberTimeline memberTimeline = TimelineResponse.MemberTimeline.builder()
                    .member(memberInfo)
                    .checkIns(checkInResponses)
                    .build();
            
            timelineResponse.add(memberTimeline);
        }
        
        return timelineResponse;
    }

    // --- CÁC PHƯƠNG THỨC CŨ BỊ XÓA (logOrUpdateDailyProgress, getProgressDashboard, buildMemberProgress, validateCheckinDate, calculateStreakForUser) ---


    // --- CÁC PHƯƠNG THỨC STATS/CHART ---
    // Logic này bị hỏng vì DailyProgress không còn được cập nhật.
    // Tạm thời trả về lỗi hoặc dữ liệu rỗng.

    @Override
    @Transactional(readOnly = true)
    public UserStatsResponse getUserStats(String userEmail) {
        log.warn("getUserStats() is called, but its logic (based on DailyProgress) is deprecated.");
        // Trả về dữ liệu rỗng/mặc định
        return UserStatsResponse.builder()
                .checkedInTodayComplete(false)
                .currentStreak(0)
                .totalTasksToday(0)
                .completedTasksToday(0)
                .build();
        // Hoặc: throw new UnsupportedOperationException("UserStats logic needs to be rewritten based on CheckInEvents.");
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProgressChartDataResponse> getProgressChartData(String userEmail) {
        log.warn("getProgressChartData() is called, but its logic (based on DailyProgress) is deprecated.");
        // Trả về rỗng
        return Collections.emptyList();
        // Hoặc: throw new UnsupportedOperationException("ProgressChartData logic needs to be rewritten based on CheckInEvents.");
    }

    // --- Helper Methods ---
    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + email));
    }

    private Plan findPlanByShareableLink(String link) {
        // Lấy plan và fetch members
        return planRepository.findByShareableLink(link)
                .map(plan -> { plan.getMembers().size(); return plan; })
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kế hoạch với link: " + link));
    }

    private PlanMember findMemberByUserAndPlan(User user, Plan plan) {
        return planMemberRepository.findByPlanIdAndUserId(plan.getId(), user.getId())
                .orElseThrow(() -> new AccessDeniedException("Bạn không phải là thành viên của kế hoạch này."));
    }
}