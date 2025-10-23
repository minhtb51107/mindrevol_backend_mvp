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
// import com.example.demo.shared.service.FileUploadService; // Không cần nếu chỉ xóa file vật lý trực tiếp
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate; // *** THÊM IMPORT ***
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map; // *** THÊM IMPORT ***


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
    private final SimpMessagingTemplate messagingTemplate; // *** INJECT SimpMessagingTemplate ***

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Override
    public TaskCommentResponse addTaskComment(Long taskId, TaskCommentRequest request, String userEmail) {
        Task task = findTaskByIdWithPlan(taskId); // Lấy cả Plan để lấy shareableLink
        User author = findUserByEmail(userEmail);
        ensureUserIsMemberOfPlan(author, task.getPlan());

        TaskComment comment = TaskComment.builder()
                .task(task)
                .author(author)
                .content(request.getContent())
                .build();

        TaskComment savedComment = taskCommentRepository.save(comment);
        log.info("User {} added comment {} to task {}", userEmail, savedComment.getId(), taskId);
        TaskCommentResponse commentResponse = taskMapper.toTaskCommentResponse(savedComment);

        // *** GỬI MESSAGE WEBSOCKET ***
        String destination = "/topic/plan/" + task.getPlan().getShareableLink() + "/tasks";
        Map<String, Object> payload = Map.of(
            "type", "NEW_TASK_COMMENT",
            "taskId", taskId,
            "comment", commentResponse
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.debug("Sent WebSocket update for new task comment to {}", destination);
        // *** KẾT THÚC GỬI WEBSOCKET ***

        return commentResponse;
    }

    @Override
    public TaskCommentResponse updateTaskComment(Long commentId, UpdateTaskCommentRequest request, String userEmail) {
        TaskComment comment = findTaskCommentByIdWithTaskAndPlan(commentId); // Lấy cả Task và Plan
        User user = findUserByEmail(userEmail);

        if (!comment.getAuthor().getId().equals(user.getId())) {
            log.warn("User {} attempted to update comment {} without permission (not author).", userEmail, commentId);
            throw new AccessDeniedException("Bạn không có quyền sửa bình luận này.");
        }

        comment.setContent(request.getContent());
        TaskComment updatedComment = taskCommentRepository.save(comment);
        log.info("User {} updated comment {}", userEmail, commentId);
        TaskCommentResponse commentResponse = taskMapper.toTaskCommentResponse(updatedComment);

        // *** GỬI MESSAGE WEBSOCKET ***
        String destination = "/topic/plan/" + comment.getTask().getPlan().getShareableLink() + "/tasks";
        Map<String, Object> payload = Map.of(
            "type", "UPDATE_TASK_COMMENT",
            "taskId", comment.getTask().getId(),
            "comment", commentResponse
        );
        messagingTemplate.convertAndSend(destination, payload);
         log.debug("Sent WebSocket update for updated task comment to {}", destination);
        // *** KẾT THÚC GỬI WEBSOCKET ***

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

         // *** GỬI MESSAGE WEBSOCKET ***
        String destination = "/topic/plan/" + plan.getShareableLink() + "/tasks";
        Map<String, Object> payload = Map.of(
            "type", "DELETE_TASK_COMMENT",
            "taskId", taskId,
            "commentId", commentId
        );
        messagingTemplate.convertAndSend(destination, payload);
         log.debug("Sent WebSocket update for deleted task comment to {}", destination);
        // *** KẾT THÚC GỬI WEBSOCKET ***
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
                // Tạo đường dẫn tuyệt đối an toàn
                .filePath(Paths.get(uploadDir).resolve(fileInfo.getStoredFilename()).normalize().toAbsolutePath().toString())
                .build();

        TaskAttachment savedAttachment = taskAttachmentRepository.save(attachment);
        log.info("User {} added attachment {} to task {}", userEmail, savedAttachment.getId(), taskId);
        TaskAttachmentResponse attachmentResponse = taskMapper.toTaskAttachmentResponse(savedAttachment);

        // *** GỬI MESSAGE WEBSOCKET ***
        String destination = "/topic/plan/" + task.getPlan().getShareableLink() + "/tasks";
         Map<String, Object> payload = Map.of(
            "type", "NEW_TASK_ATTACHMENT",
            "taskId", taskId,
            "attachment", attachmentResponse
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.debug("Sent WebSocket update for new task attachment to {}", destination);
        // *** KẾT THÚC GỬI WEBSOCKET ***

        return attachmentResponse;
    }

    @Override
    public void deleteTaskAttachment(Long attachmentId, String userEmail) {
        TaskAttachment attachment = findTaskAttachmentByIdWithTaskAndPlan(attachmentId);
        User user = findUserByEmail(userEmail);
        Task task = attachment.getTask();
        Plan plan = task.getPlan();
        Long taskId = task.getId(); // Lưu taskId
        String storedFilename = attachment.getStoredFilename(); // Lưu tên file để xóa vật lý

        boolean isPlanOwner = isUserPlanOwner(user, plan);

        if (!isPlanOwner) {
             log.warn("User {} attempted to delete attachment {} without permission.", userEmail, attachmentId);
            throw new AccessDeniedException("Chỉ chủ kế hoạch mới có quyền xóa file đính kèm.");
        }

        // Xóa file vật lý khỏi server
        try {
            // Path filePath = Paths.get(attachment.getFilePath()); // Dùng đường dẫn đã lưu
            // Hoặc tạo lại đường dẫn an toàn:
            Path filePath = Paths.get(uploadDir).resolve(storedFilename).normalize().toAbsolutePath();
            // Đảm bảo file nằm trong thư mục upload trước khi xóa
            if (filePath.startsWith(Paths.get(uploadDir).normalize().toAbsolutePath())) {
                 Files.deleteIfExists(filePath);
                 log.info("Deleted physical file: {}", filePath);
            } else {
                 log.warn("Attempted to delete file outside upload directory: {}", filePath);
            }
        } catch (IOException e) {
            log.error("Failed to delete physical file: {}", storedFilename, e);
            // Có thể ném lỗi hoặc chỉ log tùy theo yêu cầu
            // throw new RuntimeException("Could not delete file: " + storedFilename, e);
        } catch (Exception e) {
            log.error("Unexpected error deleting physical file: {}", storedFilename, e);
        }

        taskAttachmentRepository.delete(attachment);
        log.info("User {} deleted attachment {}", userEmail, attachmentId);

        // *** GỬI MESSAGE WEBSOCKET ***
        String destination = "/topic/plan/" + plan.getShareableLink() + "/tasks";
        Map<String, Object> payload = Map.of(
            "type", "DELETE_TASK_ATTACHMENT",
            "taskId", taskId,
            "attachmentId", attachmentId
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.debug("Sent WebSocket update for deleted task attachment to {}", destination);
        // *** KẾT THÚC GỬI WEBSOCKET ***
    }

    // --- Helper Methods ---

    // Helper tìm Task và fetch Plan
    private Task findTaskByIdWithPlan(Long taskId) {
        return taskRepository.findById(taskId)
                .map(task -> {
                    // Eagerly fetch the plan
                    task.getPlan().getTitle(); // Truy cập một thuộc tính để trigger load
                    return task;
                })
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công việc với ID: " + taskId));
    }

    // Helper tìm Comment và fetch Task, Plan
     private TaskComment findTaskCommentByIdWithTaskAndPlan(Long commentId) {
        return taskCommentRepository.findById(commentId)
                .map(comment -> {
                    comment.getTask().getId(); // Fetch Task
                    comment.getTask().getPlan().getTitle(); // Fetch Plan thông qua Task
                    return comment;
                })
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bình luận với ID: " + commentId));
    }

     // Helper tìm Attachment và fetch Task, Plan
     private TaskAttachment findTaskAttachmentByIdWithTaskAndPlan(Long attachmentId) {
        return taskAttachmentRepository.findById(attachmentId)
                .map(attachment -> {
                    attachment.getTask().getId(); // Fetch Task
                    attachment.getTask().getPlan().getTitle(); // Fetch Plan thông qua Task
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