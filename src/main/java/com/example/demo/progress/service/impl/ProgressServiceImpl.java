package com.example.demo.progress.service.impl;

import com.example.demo.plan.entity.Plan;
import com.example.demo.plan.entity.PlanMember;
import com.example.demo.plan.entity.Task;
import com.example.demo.plan.repository.PlanMemberRepository;
import com.example.demo.plan.repository.PlanRepository;
import com.example.demo.progress.dto.request.LogProgressRequest;
import com.example.demo.progress.dto.response.DailyProgressResponse;
import com.example.demo.progress.dto.response.DailyProgressSummaryResponse;
import com.example.demo.progress.dto.response.ProgressDashboardResponse;
// *** BỎ IMPORT NÀY NẾU KHÔNG CÒN DÙNG evidenceLinks TRONG DailyProgressResponse NỮA ***
// import com.example.demo.progress.dto.response.AttachmentResponse;
import com.example.demo.progress.entity.DailyProgress;
import com.example.demo.progress.entity.EvidenceAttachment;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.plan.entity.TaskComment; // Thêm import TaskComment
import com.example.demo.plan.entity.TaskAttachment; // Thêm import TaskAttachment
import com.example.demo.plan.repository.TaskCommentRepository; // Thêm import
import com.example.demo.plan.repository.TaskAttachmentRepository; // Thêm import
import com.example.demo.plan.repository.TaskRepository; // Thêm import
import com.example.demo.progress.dto.request.TaskCheckinUpdateRequest; // Thêm import

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProgressServiceImpl implements ProgressService {

	// ... (Inject các repository cũ) ...
    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final PlanMemberRepository planMemberRepository;
    private final DailyProgressRepository dailyProgressRepository;
    private final ProgressMapper progressMapper;
    // --- THÊM CÁC REPOSITORY MỚI ---
    private final TaskRepository taskRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    // --- KẾT THÚC THÊM ---

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Override
    public DailyProgressResponse logOrUpdateDailyProgress(String shareableLink, String userEmail, LogProgressRequest request) {
        Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);

        PlanMember member = planMemberRepository.findByPlanIdAndUserId(plan.getId(), user.getId())
                .orElseThrow(() -> new AccessDeniedException("Bạn không phải là thành viên của kế hoạch này."));

        validateCheckinDate(request.getDate(), plan);

        // Tìm hoặc tạo mới DailyProgress (giữ nguyên)
        DailyProgress progress = dailyProgressRepository.findByPlanMemberIdAndDate(member.getId(), request.getDate())
                .orElse(new DailyProgress());

        // Kiểm tra sửa log cũ (giữ nguyên)
        if (progress.getId() != null && progress.getDate().isBefore(LocalDate.now().minusDays(2))) {
             throw new BadRequestException("Không thể sửa đổi tiến độ cho ngày đã quá cũ.");
        }

        // Cập nhật thông tin chung cho DailyProgress
        progress.setPlanMember(member);
        progress.setDate(request.getDate());
        progress.setNotes(request.getNotes()); // Ghi chú chung
        // Xử lý link chung (nếu còn dùng)
        List<String> validEvidenceLinks = request.getEvidence() == null ? new ArrayList<>() :
                request.getEvidence().stream()
                        .filter(link -> link != null && !link.isBlank())
                        .collect(Collectors.toList());
        progress.setEvidence(validEvidenceLinks);

        // --- BỎ PHẦN XỬ LÝ attachments CHUNG ---
        // progress.getAttachments().clear();
        // if (request.getAttachments() != null) { ... } // Bỏ hẳn đoạn này
        // --- KẾT THÚC BỎ ---

        // Xử lý completedTaskIds (giữ nguyên)
        Set<Long> planTaskIds = plan.getDailyTasks().stream().map(Task::getId).collect(Collectors.toSet());
        int totalTasks = planTaskIds.size();
        Set<Long> validCompletedTaskIds = new HashSet<>();
        if (request.getCompletedTaskIds() != null) {
            validCompletedTaskIds = request.getCompletedTaskIds().stream()
                .filter(taskId -> taskId != null && planTaskIds.contains(taskId))
                .collect(Collectors.toSet());
        }
        progress.setCompletedTaskIds(validCompletedTaskIds);

        // Xử lý completed (giữ nguyên)
        if (totalTasks > 0) {
            progress.setCompleted(validCompletedTaskIds.size() == totalTasks);
        } else {
            progress.setCompleted(request.getCompleted());
        }
        if (totalTasks > 0 && validCompletedTaskIds.size() == totalTasks && !request.getCompleted()) {
             progress.setCompleted(true);
        }

        // --- LƯU DailyProgress TRƯỚC KHI XỬ LÝ TASK UPDATES ---
        // Cần lưu trước để có ID progress nếu là tạo mới (mặc dù hiện tại TaskComment/Attachment không link trực tiếp về Progress)
        // Hoặc có thể không cần lưu trước nếu không có liên kết trực tiếp
        DailyProgress savedProgress = dailyProgressRepository.save(progress);
        log.info("Saved/Updated DailyProgress ID: {}", savedProgress.getId());

        // --- THÊM LOGIC XỬ LÝ taskUpdates ---
        if (request.getTaskUpdates() != null && !request.getTaskUpdates().isEmpty()) {
            Path rootLocation = Paths.get(uploadDir);

            for (TaskCheckinUpdateRequest taskUpdate : request.getTaskUpdates()) {
                Long taskId = taskUpdate.getTaskId();
                // Tìm Task tương ứng (nên fetch cả Plan để đảm bảo Task thuộc đúng Plan)
                Task task = taskRepository.findById(taskId)
                    .filter(t -> t.getPlan().getId().equals(plan.getId())) // Đảm bảo task thuộc plan này
                    .orElse(null); // Hoặc ném lỗi nếu muốn chặt chẽ hơn

                if (task == null) {
                    log.warn("Skipping task update for non-existent or mismatched task ID: {}", taskId);
                    continue; // Bỏ qua nếu task ID không hợp lệ
                }

                // 1. Xử lý comment cho task (nếu có)
                String commentContent = taskUpdate.getCommentContent();
                if (commentContent != null && !commentContent.isBlank()) {
                    TaskComment taskComment = TaskComment.builder()
                            .task(task)
                            .author(user) // Người check-in là tác giả comment
                            .content(commentContent.trim())
                            .build();
                    taskCommentRepository.save(taskComment);
                    log.debug("Added comment for task ID: {}", taskId);
                }

                // 2. Xử lý attachments cho task (nếu có)
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
                        log.debug("Added attachment {} for task ID: {}", attReq.getStoredFilename(), taskId);
                    });
                }
            }
        }
        // --- KẾT THÚC LOGIC XỬ LÝ taskUpdates ---

        // Trả về response (có thể cần fetch lại progress để lấy comment/attachment mới nhất nếu mapper không tự load)
        // Hiện tại mapper sẽ lấy từ entity đã save, nhưng comment/attachment thuộc Task, không thuộc Progress
        // => Cần cập nhật mapper hoặc fetch lại Plan/Task để response đầy đủ
        // Tạm thời trả về response dựa trên savedProgress (chưa có task comment/attachment)
        // Để có dữ liệu đầy đủ, cần fetch lại Plan sau khi lưu, khá tốn kém.
        // Giải pháp tốt hơn: Cập nhật PlanMapper/TaskMapper để nhận thêm currentUserId và fetch comment/attachment nếu cần?
        // Hoặc: Frontend tự fetch lại PlanDetail sau khi check-in thành công. -> Chọn cách này cho đơn giản.

        return progressMapper.toDailyProgressResponse(savedProgress, user.getId());
    }

    private void validateCheckinDate(LocalDate checkinDate, Plan plan) {
        // ... giữ nguyên ...
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate planStartDate = plan.getStartDate();
        LocalDate planEndDate = planStartDate.plusDays(plan.getDurationInDays() - 1);

        if (checkinDate.isAfter(today)) {
            throw new BadRequestException("Không thể ghi nhận tiến độ cho một ngày trong tương lai.");
        }

        if (!checkinDate.isEqual(today) && !checkinDate.isEqual(yesterday)) {
             throw new BadRequestException("Bạn chỉ có thể ghi nhận tiến độ cho hôm nay hoặc hôm qua.");
        }

        if (checkinDate.isBefore(planStartDate) || checkinDate.isAfter(planEndDate)) {
            throw new BadRequestException("Ngày check-in không nằm trong thời gian diễn ra kế hoạch.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ProgressDashboardResponse getProgressDashboard(String shareableLink, String userEmail) {
        // ... giữ nguyên ...
        Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);

        if (plan.getMembers() == null || plan.getMembers().stream().noneMatch(m -> m.getUser() != null && m.getUser().getId().equals(user.getId()))) {
            throw new AccessDeniedException("Bạn không có quyền xem tiến độ của kế hoạch này.");
        }

        List<ProgressDashboardResponse.MemberProgressResponse> membersProgress = plan.getMembers().stream()
                .map(member -> buildMemberProgress(member, plan, user.getId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return ProgressDashboardResponse.builder()
                .planTitle(plan.getTitle())
                .membersProgress(membersProgress)
                .build();
    }

    private ProgressDashboardResponse.MemberProgressResponse buildMemberProgress(PlanMember member, Plan plan, Integer currentUserId) {
        if (member == null || member.getUser() == null) return null;

        // Tối ưu: Chỉ fetch các progress cần thiết thay vì load hết list? (tùy độ phức tạp)
        List<DailyProgress> allProgress = member.getDailyProgressList() == null ? Collections.emptyList() : member.getDailyProgressList();

        long completedDays = allProgress.stream().filter(DailyProgress::isCompleted).count();
        double completionPercentage = (plan.getDurationInDays() > 0) ? ((double) completedDays / plan.getDurationInDays() * 100) : 0;

        LocalDate planStartDate = plan.getStartDate();
        if (planStartDate == null) return null;

        Map<LocalDate, DailyProgress> progressByDate = allProgress.stream()
                .collect(Collectors.toMap(DailyProgress::getDate, p -> p));

        Map<String, DailyProgressSummaryResponse> dailyStatus = IntStream.range(0, plan.getDurationInDays())
            .mapToObj(planStartDate::plusDays)
            .collect(Collectors.toMap(
                LocalDate::toString,
                date -> {
                    DailyProgress progress = progressByDate.get(date);

                    if (progress == null) {
                        // --- SỬA LỖI Ở ĐÂY ---
                        // Bỏ .evidence(...) vì DailyProgressSummaryResponse không còn trường evidence<String>
                        return DailyProgressSummaryResponse.builder()
                                .id(null)
                                .completed(false)
                                .notes(null)
                                // .evidence(Collections.emptyList()) // Bỏ dòng này
                                .attachments(Collections.emptyList()) // Khởi tạo attachments rỗng
                                .comments(Collections.emptyList())
                                .reactions(Collections.emptyList())
                                .completedTaskIds(Collections.emptySet())
                                .build();
                        // --- KẾT THÚC SỬA LỖI ---
                    }

                    // Gọi mapper để chuyển đổi (mapper đã được cập nhật để trả về attachments)
                    return progressMapper.toDailyProgressSummaryResponse(progress, currentUserId);
                },
                (v1, v2) -> v1, // identity merge function
                LinkedHashMap::new // preserve insertion order
            ));

        return ProgressDashboardResponse.MemberProgressResponse.builder()
                .userEmail(member.getUser().getEmail())
                .userFullName(getUserFullName(member.getUser()))
                .completedDays((int) completedDays)
                .completionPercentage(completionPercentage)
                .dailyStatus(dailyStatus)
                .build();
    }

    private User findUserByEmail(String email) {
        // ... giữ nguyên ...
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + email));
    }

    private Plan findPlanByShareableLink(String link) {
        // ... giữ nguyên ...
        return planRepository.findByShareableLink(link)
                .map(plan -> {
                    plan.getDailyTasks().size(); // Trigger lazy load tasks
                    return plan;
                })
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kế hoạch với link: " + link));
    }

    private String getUserFullName(User user) {
        // ... giữ nguyên ...
        if (user == null) return "N/A";
        if (user.getCustomer() != null && user.getCustomer().getFullname() != null) {
            return user.getCustomer().getFullname();
        }
        if (user.getEmployee() != null && user.getEmployee().getFullname() != null) {
            return user.getEmployee().getFullname();
        }
        return user.getEmail();
    }
}