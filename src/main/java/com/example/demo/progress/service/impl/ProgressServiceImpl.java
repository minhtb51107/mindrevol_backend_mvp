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

// --- CÁC IMPORT MỚI ---
import com.example.demo.progress.dto.request.UpdateCheckInRequest;
import com.example.demo.progress.repository.CheckInTaskRepository; // (Cần cho việc xóa task)
import java.time.Duration;
// --- KẾT THÚC IMPORT MỚI ---

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

    // --- (Các dependencies gốc) ---
    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final PlanMemberRepository planMemberRepository;
    private final TaskRepository taskRepository;
    private final ProgressMapper progressMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final FeedService feedService;
    private final CheckInEventRepository checkInEventRepository;
    
    // --- (MỚI) Dependency ---
    private final CheckInTaskRepository checkInTaskRepository; // Cần để xóa liên kết task

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    
    // --- (MỚI) Hằng số thời gian ân hạn ---
    private static final long EDIT_GRACE_PERIOD_HOURS = 24; // 24 giờ


    @Override
    public TimelineResponse.CheckInEventResponse createCheckIn(String shareableLink, String userEmail, CheckInRequest request) {
        Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);
        PlanMember member = findMemberByUserAndPlan(user, plan);
        
        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);
        
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
            Set<Long> taskIds = request.getCompletedTaskIds();
            List<Task> validTasks = taskRepository.findAllById(taskIds).stream()
                    .filter(task -> task.getPlan().getId().equals(plan.getId()))
                    .collect(Collectors.toList());

            if (validTasks.size() != taskIds.size()) {
                log.warn("User {} tried to check in tasks not belonging to plan {}", userEmail, shareableLink);
            }
            
            for (Task task : validTasks) {
                CheckInTask checkInTask = CheckInTask.builder()
                        .task(task)
                        .build();
                checkInEvent.addTask(checkInTask);
            }
        }
        
        // 3. Xử lý Links
        if (request.getLinks() != null && !request.getLinks().isEmpty()) {
            List<String> validLinks = request.getLinks().stream()
                    .filter(link -> link != null && !link.trim().isEmpty())
                    .collect(Collectors.toList());
            checkInEvent.setLinks(validLinks);
        }

        CheckInEvent savedEvent = checkInEventRepository.save(checkInEvent);
        log.info("User {} created CheckInEvent ID {} for plan {}", userEmail, savedEvent.getId(), shareableLink);

        TimelineResponse.CheckInEventResponse response = progressMapper.toCheckInEventResponse(savedEvent);
        
        // Gửi WebSocket
        String destination = "/topic/plan/" + shareableLink + "/progress";
        Map<String, Object> payload = Map.of(
            "type", "NEW_CHECK_IN",
            "checkInEvent", response
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.info("Sent WebSocket update to {} for new CheckInEvent ID {}", destination, savedEvent.getId());
        
        // (Gửi Feed Event)
        // feedService.createAndPublishFeedEvent(FeedEventType.CHECK_IN, user, plan, null);

        return response;
    }


    @Override
    @Transactional(readOnly = true)
    public TimelineResponse getDailyTimeline(String shareableLink, String userEmail, LocalDate date) {
        Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);
        findMemberByUserAndPlan(user, plan); // Check quyền

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
        
        List<CheckInEvent> events = checkInEventRepository.findByPlanIdAndTimestampBetweenWithDetails(plan.getId().longValue(), startOfDay, endOfDay);
        
        Map<Integer, List<CheckInEvent>> eventsByMemberId = events.stream()
                .collect(Collectors.groupingBy(event -> event.getPlanMember().getId()));
        
        TimelineResponse timelineResponse = new TimelineResponse();
        
        for (PlanMember member : plan.getMembers()) {
            List<CheckInEvent> memberEvents = eventsByMemberId.getOrDefault(member.getId(), Collections.emptyList());
            TimelineResponse.MemberInfo memberInfo = progressMapper.toMemberInfo(member);
            List<TimelineResponse.CheckInEventResponse> checkInResponses = memberEvents.stream()
                    .map(progressMapper::toCheckInEventResponse)
                    .collect(Collectors.toList());
            
            TimelineResponse.MemberTimeline memberTimeline = TimelineResponse.MemberTimeline.builder()
                    .member(memberInfo)
                    .checkIns(checkInResponses)
                    .build();
            
            timelineResponse.add(memberTimeline);
        }
        
        return timelineResponse;
    }
    
    // --- (MỚI) HÀM LOGIC SỬA CHECK-IN ---
    @Override
    @Transactional
    public TimelineResponse.CheckInEventResponse updateCheckIn(Long checkInEventId, UpdateCheckInRequest request, String userEmail) {
        User currentUser = findUserByEmail(userEmail);
        
        // 1. Tìm CheckInEvent gốc
        CheckInEvent event = checkInEventRepository.findById(checkInEventId)
                .orElseThrow(() -> new ResourceNotFoundException("CheckInEvent not found with id: " + checkInEventId));

        // 2. Xác thực (Authorization)
        validateCheckInModification(event, currentUser); // (Xem hàm helper bên dưới)

        // 3. Cập nhật các trường đơn giản
        event.setNotes(request.getNotes());
        // (Lọc link rỗng)
        if (request.getLinks() != null) {
            event.setLinks(request.getLinks().stream()
                    .filter(link -> link != null && !link.trim().isEmpty())
                    .collect(Collectors.toList()));
        } else {
            event.setLinks(Collections.emptyList());
        }
        // (V1 không hỗ trợ sửa attachments)

        // 4. Cập nhật các Task đã hoàn thành
        // (Do có orphanRemoval = true, chúng ta chỉ cần clear() và add())
        
        // 4.1. Xóa các liên kết task cũ
        // (Lưu ý: Cần xóa thủ công khỏi repo trung gian NẾU orphanRemoval không hoạt động)
        // (Cách an toàn: Xóa thủ công)
        checkInTaskRepository.deleteAll(event.getCompletedTasks());
        event.getCompletedTasks().clear(); // Xóa khỏi Set

        // 4.2. Thêm các liên kết task mới (nếu có)
        if (request.getCompletedTaskIds() != null && !request.getCompletedTaskIds().isEmpty()) {
            Set<Long> taskIds = request.getCompletedTaskIds().stream().collect(Collectors.toSet());
            List<Task> validTasks = taskRepository.findAllById(taskIds).stream()
                    .filter(task -> task.getPlan().getId().equals(event.getPlanMember().getPlan().getId()))
                    .collect(Collectors.toList());
            
            for (Task task : validTasks) {
                CheckInTask checkInTask = CheckInTask.builder()
                        .task(task)
                        .build();
                event.addTask(checkInTask); // Thêm vào Set
            }
        }
        
        CheckInEvent updatedEvent = checkInEventRepository.save(event);
        
        // (Nếu bạn có logic FeedService, hãy cập nhật sự kiện feed)
        // feedService.createAndPublishFeedEvent(FeedEventType.CHECKIN_UPDATED, updatedEvent.getPlanMember().getUser(), updatedEvent.getPlanMember().getPlan(), null);

        // 5. Gửi WebSocket (quan trọng)
        TimelineResponse.CheckInEventResponse response = progressMapper.toCheckInEventResponse(updatedEvent);
        String topic = "/topic/plan/" + event.getPlanMember().getPlan().getShareableLink() + "/progress";
        
        messagingTemplate.convertAndSend(topic, 
             Map.of("type", "UPDATE_CHECK_IN", "checkInEvent", response)
        );
        
        log.info("User {} updated CheckInEvent ID {}", userEmail, updatedEvent.getId());
        return response;
    }

    // --- (MỚI) HÀM LOGIC XÓA CHECK-IN ---
    @Override
    @Transactional
    public void deleteCheckIn(Long checkInEventId, String userEmail) {
        User currentUser = findUserByEmail(userEmail);
        
        // 1. Tìm CheckInEvent gốc
        CheckInEvent event = checkInEventRepository.findById(checkInEventId)
                .orElseThrow(() -> new ResourceNotFoundException("CheckInEvent not found with id: " + checkInEventId));

        // 2. Xác thực (Authorization)
        validateCheckInModification(event, currentUser);

        String shareableLink = event.getPlanMember().getPlan().getShareableLink();

        // 3. Xóa sự kiện
        checkInEventRepository.delete(event); // CascadeType.ALL và orphanRemoval=true sẽ xóa CheckInTask, CheckInAttachment...

        // (Nếu bạn có logic FeedService, hãy xóa sự kiện feed)
        // feedService.deleteFeedEvent(event.getId(), FeedEventType.CHECKIN_CREATED);
        // feedService.deleteFeedEvent(event.getId(), FeedEventType.CHECKIN_UPDATED);

        // 4. Gửi WebSocket
        String topic = "/topic/plan/" + shareableLink + "/progress";
        messagingTemplate.convertAndSend(topic, 
            Map.of("type", "DELETE_CHECK_IN", "checkInEventId", checkInEventId)
        );
        log.info("User {} deleted CheckInEvent ID {}", userEmail, checkInEventId);
    }

    // --- (MỚI) HÀM HELPER XÁC THỰC (ĐÃ SỬA LỖI Instant.now()) ---
    private void validateCheckInModification(CheckInEvent event, User currentUser) {
        // 1. User có phải là chủ sở hữu của check-in không?
        if (!event.getPlanMember().getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("User is not the owner of this check-in.");
        }

        // 2. Check-in có còn trong thời gian ân hạn 24 giờ không?
        // (Sửa: So sánh LocalDateTime với LocalDateTime)
        LocalDateTime checkInTime = event.getCheckInTimestamp(); // (Giả định đã ở múi giờ VN)
        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE); // Lấy giờ hiện tại ở VN
        
        if (Duration.between(checkInTime, now).toHours() >= EDIT_GRACE_PERIOD_HOURS) {
            throw new BadRequestException("Check-in can no longer be modified after " + EDIT_GRACE_PERIOD_HOURS + " hours.");
        }
    }

    // --- (Các hàm Stats/Chart và Helper cũ giữ nguyên) ---

    @Override
    @Transactional(readOnly = true)
    public UserStatsResponse getUserStats(String userEmail) {
        log.warn("getUserStats() is called, but its logic (based on DailyProgress) is deprecated.");
        return UserStatsResponse.builder()
                .checkedInTodayComplete(false)
                .currentStreak(0)
                .totalTasksToday(0)
                .completedTasksToday(0)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProgressChartDataResponse> getProgressChartData(String userEmail) {
        log.warn("getProgressChartData() is called, but its logic (based on DailyProgress) is deprecated.");
        return Collections.emptyList();
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + email));
    }

    private Plan findPlanByShareableLink(String link) {
        return planRepository.findByShareableLink(link)
                .map(plan -> { plan.getMembers().size(); return plan; })
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kế hoạch với link: " + link));
    }

    private PlanMember findMemberByUserAndPlan(User user, Plan plan) {
        return planMemberRepository.findByPlanIdAndUserId(plan.getId(), user.getId())
                .orElseThrow(() -> new AccessDeniedException("Bạn không phải là thành viên của kế hoạch này."));
    }
}