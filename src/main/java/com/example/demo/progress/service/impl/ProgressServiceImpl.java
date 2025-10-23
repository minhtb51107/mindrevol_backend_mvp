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
// import com.example.demo.progress.dto.response.AttachmentResponse; // Vẫn giữ import này vì mapper cần
import com.example.demo.progress.entity.DailyProgress;
// import com.example.demo.progress.entity.EvidenceAttachment; // Không cần nữa
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
import org.springframework.messaging.simp.SimpMessagingTemplate; // *** THÊM IMPORT ***
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.plan.entity.TaskComment;
import com.example.demo.plan.entity.TaskAttachment;
import com.example.demo.plan.repository.TaskCommentRepository;
import com.example.demo.plan.repository.TaskAttachmentRepository;
import com.example.demo.plan.repository.TaskRepository;
import com.example.demo.progress.dto.request.TaskCheckinUpdateRequest;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*; // Import Map và LinkedHashMap
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProgressServiceImpl implements ProgressService {

    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final PlanMemberRepository planMemberRepository;
    private final DailyProgressRepository dailyProgressRepository;
    private final ProgressMapper progressMapper; // Đã inject từ trước
    private final TaskRepository taskRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final SimpMessagingTemplate messagingTemplate; // *** INJECT SimpMessagingTemplate ***


    @Value("${file.upload-dir}")
    private String uploadDir;

    @Override
    public DailyProgressResponse logOrUpdateDailyProgress(String shareableLink, String userEmail, LogProgressRequest request) {
        Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);

        PlanMember member = planMemberRepository.findByPlanIdAndUserId(plan.getId(), user.getId())
                .orElseThrow(() -> new AccessDeniedException("Bạn không phải là thành viên của kế hoạch này."));

        validateCheckinDate(request.getDate(), plan);

        DailyProgress progress = dailyProgressRepository.findByPlanMemberIdAndDate(member.getId(), request.getDate())
                .orElse(new DailyProgress());

        if (progress.getId() != null && progress.getDate().isBefore(LocalDate.now().minusDays(2))) {
             throw new BadRequestException("Không thể sửa đổi tiến độ cho ngày đã quá cũ.");
        }

        // Cập nhật thông tin chung
        progress.setPlanMember(member);
        progress.setDate(request.getDate());
        progress.setNotes(request.getNotes());
        List<String> validEvidenceLinks = request.getEvidence() == null ? new ArrayList<>() :
                request.getEvidence().stream()
                        .filter(link -> link != null && !link.isBlank())
                        .collect(Collectors.toList());
        progress.setEvidence(validEvidenceLinks);

        // Xử lý completedTaskIds
        Set<Long> planTaskIds = plan.getDailyTasks().stream().map(Task::getId).collect(Collectors.toSet());
        int totalTasks = planTaskIds.size();
        Set<Long> validCompletedTaskIds = new HashSet<>();
        if (request.getCompletedTaskIds() != null) {
            validCompletedTaskIds = request.getCompletedTaskIds().stream()
                .filter(taskId -> taskId != null && planTaskIds.contains(taskId))
                .collect(Collectors.toSet());
        }
        progress.setCompletedTaskIds(validCompletedTaskIds);

        // Xử lý completed
        if (totalTasks > 0) {
            progress.setCompleted(validCompletedTaskIds.size() == totalTasks);
        } else {
            // Nếu không có task, trạng thái completed dựa vào input request
            progress.setCompleted(request.getCompleted() != null && request.getCompleted());
        }
        // Trường hợp user tick hết task nhưng quên tick completed chung
        if (totalTasks > 0 && validCompletedTaskIds.size() == totalTasks) {
             progress.setCompleted(true);
        }

        DailyProgress savedProgress = dailyProgressRepository.save(progress);
        log.info("Saved/Updated DailyProgress ID: {} for user: {} on date: {}", savedProgress.getId(), userEmail, request.getDate());

        // Xử lý taskUpdates (thêm comment/attachment cho task)
        if (request.getTaskUpdates() != null && !request.getTaskUpdates().isEmpty()) {
            Path rootLocation = Paths.get(uploadDir);
            for (TaskCheckinUpdateRequest taskUpdate : request.getTaskUpdates()) {
                Long taskId = taskUpdate.getTaskId();
                Task task = taskRepository.findById(taskId)
                    .filter(t -> t.getPlan().getId().equals(plan.getId()))
                    .orElse(null);

                if (task == null) {
                    log.warn("Skipping task update for non-existent or mismatched task ID: {}", taskId);
                    continue;
                }

                // Xử lý comment
                String commentContent = taskUpdate.getCommentContent();
                if (commentContent != null && !commentContent.isBlank()) {
                    TaskComment taskComment = TaskComment.builder()
                            .task(task).author(user).content(commentContent.trim()).build();
                    taskCommentRepository.save(taskComment);
                    log.debug("Added comment for task ID: {}", taskId);
                    // TODO: Gửi WebSocket update cho Task Comment (sẽ làm sau)
                }

                // Xử lý attachments
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
                         // TODO: Gửi WebSocket update cho Task Attachment (sẽ làm sau)
                    });
                }
            }
        }

        // *** GỬI MESSAGE QUA WEBSOCKET ***
        String destination = "/topic/plan/" + shareableLink + "/progress";
        // Chuyển đổi savedProgress sang DTO tóm tắt để gửi đi
        // Cần truyền userId hiện tại để mapper tính đúng `hasCurrentUserReacted` (mặc dù không dùng ở đây)
        DailyProgressSummaryResponse progressSummary = progressMapper.toDailyProgressSummaryResponse(savedProgress, user.getId());

        // Tạo payload, bao gồm cả thông tin người check-in
        Map<String, Object> payload = Map.of(
            "type", "PROGRESS_UPDATE",
            "date", savedProgress.getDate().toString(), // Gửi date dạng string YYYY-MM-DD
            "memberEmail", userEmail, // Email của người vừa check-in
            "memberFullName", getUserFullName(user), // Tên đầy đủ
            "progressSummary", progressSummary // Dữ liệu tóm tắt tiến độ
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.info("Sent WebSocket update to {} for progress ID {}", destination, savedProgress.getId());
        // *** KẾT THÚC GỬI WEBSOCKET ***

        // Trả về response đầy đủ cho người gọi API ban đầu
        return progressMapper.toDailyProgressResponse(savedProgress, user.getId());
    }

    private void validateCheckinDate(LocalDate checkinDate, Plan plan) {
        LocalDate today = LocalDate.now(); // Lấy ngày hiện tại theo múi giờ server
        // LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")); // Hoặc múi giờ cụ thể nếu cần
        LocalDate yesterday = today.minusDays(1);
        LocalDate planStartDate = plan.getStartDate();
        LocalDate planEndDate = planStartDate.plusDays(plan.getDurationInDays() - 1);

        if (checkinDate.isAfter(today)) {
            throw new BadRequestException("Không thể ghi nhận tiến độ cho một ngày trong tương lai.");
        }

        // Cho phép check-in tối đa 2 ngày trước (hôm nay, hôm qua, hôm kia)
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
        Plan plan = findPlanByShareableLinkWithTasks(shareableLink); // Dùng hàm helper mới
        User user = findUserByEmail(userEmail);

        if (plan.getMembers() == null || plan.getMembers().stream().noneMatch(m -> m.getUser() != null && m.getUser().getId().equals(user.getId()))) {
            throw new AccessDeniedException("Bạn không có quyền xem tiến độ của kế hoạch này.");
        }

        // Lấy tất cả progress của plan này một lần để tối ưu
        List<DailyProgress> allProgressForPlan = dailyProgressRepository.findAll().stream()
                .filter(dp -> dp.getPlanMember() != null && dp.getPlanMember().getPlan() != null && dp.getPlanMember().getPlan().getId().equals(plan.getId()))
                .toList();

        // Nhóm progress theo memberId
        Map<Integer, List<DailyProgress>> progressByMemberId = allProgressForPlan.stream()
                .collect(Collectors.groupingBy(dp -> dp.getPlanMember().getId()));


        List<ProgressDashboardResponse.MemberProgressResponse> membersProgress = plan.getMembers().stream()
                .map(member -> buildMemberProgress(member, plan, user.getId(), progressByMemberId.getOrDefault(member.getId(), Collections.emptyList()))) // Truyền progress của member vào
                .filter(Objects::nonNull) // Lọc bỏ member null (nếu có lỗi dữ liệu)
                .sorted(Comparator.comparing(ProgressDashboardResponse.MemberProgressResponse::getUserFullName)) // Sắp xếp theo tên
                .collect(Collectors.toList());

        return ProgressDashboardResponse.builder()
                .planTitle(plan.getTitle())
                .membersProgress(membersProgress)
                .build();
    }

    // Sửa hàm buildMemberProgress để nhận progress đã fetch sẵn
    private ProgressDashboardResponse.MemberProgressResponse buildMemberProgress(PlanMember member, Plan plan, Integer currentUserId, List<DailyProgress> memberProgressList) {
        if (member == null || member.getUser() == null) return null;

        long completedDays = memberProgressList.stream().filter(DailyProgress::isCompleted).count();
        double completionPercentage = (plan.getDurationInDays() > 0) ? ((double) completedDays / plan.getDurationInDays() * 100) : 0;

        LocalDate planStartDate = plan.getStartDate();
        if (planStartDate == null) return null; // Nên có validate khi tạo plan

        // Tạo map progress theo ngày từ list đã có
        Map<LocalDate, DailyProgress> progressByDate = memberProgressList.stream()
                .collect(Collectors.toMap(DailyProgress::getDate, p -> p));

        // Tạo map dailyStatus cho tất cả các ngày trong plan
        Map<String, DailyProgressSummaryResponse> dailyStatus = IntStream.range(0, plan.getDurationInDays())
            .mapToObj(planStartDate::plusDays)
            .collect(Collectors.toMap(
                LocalDate::toString, // Key là ngày dạng "YYYY-MM-DD"
                date -> {
                    DailyProgress progress = progressByDate.get(date);
                    if (progress == null) {
                        // Trả về DTO rỗng nếu chưa có progress cho ngày đó
                        return DailyProgressSummaryResponse.builder()
                                .id(null).completed(false).notes(null)
                                .attachments(Collections.emptyList()) // Khởi tạo rỗng
                                .comments(Collections.emptyList())
                                .reactions(Collections.emptyList())
                                .completedTaskIds(Collections.emptySet())
                                .build();
                    }
                    // Dùng mapper để chuyển đổi progress hiện có
                    return progressMapper.toDailyProgressSummaryResponse(progress, currentUserId);
                },
                (v1, v2) -> v1, // Merge function (không cần thiết nếu key là duy nhất)
                LinkedHashMap::new // Giữ thứ tự ngày tháng
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
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + email));
    }

    private Plan findPlanByShareableLink(String link) {
        return planRepository.findByShareableLink(link)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kế hoạch với link: " + link));
    }

     // Helper để load cả plan, members, tasks
     private Plan findPlanByShareableLinkWithTasks(String link) {
        return planRepository.findByShareableLink(link)
                .map(plan -> {
                    plan.getMembers().size();
                    plan.getDailyTasks().size();
                    plan.getDailyTasks().sort(Comparator.comparing(Task::getOrder, Comparator.nullsLast(Integer::compareTo))); // Sắp xếp task theo order
                    return plan;
                })
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kế hoạch với link: " + link));
    }

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
}