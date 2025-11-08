// File: src/main/java/com/example/demo/progress/service/impl/ProgressServiceImpl.java
package com.example.demo.progress.service.impl;

import com.example.demo.feed.service.FeedService; 
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

import com.example.demo.progress.dto.request.UpdateCheckInRequest;
import com.example.demo.progress.repository.CheckInTaskRepository; 
import java.time.Duration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// --- CÁC IMPORT MỚI ĐỂ XỬ LÝ COMMENT/REACTION ---
import com.example.demo.community.dto.request.AddReactionRequest;
import com.example.demo.community.dto.request.PostCommentRequest;
import com.example.demo.community.dto.request.UpdateCommentRequest;
import com.example.demo.community.dto.response.CommentResponse;
import com.example.demo.community.entity.ProgressComment;
import com.example.demo.community.entity.ProgressReaction;
import com.example.demo.community.entity.ReactionType;
import com.example.demo.community.mapper.CommentMapper;
import com.example.demo.community.repository.ProgressCommentRepository;
import com.example.demo.community.repository.ProgressReactionRepository;
import java.time.Instant; // Dùng Instant cho comment
import java.util.Optional;
// --- KẾT THÚC IMPORT MỚI ---

@Slf4j
@Service
@RequiredArgsConstructor // Lombok sẽ tự động inject các dependencies
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
    private final CheckInTaskRepository checkInTaskRepository;

    // --- THÊM CÁC DEPENDENCIES TỪ PACKAGE 'community' ---
    private final ProgressCommentRepository progressCommentRepository;
    private final ProgressReactionRepository progressReactionRepository;
    private final CommentMapper commentMapper; // Sẽ cần file này

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final long EDIT_GRACE_PERIOD_HOURS = 24;


    // --- HÀM createCheckIn (Giữ nguyên) ---
    @Override
    public TimelineResponse.CheckInEventResponse createCheckIn(String shareableLink, String userEmail, CheckInRequest request) {
        // (Nội dung hàm của bạn giữ nguyên)
        Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);
        PlanMember member = findMemberByUserAndPlan(user, plan);
        
        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);
        
     // === [BẮT ĐẦU ĐOẠN CODE THÊM MỚI] ===
        // 1. Lấy tất cả các task đã check-in hôm nay của member này
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = now.toLocalDate().atTime(LocalTime.MAX);
        
        // Lưu ý: Bạn cần đảm bảo repository có phương thức này hoặc tương tự
        List<CheckInEvent> todaysEvents = checkInEventRepository.findByPlanMemberIdAndCheckInTimestampBetween(
                member.getId(), startOfDay, endOfDay);

        Set<Long> completedTaskIdsToday = todaysEvents.stream()
                .flatMap(event -> event.getCompletedTasks().stream())
                .map(checkInTask -> checkInTask.getTask().getId())
                .collect(Collectors.toSet());

        // 2. Kiểm tra xem có task nào trong request trùng với task đã hoàn thành không
        if (request.getCompletedTaskIds() != null) {
            for (Long requestingTaskId : request.getCompletedTaskIds()) {
                if (completedTaskIdsToday.contains(requestingTaskId)) {
                    throw new BadRequestException("Công việc (ID: " + requestingTaskId + ") đã được bạn check-in hôm nay rồi!");
                }
            }
        }
        // === [KẾT THÚC ĐOẠN CODE THÊM MỚI] ===
        
        CheckInEvent checkInEvent = CheckInEvent.builder()
                .planMember(member)
                .checkInTimestamp(now)
                .notes(request.getNotes())
                .build();

        // (Logic xử lý Attachments, Tasks, Links giữ nguyên...)
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
        
        return response;
    }
    
 // (Thêm vào ProgressServiceImpl.java)
    @Override
    @Transactional(readOnly = true)
    public Set<Long> getCompletedTaskIdsToday(String shareableLink, String userEmail) {
        Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);
        PlanMember member = findMemberByUserAndPlan(user, plan);

        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = now.toLocalDate().atTime(LocalTime.MAX);

        List<CheckInEvent> todaysEvents = checkInEventRepository.findByPlanMemberIdAndCheckInTimestampBetween(
                member.getId(), startOfDay, endOfDay);

        return todaysEvents.stream()
                .flatMap(event -> event.getCompletedTasks().stream())
                .map(checkInTask -> checkInTask.getTask().getId())
                .collect(Collectors.toSet());
    }

    // --- HÀM getDailyTimeline (Giữ nguyên) ---
    @Override
    @Transactional(readOnly = true)
    public TimelineResponse getDailyTimeline(String shareableLink, String userEmail, LocalDate date) {
        // (Nội dung hàm của bạn giữ nguyên)
        Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);
        findMemberByUserAndPlan(user, plan); 

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
    
    // --- HÀM updateCheckIn (Giữ nguyên) ---
    @Override
    @Transactional
    public TimelineResponse.CheckInEventResponse updateCheckIn(Long checkInEventId, UpdateCheckInRequest request, String userEmail) {
        // (Nội dung hàm của bạn giữ nguyên)
        User currentUser = findUserByEmail(userEmail);
        CheckInEvent event = checkInEventRepository.findById(checkInEventId)
                .orElseThrow(() -> new ResourceNotFoundException("CheckInEvent not found with id: " + checkInEventId));
        validateCheckInModification(event, currentUser); 
        event.setNotes(request.getNotes());
        if (request.getLinks() != null) {
            event.setLinks(request.getLinks().stream()
                    .filter(link -> link != null && !link.trim().isEmpty())
                    .collect(Collectors.toList()));
        } else {
            event.setLinks(Collections.emptyList());
        }
        checkInTaskRepository.deleteAll(event.getCompletedTasks());
        event.getCompletedTasks().clear(); 
        if (request.getCompletedTaskIds() != null && !request.getCompletedTaskIds().isEmpty()) {
            Set<Long> taskIds = request.getCompletedTaskIds().stream().collect(Collectors.toSet());
            List<Task> validTasks = taskRepository.findAllById(taskIds).stream()
                    .filter(task -> task.getPlan().getId().equals(event.getPlanMember().getPlan().getId()))
                    .collect(Collectors.toList());
            for (Task task : validTasks) {
                CheckInTask checkInTask = CheckInTask.builder()
                        .task(task)
                        .build();
                event.addTask(checkInTask);
            }
        }
        CheckInEvent updatedEvent = checkInEventRepository.save(event);
        TimelineResponse.CheckInEventResponse response = progressMapper.toCheckInEventResponse(updatedEvent);
        String topic = "/topic/plan/" + event.getPlanMember().getPlan().getShareableLink() + "/progress";
        messagingTemplate.convertAndSend(topic, 
             Map.of("type", "UPDATE_CHECK_IN", "checkInEvent", response)
        );
        log.info("User {} updated CheckInEvent ID {}", userEmail, updatedEvent.getId());
        return response;
    }

    // --- HÀM deleteCheckIn (Giữ nguyên) ---
    @Override
    @Transactional
    public void deleteCheckIn(Long checkInEventId, String userEmail) {
        // (Nội dung hàm của bạn giữ nguyên)
        User currentUser = findUserByEmail(userEmail);
        CheckInEvent event = checkInEventRepository.findById(checkInEventId)
                .orElseThrow(() -> new ResourceNotFoundException("CheckInEvent not found with id: " + checkInEventId));
        validateCheckInModification(event, currentUser);
        String shareableLink = event.getPlanMember().getPlan().getShareableLink();
        checkInEventRepository.delete(event); 
        String topic = "/topic/plan/" + shareableLink + "/progress";
        messagingTemplate.convertAndSend(topic, 
            Map.of("type", "DELETE_CHECK_IN", "checkInEventId", checkInEventId)
        );
        log.info("User {} deleted CheckInEvent ID {}", userEmail, checkInEventId);
    }
    
    // --- HÀM validateCheckInModification (Giữ nguyên) ---
    private void validateCheckInModification(CheckInEvent event, User currentUser) {
        // (Nội dung hàm của bạn giữ nguyên)
        if (!event.getPlanMember().getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("User is not the owner of this check-in.");
        }
        LocalDateTime checkInTime = event.getCheckInTimestamp(); 
        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE); 
        if (Duration.between(checkInTime, now).toHours() >= EDIT_GRACE_PERIOD_HOURS) {
            throw new BadRequestException("Check-in can no longer be modified after " + EDIT_GRACE_PERIOD_HOURS + " hours.");
        }
    }


    // === TRIỂN KHAI CÁC HÀM MỚI (ĐÃ SỬA LỖI) ===

    @Override
    @Transactional
    public CommentResponse addCommentToCheckIn(Long checkInEventId, PostCommentRequest request, String userEmail) {
        User user = findUserByEmail(userEmail);
        CheckInEvent event = checkInEventRepository.findById(checkInEventId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy CheckInEvent: " + checkInEventId));

        ProgressComment comment = ProgressComment.builder()
                .checkInEvent(event)
                // SỬA LỖI 1: Dùng 'author' thay vì 'user'
                .author(user) //
                .content(request.getContent())
                .createdAt(Instant.now().atOffset(ZoneId.of("Asia/Ho_Chi_Minh").getRules().getOffset(Instant.now()))) // Sửa từ Instant -> OffsetDateTime
                .build();

        ProgressComment savedComment = progressCommentRepository.save(comment);
        log.info("User {} đã thêm bình luận {} vào CheckInEvent {}", userEmail, savedComment.getId(), checkInEventId);
        
        // Gửi WebSocket
        String topic = "/topic/plan/" + event.getPlanMember().getPlan().getShareableLink() + "/progress";
        messagingTemplate.convertAndSend(topic, 
             Map.of("type", "NEW_CHECKIN_COMMENT", "checkInEventId", checkInEventId)
        );

        return commentMapper.toCommentResponse(savedComment);
    }

    @Override
    @Transactional
    public CommentResponse updateCheckInComment(Long commentId, UpdateCommentRequest request, String userEmail) {
        User user = findUserByEmail(userEmail);
        ProgressComment comment = progressCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bình luận: " + commentId));

        // SỬA LỖI 1: Dùng 'getAuthor()' thay vì 'getUser()'
        if (!comment.getAuthor().getId().equals(user.getId())) { //
            throw new AccessDeniedException("Bạn không phải là tác giả của bình luận này.");
        }
        
        comment.setContent(request.getContent());
        ProgressComment updatedComment = progressCommentRepository.save(comment);
        log.info("User {} đã cập nhật bình luận {}", userEmail, commentId);

        // Gửi WebSocket
        String topic = "/topic/plan/" + comment.getCheckInEvent().getPlanMember().getPlan().getShareableLink() + "/progress";
        messagingTemplate.convertAndSend(topic, 
             Map.of("type", "UPDATE_CHECKIN_COMMENT", "checkInEventId", comment.getCheckInEvent().getId())
        );

        return commentMapper.toCommentResponse(updatedComment);
    }

    @Override
    @Transactional
    public void deleteCheckInComment(Long commentId, String userEmail) {
        User user = findUserByEmail(userEmail);
        ProgressComment comment = progressCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bình luận: " + commentId));
        
        CheckInEvent event = comment.getCheckInEvent();

        // SỬA LỖI 1: Dùng 'getAuthor()' thay vì 'getUser()'
        boolean isAuthor = comment.getAuthor().getId().equals(user.getId()); //
        boolean isPlanOwner = event.getPlanMember().getPlan().getCreator().getId().equals(user.getId());
        
        if (!isAuthor && !isPlanOwner) {
            throw new AccessDeniedException("Bạn không có quyền xóa bình luận này.");
        }

        progressCommentRepository.delete(comment);
        log.info("User {} đã xóa bình luận {}", userEmail, commentId);
        
        // Gửi WebSocket
        String topic = "/topic/plan/" + event.getPlanMember().getPlan().getShareableLink() + "/progress";
        messagingTemplate.convertAndSend(topic, 
             Map.of("type", "DELETE_CHECKIN_COMMENT", "checkInEventId", event.getId())
        );
    }

    @Override
    @Transactional
    public void toggleReactionOnCheckIn(Long checkInEventId, AddReactionRequest request, String userEmail) {
        User user = findUserByEmail(userEmail);
        CheckInEvent event = checkInEventRepository.findById(checkInEventId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy CheckInEvent: " + checkInEventId));

        ReactionType newType = request.getReactionType();

        // SỬA LỖI: Tìm BẤT KỲ reaction nào của user này, KHÔNG CẦN BIẾT TYPE
        Optional<ProgressReaction> existingReactionOpt = progressReactionRepository
                .findByCheckInEventIdAndUserId(checkInEventId, user.getId()); // Dùng phương thức này

        if (existingReactionOpt.isPresent()) {
            // User này đã reaction trước đó
            ProgressReaction existingReaction = existingReactionOpt.get();

            if (existingReaction.getType().equals(newType)) {
                // 1. CÙNG LOẠI: User bấm lại reaction cũ -> Xóa (toggle off)
                progressReactionRepository.delete(existingReaction);
                log.info("User {} đã XÓA reaction {} khỏi CheckInEvent {}", userEmail, newType, checkInEventId);
            
            } else {
                // 2. KHÁC LOẠI: User đổi reaction -> Cập nhật type
                existingReaction.setType(newType);
                progressReactionRepository.save(existingReaction); // Hàm save này sẽ là UPDATE
                log.info("User {} đã ĐỔI reaction thành {} trên CheckInEvent {}", userEmail, newType, checkInEventId);
            }

        } else {
            // 3. CHƯA CÓ: User reaction lần đầu -> Thêm mới (Insert)
            ProgressReaction newReaction = ProgressReaction.builder()
                    .checkInEvent(event)
                    .user(user) 
                    .type(newType)
                    .build();
            progressReactionRepository.save(newReaction);
            log.info("User {} đã THÊM reaction {} vào CheckInEvent {}", userEmail, newType, checkInEventId);
        }
        
        // Gửi WebSocket (Giữ nguyên)
        String topic = "/topic/plan/" + event.getPlanMember().getPlan().getShareableLink() + "/progress";
        messagingTemplate.convertAndSend(topic, 
             Map.of("type", "UPDATE_CHECKIN_REACTION", "checkInEventId", checkInEventId)
        );
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