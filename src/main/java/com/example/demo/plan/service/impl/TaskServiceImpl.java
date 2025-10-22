package com.example.demo.plan.service.impl;

import com.example.demo.plan.dto.request.TaskCommentRequest;
import com.example.demo.plan.dto.request.UpdateTaskCommentRequest;
import com.example.demo.plan.dto.response.TaskAttachmentResponse;
import com.example.demo.plan.dto.response.TaskCommentResponse;
import com.example.demo.plan.entity.*; // Import các entity cần thiết (Task, TaskComment, TaskAttachment, PlanMember, MemberRole)
import com.example.demo.plan.mapper.TaskMapper;
import com.example.demo.plan.repository.PlanMemberRepository;
import com.example.demo.plan.repository.TaskAttachmentRepository;
import com.example.demo.plan.repository.TaskCommentRepository;
import com.example.demo.plan.repository.TaskRepository;
import com.example.demo.plan.service.TaskService;
import com.example.demo.shared.dto.response.FileUploadResponse;
import com.example.demo.shared.exception.ResourceNotFoundException;
import com.example.demo.shared.service.FileUploadService; // Có thể cần để xóa file vật lý
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value; // Import Value
import org.springframework.security.access.AccessDeniedException; // Import AccessDeniedException
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException; // Import nếu có logic xóa file vật lý
import java.nio.file.Files; // Import nếu có logic xóa file vật lý
import java.nio.file.Path; // Import nếu có logic xóa file vật lý
import java.nio.file.Paths; // Import nếu có logic xóa file vật lý


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final UserRepository userRepository;
    private final PlanMemberRepository planMemberRepository; // Để kiểm tra thành viên
    private final TaskMapper taskMapper;
    // private final FileUploadService fileUploadService; // Inject nếu cần xóa file vật lý

    @Value("${file.upload-dir}") // Inject đường dẫn upload (nếu cần xóa file)
    private String uploadDir;

    @Override
    public TaskCommentResponse addTaskComment(Long taskId, TaskCommentRequest request, String userEmail) {
        Task task = findTaskById(taskId);
        User author = findUserByEmail(userEmail);
        ensureUserIsMemberOfPlan(author, task.getPlan()); // Kiểm tra user có thuộc plan không

        TaskComment comment = TaskComment.builder()
                .task(task)
                .author(author)
                .content(request.getContent())
                .build();

        TaskComment savedComment = taskCommentRepository.save(comment);
        log.info("User {} added comment {} to task {}", userEmail, savedComment.getId(), taskId);
        return taskMapper.toTaskCommentResponse(savedComment);
    }
    
    @Override
    public TaskCommentResponse updateTaskComment(Long commentId, UpdateTaskCommentRequest request, String userEmail) {
        TaskComment comment = findTaskCommentById(commentId);
        User user = findUserByEmail(userEmail);

        // Kiểm tra quyền: Chỉ tác giả mới được sửa
        if (!comment.getAuthor().getId().equals(user.getId())) {
            log.warn("User {} attempted to update comment {} without permission (not author).", userEmail, commentId);
            throw new AccessDeniedException("Bạn không có quyền sửa bình luận này.");
        }

        // Cập nhật nội dung
        comment.setContent(request.getContent());
        // Trường updatedAt sẽ tự động cập nhật nhờ @UpdateTimestamp

        TaskComment updatedComment = taskCommentRepository.save(comment);
        log.info("User {} updated comment {}", userEmail, commentId);
        return taskMapper.toTaskCommentResponse(updatedComment);
    }

    @Override
    public void deleteTaskComment(Long commentId, String userEmail) {
        TaskComment comment = findTaskCommentById(commentId);
        User user = findUserByEmail(userEmail);
        Task task = comment.getTask();
        Plan plan = task.getPlan();

        boolean isAuthor = comment.getAuthor().getId().equals(user.getId());
        boolean isPlanOwner = isUserPlanOwner(user, plan);

        if (!isAuthor && !isPlanOwner) {
            log.warn("User {} attempted to delete comment {} without permission.", userEmail, commentId);
            throw new AccessDeniedException("Bạn không có quyền xóa bình luận này.");
        }

        taskCommentRepository.delete(comment);
        log.info("User {} deleted comment {}", userEmail, commentId);
    }

    @Override
    public TaskAttachmentResponse addTaskAttachment(Long taskId, FileUploadResponse fileInfo, String userEmail) {
        Task task = findTaskById(taskId);
        User uploader = findUserByEmail(userEmail);
        ensureUserIsMemberOfPlan(uploader, task.getPlan());

        // Tạo entity TaskAttachment từ FileUploadResponse
        TaskAttachment attachment = TaskAttachment.builder()
                .task(task)
                .originalFilename(fileInfo.getOriginalFilename()) // Giờ đã có thể gọi được
                .storedFilename(fileInfo.getStoredFilename())
                .contentType(fileInfo.getContentType())
                .fileSize(fileInfo.getSize())
                .fileUrl(fileInfo.getFileUrl())
                .filePath(Paths.get(uploadDir).resolve(fileInfo.getStoredFilename()).normalize().toAbsolutePath().toString())
                .build();

        TaskAttachment savedAttachment = taskAttachmentRepository.save(attachment);
        log.info("User {} added attachment {} to task {}", userEmail, savedAttachment.getId(), taskId);
        return taskMapper.toTaskAttachmentResponse(savedAttachment);
    }

    @Override
    public void deleteTaskAttachment(Long attachmentId, String userEmail) {
        TaskAttachment attachment = findTaskAttachmentById(attachmentId);
        User user = findUserByEmail(userEmail);
        Task task = attachment.getTask();
        Plan plan = task.getPlan();

        boolean isPlanOwner = isUserPlanOwner(user, plan);

        if (!isPlanOwner) {
             log.warn("User {} attempted to delete attachment {} without permission.", userEmail, attachmentId);
            throw new AccessDeniedException("Chỉ chủ kế hoạch mới có quyền xóa file đính kèm.");
        }

        // (Tùy chọn) Xóa file vật lý khỏi server
        try {
            Path filePath = Paths.get(attachment.getFilePath());
            Files.deleteIfExists(filePath);
            log.info("Deleted physical file: {}", attachment.getFilePath());
        } catch (IOException e) {
            log.error("Failed to delete physical file: {}", attachment.getFilePath(), e);
            // Quyết định xem có nên ném lỗi hay chỉ log ở đây
            // throw new RuntimeException("Could not delete file: " + attachment.getOriginalFilename(), e);
        }

        taskAttachmentRepository.delete(attachment);
        log.info("User {} deleted attachment {}", userEmail, attachmentId);
    }

    // --- Helper Methods ---

    private Task findTaskById(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công việc với ID: " + taskId));
    }

    private TaskComment findTaskCommentById(Long commentId) {
        return taskCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bình luận với ID: " + commentId));
    }

     private TaskAttachment findTaskAttachmentById(Long attachmentId) {
        return taskAttachmentRepository.findById(attachmentId)
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