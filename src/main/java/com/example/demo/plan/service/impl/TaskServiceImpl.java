package com.example.demo.plan.service.impl;

import com.example.demo.plan.dto.request.TaskCommentRequest;
import com.example.demo.plan.dto.request.UpdateTaskCommentRequest;
import com.example.demo.plan.dto.response.TaskAttachmentResponse;
import com.example.demo.plan.dto.response.TaskCommentResponse;
import com.example.demo.plan.entity.*; // Import các entity cần thiết
import com.example.demo.plan.mapper.TaskMapper;
import com.example.demo.plan.repository.PlanMemberRepository;
import com.example.demo.plan.repository.TaskAttachmentRepository;
import com.example.demo.plan.repository.TaskCommentRepository;
import com.example.demo.plan.repository.TaskRepository;
import com.example.demo.plan.service.TaskService;
import com.example.demo.shared.dto.response.FileUploadResponse;
import com.example.demo.shared.exception.ResourceNotFoundException;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository; // *** THÊM IMPORT ***
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.notification.service.NotificationService; // *** THÊM IMPORT ***

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Matcher; // *** THÊM IMPORT ***
import java.util.regex.Pattern; // *** THÊM IMPORT ***
import java.util.Set; // *** THÊM IMPORT ***
import java.util.HashSet; // *** THÊM IMPORT ***

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final UserRepository userRepository;
    private final PlanMemberRepository planMemberRepository;
    private final TaskMapper taskMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService; // *** THÊM DEPENDENCY ***

    @Value("${file.upload-dir}")
    private String uploadDir;

    // *** Định nghĩa Pattern để tìm mention dạng @[Display Name](userId) ***
    private static final Pattern MENTION_PATTERN = Pattern.compile("@\\[[^\\]]+?\\]\\((\\d+?)\\)");

    @Override
    public TaskCommentResponse addTaskComment(Long taskId, TaskCommentRequest request, String userEmail) {
        Task task = findTaskByIdWithPlan(taskId);
        User author = findUserByEmail(userEmail);
        ensureUserIsMemberOfPlan(author, task.getPlan());

        TaskComment comment = TaskComment.builder()
                .task(task)
                .author(author)
                .content(request.getContent()) // Nội dung có thể chứa mention
                .build();

        TaskComment savedComment = taskCommentRepository.save(comment);
        log.info("User {} added comment {} to task {}", userEmail, savedComment.getId(), taskId);
        TaskCommentResponse commentResponse = taskMapper.toTaskCommentResponse(savedComment);

        // *** XỬ LÝ NOTIFICATION CHO MENTION ***
        String authorName = getUserFullName(author); // Lấy tên người bình luận
        Plan plan = task.getPlan();
        Set<Integer> mentionedUserIds = extractMentionedUserIds(savedComment.getContent());
        log.debug("Mentioned User IDs in task comment {}: {}", savedComment.getId(), mentionedUserIds);

        for (Integer mentionedUserId : mentionedUserIds) {
            // Không gửi notification nếu tự mention
            if (!mentionedUserId.equals(author.getId())) {
                userRepository.findById(mentionedUserId).ifPresent(mentionedUser -> {
                    // Kiểm tra xem người được mention có còn là thành viên không
                    if (isUserMemberOfPlan(mentionedUser, plan)) { // Sử dụng helper isUserMemberOfPlan
                        String taskDescShort = task.getDescription().length() > 50 ? task.getDescription().substring(0, 47) + "..." : task.getDescription();
                        String mentionMessage = String.format("%s đã nhắc đến bạn trong bình luận về công việc '%s' (%s).",
                                                            authorName, taskDescShort, plan.getTitle());
                        // Link đến trang plan, thêm fragment để scroll tới comment của task
                        String mentionLink = String.format("/plan/%s?taskId=%d#task-comment-%d", // Sử dụng query param taskId và fragment commentId
                                                         plan.getShareableLink(), taskId, savedComment.getId());
                        notificationService.createNotification(mentionedUser, mentionMessage, mentionLink);
                        log.info("Sent mention notification to user {} from task comment {}", mentionedUserId, savedComment.getId());
                    } else {
                         log.warn("User {} mentioned in task comment {} is no longer a member of plan {}.", mentionedUserId, savedComment.getId(), plan.getId());
                    }
                });
            }
        }
        // *** KẾT THÚC XỬ LÝ MENTION ***

        // Gửi WebSocket (giữ nguyên)
        String destination = "/topic/plan/" + task.getPlan().getShareableLink() + "/tasks";
        Map<String, Object> payload = Map.of(
            "type", "NEW_TASK_COMMENT",
            "taskId", taskId,
            "comment", commentResponse
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.debug("Sent WebSocket update for new task comment to {}", destination);

        return commentResponse;
    }

    @Override
    public TaskCommentResponse updateTaskComment(Long commentId, UpdateTaskCommentRequest request, String userEmail) {
        TaskComment comment = findTaskCommentByIdWithTaskAndPlan(commentId);
        User user = findUserByEmail(userEmail);

        if (!comment.getAuthor().getId().equals(user.getId())) {
            log.warn("User {} attempted to update task comment {} without permission (not author).", userEmail, commentId);
            throw new AccessDeniedException("Bạn không có quyền sửa bình luận này.");
        }

        String oldContent = comment.getContent(); // Lưu lại nội dung cũ để so sánh mention
        comment.setContent(request.getContent()); // Cập nhật nội dung mới
        TaskComment updatedComment = taskCommentRepository.save(comment);
        log.info("User {} updated task comment {}", userEmail, commentId);
        TaskCommentResponse commentResponse = taskMapper.toTaskCommentResponse(updatedComment);

        // *** XỬ LÝ NOTIFICATION CHO MENTION KHI UPDATE ***
        Task task = comment.getTask();
        Plan plan = task.getPlan();
        String authorName = getUserFullName(user); // Lấy tên người sửa

        Set<Integer> oldMentionedIds = extractMentionedUserIds(oldContent);
        Set<Integer> newMentionedIds = extractMentionedUserIds(updatedComment.getContent());
        // Chỉ gửi cho những người mới được mention (chưa có trong oldMentionedIds)
        for (Integer mentionedUserId : newMentionedIds) {
            if (!oldMentionedIds.contains(mentionedUserId) && // Là mention mới
                !mentionedUserId.equals(user.getId())) {    // Không phải tự mention
                userRepository.findById(mentionedUserId).ifPresent(mentionedUser -> {
                     if (isUserMemberOfPlan(mentionedUser, plan)) { // Kiểm tra là thành viên
                        String taskDescShort = task.getDescription().length() > 50 ? task.getDescription().substring(0, 47) + "..." : task.getDescription();
                        String mentionMessage = String.format("%s đã nhắc đến bạn trong bình luận đã sửa về công việc '%s' (%s).",
                                                            authorName, taskDescShort, plan.getTitle());
                        String mentionLink = String.format("/plan/%s?taskId=%d#task-comment-%d",
                                                         plan.getShareableLink(), task.getId(), updatedComment.getId());
                        notificationService.createNotification(mentionedUser, mentionMessage, mentionLink);
                        log.info("Sent mention notification (update) to user {} from task comment {}", mentionedUserId, updatedComment.getId());
                     }
                });
            }
        }
        // *** KẾT THÚC XỬ LÝ MENTION ***

        // Gửi WebSocket (giữ nguyên)
        String destination = "/topic/plan/" + comment.getTask().getPlan().getShareableLink() + "/tasks";
        Map<String, Object> payload = Map.of(
            "type", "UPDATE_TASK_COMMENT",
            "taskId", comment.getTask().getId(),
            "comment", commentResponse
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.debug("Sent WebSocket update for updated task comment to {}", destination);

        return commentResponse;
    }

    @Override
    public void deleteTaskComment(Long commentId, String userEmail) {
        TaskComment comment = findTaskCommentByIdWithTaskAndPlan(commentId);
        User user = findUserByEmail(userEmail);
        Task task = comment.getTask();
        Plan plan = task.getPlan();
        Long taskId = task.getId(); // Lưu taskId trước khi xóa comment

        boolean isAuthor = comment.getAuthor().getId().equals(user.getId());
        boolean isPlanOwner = isUserPlanOwner(user, plan);

        if (!isAuthor && !isPlanOwner) {
            log.warn("User {} attempted to delete comment {} without permission.", userEmail, commentId);
            throw new AccessDeniedException("Bạn không có quyền xóa bình luận này.");
        }

        taskCommentRepository.delete(comment);
        log.info("User {} deleted comment {}", userEmail, commentId);

         // Gửi WebSocket (giữ nguyên)
        String destination = "/topic/plan/" + plan.getShareableLink() + "/tasks";
        Map<String, Object> payload = Map.of(
            "type", "DELETE_TASK_COMMENT",
            "taskId", taskId,
            "commentId", commentId
        );
        messagingTemplate.convertAndSend(destination, payload);
         log.debug("Sent WebSocket update for deleted task comment to {}", destination);
    }

    @Override
    public TaskAttachmentResponse addTaskAttachment(Long taskId, FileUploadResponse fileInfo, String userEmail) {
        Task task = findTaskByIdWithPlan(taskId);
        User uploader = findUserByEmail(userEmail);
        ensureUserIsMemberOfPlan(uploader, task.getPlan());

        TaskAttachment attachment = TaskAttachment.builder()
                .task(task)
                .originalFilename(fileInfo.getOriginalFilename())
                .storedFilename(fileInfo.getStoredFilename())
                .contentType(fileInfo.getContentType())
                .fileSize(fileInfo.getSize())
                .fileUrl(fileInfo.getFileUrl())
                .filePath(Paths.get(uploadDir).resolve(fileInfo.getStoredFilename()).normalize().toAbsolutePath().toString())
                .build();

        TaskAttachment savedAttachment = taskAttachmentRepository.save(attachment);
        log.info("User {} added attachment {} to task {}", userEmail, savedAttachment.getId(), taskId);
        TaskAttachmentResponse attachmentResponse = taskMapper.toTaskAttachmentResponse(savedAttachment);

        // Gửi WebSocket (giữ nguyên)
        String destination = "/topic/plan/" + task.getPlan().getShareableLink() + "/tasks";
         Map<String, Object> payload = Map.of(
            "type", "NEW_TASK_ATTACHMENT",
            "taskId", taskId,
            "attachment", attachmentResponse
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.debug("Sent WebSocket update for new task attachment to {}", destination);

        return attachmentResponse;
    }

    @Override
    public void deleteTaskAttachment(Long attachmentId, String userEmail) {
        TaskAttachment attachment = findTaskAttachmentByIdWithTaskAndPlan(attachmentId);
        User user = findUserByEmail(userEmail);
        Task task = attachment.getTask();
        Plan plan = task.getPlan();
        Long taskId = task.getId();
        String storedFilename = attachment.getStoredFilename();

        boolean isPlanOwner = isUserPlanOwner(user, plan);

        if (!isPlanOwner) {
             log.warn("User {} attempted to delete task attachment {} without permission (not owner).", userEmail, attachmentId);
            throw new AccessDeniedException("Chỉ chủ sở hữu kế hoạch mới có quyền xóa file đính kèm công việc.");
        }

        // Xóa file vật lý (giữ nguyên)
        try {
            Path filePath = Paths.get(uploadDir).resolve(storedFilename).normalize().toAbsolutePath();
            if (filePath.startsWith(Paths.get(uploadDir).normalize().toAbsolutePath())) {
                 Files.deleteIfExists(filePath);
                 log.info("Deleted physical file: {}", filePath);
            } else {
                 log.warn("Attempted to delete file outside upload directory: {}", filePath);
            }
        } catch (IOException e) {
            log.error("Failed to delete physical file: {}", storedFilename, e);
        } catch (Exception e) {
            log.error("Unexpected error deleting physical file: {}", storedFilename, e);
        }

        taskAttachmentRepository.delete(attachment);
        log.info("User {} deleted task attachment {}", userEmail, attachmentId);

        // Gửi WebSocket (giữ nguyên)
        String destination = "/topic/plan/" + plan.getShareableLink() + "/tasks";
        Map<String, Object> payload = Map.of(
            "type", "DELETE_TASK_ATTACHMENT",
            "taskId", taskId,
            "attachmentId", attachmentId
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.debug("Sent WebSocket update for deleted task attachment to {}", destination);
    }

    // --- Helper Methods ---

    // *** THÊM HELPER TRÍCH XUẤT MENTION (giống CommunityServiceImpl) ***
    private Set<Integer> extractMentionedUserIds(String content) {
        Set<Integer> userIds = new HashSet<>();
        if (content == null || content.isBlank()) {
            return userIds;
        }
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            try {
                Integer userId = Integer.parseInt(matcher.group(1));
                userIds.add(userId);
            } catch (NumberFormatException e) {
                log.warn("Could not parse user ID from mention: {}", matcher.group(1));
            }
        }
        return userIds;
    }

    // *** THÊM HELPER isUserMemberOfPlan NHẬN Plan entity ***
    private boolean isUserMemberOfPlan(User user, Plan plan) {
        if (plan == null || plan.getMembers() == null || user == null) {
             return false;
        }
        // Tối ưu: Dùng PlanMemberRepository nếu Plan không load sẵn Members
        // return planMemberRepository.findByPlanIdAndUserId(plan.getId(), user.getId()).isPresent();
        // Hoặc dùng stream nếu Members đã được load (như trong trường hợp này)
        return plan.getMembers().stream()
                   .anyMatch(member -> member.getUser() != null && member.getUser().getId().equals(user.getId()));
    }

    // *** THÊM HELPER LẤY TÊN ĐẦY ĐỦ (giống các service/mapper khác) ***
     private String getUserFullName(User user) {
        if (user == null) return "N/A";
        if (user.getCustomer() != null && user.getCustomer().getFullname() != null && !user.getCustomer().getFullname().isBlank()) {
            return user.getCustomer().getFullname();
        }
        if (user.getEmployee() != null && user.getEmployee().getFullname() != null && !user.getEmployee().getFullname().isBlank()) {
            return user.getEmployee().getFullname();
        }
        return user.getEmail();
    }

    private Task findTaskByIdWithPlan(Long taskId) {
        return taskRepository.findById(taskId)
                .map(task -> {
                    task.getPlan().getTitle(); // Trigger load Plan
                    return task;
                })
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công việc với ID: " + taskId));
    }

     private TaskComment findTaskCommentByIdWithTaskAndPlan(Long commentId) {
        return taskCommentRepository.findById(commentId)
                .map(comment -> {
                    comment.getTask().getId(); // Trigger load Task
                    comment.getTask().getPlan().getTitle(); // Trigger load Plan
                    return comment;
                })
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bình luận với ID: " + commentId));
    }

     private TaskAttachment findTaskAttachmentByIdWithTaskAndPlan(Long attachmentId) {
        return taskAttachmentRepository.findById(attachmentId)
                .map(attachment -> {
                    attachment.getTask().getId(); // Trigger load Task
                    attachment.getTask().getPlan().getTitle(); // Trigger load Plan
                    return attachment;
                })
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy file đính kèm với ID: " + attachmentId));
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + email));
    }

    private void ensureUserIsMemberOfPlan(User user, Plan plan) {
        boolean isMember = planMemberRepository.findByPlanIdAndUserId(plan.getId(), user.getId()).isPresent();
        if (!isMember) {
            throw new AccessDeniedException("Bạn không phải là thành viên của kế hoạch này.");
        }
    }

    private boolean isUserPlanOwner(User user, Plan plan) {
        return planMemberRepository.findByPlanIdAndUserId(plan.getId(), user.getId())
                .map(member -> member.getRole() == MemberRole.OWNER)
                .orElse(false);
    }
}