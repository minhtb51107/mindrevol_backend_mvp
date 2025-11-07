package com.example.demo.plan.service.impl;

import com.example.demo.feed.entity.FeedEventType;
import com.example.demo.feed.service.FeedService;
import com.example.demo.plan.dto.request.CreatePlanRequest;
import com.example.demo.plan.dto.request.CreatePlanWithScheduleRequest;
import com.example.demo.plan.dto.request.ManageTaskRequest;
import com.example.demo.plan.dto.request.ReorderTasksRequest;
import com.example.demo.plan.dto.request.TransferOwnershipRequest;
// THÊM IMPORT NÀY
import com.example.demo.plan.dto.request.UpdatePlanDetailsRequest;
import com.example.demo.plan.dto.request.UpdatePlanRequest;
import com.example.demo.plan.dto.response.PlanDetailResponse;
import com.example.demo.plan.dto.response.PlanPublicResponse;
import com.example.demo.plan.dto.response.PlanSummaryResponse;
import com.example.demo.plan.dto.response.TaskResponse;
import com.example.demo.plan.entity.MemberRole;
import com.example.demo.plan.entity.Plan;
import com.example.demo.plan.entity.PlanMember;
import com.example.demo.plan.entity.PlanStatus;
import com.example.demo.plan.entity.Task;
import com.example.demo.plan.mapper.PlanMapper;
import com.example.demo.plan.mapper.TaskMapper;
import com.example.demo.plan.repository.PlanMemberRepository;
import com.example.demo.plan.repository.PlanRepository;
import com.example.demo.plan.repository.TaskRepository;
import com.example.demo.plan.service.PlanService;
import com.example.demo.shared.exception.BadRequestException;
import com.example.demo.shared.exception.ResourceNotFoundException;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.demo.progress.repository.CheckInEventRepository;
import com.example.demo.progress.repository.CheckInTaskRepository;
import com.example.demo.feed.repository.FeedEventRepository; // <-- THÊM IMPORT NÀY
import com.example.demo.community.repository.ProgressCommentRepository; // <-- THÊM IMPORT NÀY
import com.example.demo.community.repository.ProgressReactionRepository;
import com.example.demo.progress.repository.DailyProgressRepository; // <-- THÊM IMPORT NÀY
import com.example.demo.progress.entity.DailyProgress; // <-- THÊM IMPORT NÀY
import com.example.demo.progress.entity.checkin.CheckInEvent;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PlanServiceImpl implements PlanService {

    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final PlanMapper planMapper;
    private final PlanMemberRepository planMemberRepository;
    private final TaskRepository taskRepository;
    private final TaskMapper taskMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final FeedService feedService;
    private final CheckInEventRepository checkInEventRepository;
    private final CheckInTaskRepository checkInTaskRepository;
    private final FeedEventRepository feedEventRepository; // <-- THÊM REPO NÀY
    private final ProgressCommentRepository progressCommentRepository;
    private final DailyProgressRepository dailyProgressRepository;
    private final ProgressReactionRepository progressReactionRepository;
    
    @Override
    public PlanDetailResponse createPlan(CreatePlanRequest request, String creatorEmail) {
        User creator = findUserByEmail(creatorEmail);

        if (request.getStartDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("Ngày bắt đầu không thể là một ngày trong quá khứ.");
        }

        PlanStatus initialStatus = request.getStartDate().isAfter(LocalDate.now()) ? PlanStatus.ACTIVE : PlanStatus.ACTIVE;

        Plan newPlan = Plan.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .durationInDays(request.getDurationInDays())
                .motivation(request.getMotivation())
                .dailyGoal(request.getDailyGoal())
                .startDate(request.getStartDate())
                .creator(creator)
                .members(new ArrayList<>())
                .dailyTasks(new ArrayList<>()) 
                .status(initialStatus)
                .build();

        // --- BẮT ĐẦU LOGIC MỚI ---
        List<CreatePlanRequest.TaskRequest> taskReqs = request.getDailyTasks();

        if (taskReqs != null && !taskReqs.isEmpty()) {

            if (request.isRepeatTasks()) {
                // LUỒNG 1: Lặp lại task cho mọi ngày (Template Flow)
                log.info("Repeating {} tasks for {} days...", taskReqs.size(), newPlan.getDurationInDays());

                for (int day = 0; day < newPlan.getDurationInDays(); day++) {
                    LocalDate currentDate = newPlan.getStartDate().plusDays(day);

                    IntStream.range(0, taskReqs.size())
                        .mapToObj(i -> {
                            CreatePlanRequest.TaskRequest taskReq = taskReqs.get(i);
                            return Task.builder()
                                    .description(taskReq.getDescription())
                                    .deadlineTime(taskReq.getDeadlineTime()) // Lấy từ request
                                    .order(i)
                                    .plan(newPlan)
                                    .taskDate(currentDate) // <-- Gán cho ngày hiện tại trong vòng lặp
                                    .build();
                        })
                        .forEach(newPlan::addTask);
                }

            } else {
                // LUỒNG 2: Chỉ thêm task cho Ngày 1 (Logic cũ của bạn)
                log.info("Adding {} tasks for start date only...", taskReqs.size());

                IntStream.range(0, taskReqs.size())
                    .mapToObj(i -> {
                        CreatePlanRequest.TaskRequest taskReq = taskReqs.get(i);
                        return Task.builder()
                                .description(taskReq.getDescription())
                                .deadlineTime(taskReq.getDeadlineTime()) // Lấy từ request
                                .order(i)
                                .plan(newPlan)
                                .taskDate(newPlan.getStartDate()) // <-- Chỉ gán cho ngày bắt đầu
                                .build();
                    })
                    .forEach(newPlan::addTask);
            }
        }
        // --- KẾT THÚC LOGIC MỚI ---

        // Thêm người tạo làm chủ sở hữu (giữ nguyên)
        PlanMember creatorAsMember = PlanMember.builder()
                .plan(newPlan)
                .user(creator)
                .role(MemberRole.OWNER)
                .build();
        newPlan.getMembers().add(creatorAsMember);

        Plan savedPlan = planRepository.save(newPlan);
        log.info("Plan created with ID: {} and shareableLink: {}", savedPlan.getId(), savedPlan.getShareableLink());

        return planMapper.toPlanDetailResponse(savedPlan);
    }
    
    @Override
    public PlanDetailResponse createPlanWithSchedule(CreatePlanWithScheduleRequest request, String creatorEmail) {
        User creator = findUserByEmail(creatorEmail);

        PlanStatus initialStatus = request.getStartDate().isAfter(LocalDate.now()) ? PlanStatus.ACTIVE : PlanStatus.ACTIVE;

        Plan newPlan = Plan.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .durationInDays(request.getDurationInDays())
                .motivation(request.getMotivation())
                .dailyGoal(request.getDailyGoal())
                .startDate(request.getStartDate())
                .creator(creator)
                .members(new ArrayList<>())
                .dailyTasks(new ArrayList<>()) 
                .status(initialStatus)
                .build();

        // Dùng Map để theo dõi 'order' cho mỗi ngày
        Map<LocalDate, Integer> orderTracker = new HashMap<>();

        if (request.getTasks() != null) {
            log.info("Creating plan with {} scheduled tasks...", request.getTasks().size());

            for (CreatePlanWithScheduleRequest.ScheduledTaskRequest taskReq : request.getTasks()) {
                LocalDate taskDate = taskReq.getTaskDate();

                // Lấy và tăng 'order' cho ngày này
                int order = orderTracker.getOrDefault(taskDate, 0);
                orderTracker.put(taskDate, order + 1);

                Task newTask = Task.builder()
                        .description(taskReq.getDescription())
                        .deadlineTime(taskReq.getDeadlineTime())
                        .taskDate(taskDate)
                        .order(order)
                        .plan(newPlan)
                        .build();

                newPlan.addTask(newTask);
            }
        }

        // Thêm người tạo làm chủ sở hữu
        PlanMember creatorAsMember = PlanMember.builder()
                .plan(newPlan)
                .user(creator)
                .role(MemberRole.OWNER)
                .build();
        newPlan.getMembers().add(creatorAsMember);

        // Lưu Plan (cascade lưu tất cả task mới)
        Plan savedPlan = planRepository.save(newPlan);
        log.info("Plan (with schedule) created with ID: {}", savedPlan.getId());

        return planMapper.toPlanDetailResponse(savedPlan);
    }
    
    @Override
    public void nudgeMember(String shareableLink, Integer targetUserId, String nudgerEmail) {
        Plan plan = findPlanByShareableLink(shareableLink); // Chỉ thúc giục trong plan ACTIVE
        if (plan.getStatus() != PlanStatus.ACTIVE) {
             throw new BadRequestException("Chỉ có thể thúc giục thành viên trong kế hoạch đang hoạt động.");
        }
        
        User nudger = findUserByEmail(nudgerEmail);

        // Không thể tự thúc giục chính mình
        if (nudger.getId().equals(targetUserId)) {
            throw new BadRequestException("Bạn không thể tự thúc giục chính mình! Hãy tự giác nhé ^^");
        }

        // Tìm thành viên mục tiêu trong plan
        PlanMember targetMember = plan.getMembers().stream()
                .filter(m -> m.getUser().getId().equals(targetUserId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Thành viên mục tiêu không tồn tại trong kế hoạch này."));

        // Gửi thông báo (Giả định bạn đã có hàm createNotification hoặc tương tự trong NotificationService)
        // Bạn cần điều chỉnh tên hàm dưới đây cho khớp với NotificationService của bạn
        String title = "🔔 Lời nhắc từ đồng đội";
        String message = String.format("%s vừa thúc giục bạn trong kế hoạch '%s'. Cố lên nào!",
                getUserFullName(nudger), plan.getTitle());
        String link = "/plan/" + shareableLink; // Link để user click vào xem plan

        // VÍ DỤ GỌI HAM - HÃY SỬA LẠI CHO ĐÚNG VỚI SERVICE CỦA BẠN
        // notificationService.send(targetMember.getUser(), title, message, link);
        log.info("Nudge sent from {} to user ID {} in plan {}", nudgerEmail, targetUserId, shareableLink);
    }

    @Override
    public PlanDetailResponse joinPlan(String shareableLink, String userEmail) {
        // SỬA: Dùng findPlanByShareableLink (an toàn, user không thể join plan đã ARCHIVED)
        Plan plan = findPlanByShareableLink(shareableLink); 
        User user = findUserByEmail(userEmail);

        // Kiểm tra xem user đã là thành viên chưa
        if (isUserMemberOfPlan(plan, user.getId())) {
            throw new BadRequestException("Bạn đã tham gia kế hoạch này rồi.");
        }
        
        // SỬA: Kiểm tra plan có đang ACTIVE không (nếu bạn có status UPCOMING/COMPLETED)
        if (plan.getStatus() != PlanStatus.ACTIVE) {
             throw new BadRequestException("Kế hoạch này không hoạt động. Không thể tham gia.");
        }

        // Tạo thành viên mới
        PlanMember newMember = PlanMember.builder()
                .plan(plan)
                .user(user)
                .role(MemberRole.MEMBER) // Vai trò mặc định là MEMBER
                .build();
        plan.getMembers().add(newMember); // Thêm vào danh sách của Plan

        // Lưu lại Plan (cascade lưu cả member mới)
        Plan updatedPlan = planRepository.save(plan);

        // Gửi Feed Event JOIN_PLAN
        feedService.createAndPublishFeedEvent(FeedEventType.JOIN_PLAN, user, plan, null);

        // Gửi WebSocket thông báo có thành viên mới
        String destination = "/topic/plan/" + shareableLink + "/details";
        // Lấy lại member vừa lưu để có ID (nếu cần)
        PlanMember savedNewMember = updatedPlan.getMembers().stream()
                                            .filter(m -> m.getUser().getId().equals(user.getId()))
                                            .findFirst().orElse(newMember); // Fallback về newMember nếu không tìm thấy ngay
        PlanDetailResponse.PlanMemberResponse memberResponse = planMapper.toPlanMemberResponse(savedNewMember);
        Map<String, Object> payload = Map.of(
            "type", "MEMBER_JOINED",
            "member", memberResponse // Gửi thông tin thành viên mới
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.info("User {} joined plan {}. Sent WebSocket update to {}", userEmail, shareableLink, destination);

        // Trả về chi tiết Plan đã cập nhật
        return planMapper.toPlanDetailResponse(updatedPlan);
    }

    @Override
    @Transactional(readOnly = true)
    public Object getPlanDetails(String shareableLink, String userEmail) {
        // (SỬA) Dùng hàm helper mới để có thể tìm thấy plan đã ARCHIVED
        Plan plan = findPlanRegardlessOfStatus(shareableLink);
        User user = findUserByEmail(userEmail);

        // Nếu là thành viên, trả về chi tiết đầy đủ (PlanDetailResponse)
        if (isUserMemberOfPlan(plan, user.getId())) {
            // (SỬA) Kể cả khi là thành viên, nếu plan đã lưu trữ,
            // User vẫn có thể xem (nhưng UI sẽ hiển thị là "Đã lưu trữ")
            return planMapper.toPlanDetailResponse(plan);
        } else {
            // (SỬA) Nếu không phải thành viên VÀ plan đã lưu trữ, ném lỗi 403/404
            if (plan.getStatus() == PlanStatus.ARCHIVED) {
                 throw new ResourceNotFoundException("Không tìm thấy kế hoạch với link: " + shareableLink);
            }
            // Nếu không phải thành viên, trả về thông tin công khai (PlanPublicResponse)
            return planMapper.toPlanPublicResponse(plan);
        }
    }

    @Override
    public PlanDetailResponse updatePlan(String shareableLink, UpdatePlanRequest request, String userEmail) {
        // SỬA: Dùng hàm helper mới để có thể tìm thấy plan bất kể trạng thái
        Plan plan = findPlanRegardlessOfStatus(shareableLink);
        User user = findUserByEmail(userEmail);
        ensureUserIsOwner(plan, user.getId()); // Đảm bảo là chủ sở hữu
        
        // THÊM: Cấm sửa plan đã lưu trữ
        ensurePlanIsNotArchived(plan);

        // Kiểm tra xem thông tin cơ bản có thay đổi không
        boolean infoChanged = !Objects.equals(plan.getTitle(), request.getTitle()) ||
                              !Objects.equals(plan.getDescription(), request.getDescription()) ||
                              plan.getDurationInDays() != request.getDurationInDays() ||
                              !Objects.equals(plan.getDailyGoal(), request.getDailyGoal());
        PlanStatus statusBeforeUpdate = plan.getStatus(); 

        // Cập nhật thông tin cơ bản
        plan.setTitle(request.getTitle());
        plan.setDescription(request.getDescription());
        plan.setDurationInDays(request.getDurationInDays());
        plan.setDailyGoal(request.getDailyGoal());
        // startDate không cho phép cập nhật trong logic này

        // Xử lý cập nhật danh sách task ban đầu (chỉ ảnh hưởng ngày startDate)
        // -> Xóa các task cũ của ngày startDate
        List<Task> oldTasksOnStartDate = taskRepository.findAllByPlanIdAndTaskDate(plan.getId().longValue(), plan.getStartDate());
        if (!oldTasksOnStartDate.isEmpty()) {
            taskRepository.deleteAll(oldTasksOnStartDate); // Xóa khỏi DB
            plan.getDailyTasks().removeAll(oldTasksOnStartDate); 
            log.info("Removed {} old tasks from start date {} during plan update.", oldTasksOnStartDate.size(), plan.getStartDate());
        }

        // Thêm các task mới (nếu có) cho ngày startDate
        if (request.getDailyTasks() != null) {
             IntStream.range(0, request.getDailyTasks().size())
                 .mapToObj(i -> {
                     UpdatePlanRequest.TaskRequest taskReq = request.getDailyTasks().get(i);
                     return Task.builder()
                             .description(taskReq.getDescription())
                             .deadlineTime(taskReq.getDeadlineTime())
                             .order(i)
                             .plan(plan)
                             .taskDate(plan.getStartDate()) // Luôn gán cho ngày bắt đầu
                             .build();
                 })
                 .forEach(plan::addTask); 
              log.info("Added {} new tasks to start date {} during plan update.", request.getDailyTasks().size(), plan.getStartDate());
        }

        // Lưu lại Plan (cascade lưu task mới nếu có)
        Plan updatedPlan = planRepository.save(plan);

        // Gửi WebSocket nếu thông tin cơ bản thay đổi
        if (infoChanged) {
            String destination = "/topic/plan/" + shareableLink + "/details";
             Map<String, Object> payload = Map.of(
                "type", "PLAN_INFO_UPDATED",
                "title", updatedPlan.getTitle(),
                "description", updatedPlan.getDescription(),
                "durationInDays", updatedPlan.getDurationInDays(),
                "dailyGoal", updatedPlan.getDailyGoal(),
                "startDate", updatedPlan.getStartDate().toString(), // Gửi cả ngày bắt đầu/kết thúc mới
                "endDate", updatedPlan.getStartDate().plusDays(updatedPlan.getDurationInDays() - 1).toString()
            );
            messagingTemplate.convertAndSend(destination, payload);
             log.info("Plan {} info updated. Sent WebSocket update to {}", shareableLink, destination);
        }

        // Kiểm tra xem plan có vừa mới hoàn thành không
        LocalDate endDate = updatedPlan.getStartDate().plusDays(updatedPlan.getDurationInDays() - 1);
        boolean justCompleted = statusBeforeUpdate == PlanStatus.ACTIVE &&
                                LocalDate.now().isAfter(endDate); 
        
        if (justCompleted && statusBeforeUpdate != PlanStatus.COMPLETED) {
            log.info("Plan {} is marked as COMPLETED based on end date.", shareableLink);
            updatedPlan.setStatus(PlanStatus.COMPLETED); // SỬA: Cập nhật status
            planRepository.save(updatedPlan); // Lưu lại
            
            Map<String, Object> details = Map.of("memberCount", updatedPlan.getMembers().size());
            feedService.createAndPublishFeedEvent(FeedEventType.PLAN_COMPLETE, null, updatedPlan, details);
        }

        return planMapper.toPlanDetailResponse(updatedPlan);
    }
    
    // --- THÊM PHƯƠNG THỨC MỚI ---
    @Override
    @Transactional
    public PlanDetailResponse updatePlanDetails(String shareableLink, UpdatePlanDetailsRequest request, String userEmail) {
        Plan plan = findPlanRegardlessOfStatus(shareableLink); // Owner có thể sửa plan (trừ plan archived)
        User user = findUserByEmail(userEmail);
        ensureUserIsOwner(plan, user.getId());

        // Cấm sửa plan đã lưu trữ
        ensurePlanIsNotArchived(plan);

        plan.setTitle(request.getTitle());
        plan.setDescription(request.getDescription());
        plan.setMotivation(request.getMotivation()); // <-- THÊM DÒNG NÀY
        plan.setDailyGoal(request.getDailyGoal());

        Plan savedPlan = planRepository.save(plan);
        PlanDetailResponse response = planMapper.toPlanDetailResponse(savedPlan);

        // Gửi WebSocket thông báo
        String destination = "/topic/plan/" + shareableLink + "/details";
         Map<String, Object> payload = Map.of(
            "type", "PLAN_INFO_UPDATED",
            "title", response.getTitle(),
            "description", response.getDescription(),
            "dailyGoal", response.getDailyGoal()
            // Không gửi các trường không thay đổi
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.info("User {} updated plan details for {}. Sent WebSocket update.", userEmail, shareableLink);

        return response;
    }
    // --- KẾT THÚC THÊM MỚI ---


    @Override
    public void leavePlan(String shareableLink, String userEmail) {
        // SỬA: Dùng helper mới, cho phép user rời cả plan đã archived
        Plan plan = findPlanRegardlessOfStatus(shareableLink);
        User user = findUserByEmail(userEmail);

        // Tìm thành viên tương ứng
        PlanMember member = plan.getMembers().stream()
                .filter(m -> m.getUser() != null && m.getUser().getId().equals(user.getId()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Bạn không phải là thành viên của kế hoạch này."));

        // Chủ sở hữu không được rời plan
        if (member.getRole() == MemberRole.OWNER) {
             throw new BadRequestException("Chủ sở hữu không thể rời khỏi kế hoạch. Bạn cần phải xóa hoặc chuyển quyền sở hữu kế hoạch.");
        }

        // Xóa thành viên
        plan.getMembers().remove(member); // Xóa khỏi collection của Plan
        planMemberRepository.delete(member); // Xóa khỏi DB
         log.info("User {} left plan {}", userEmail, shareableLink);
         
        // Gửi WebSocket cho các thành viên còn lại
        String destination = "/topic/plan/" + shareableLink + "/details";
        Map<String, Object> payload = Map.of(
            "type", "MEMBER_LEFT",
            "userId", user.getId()
        );
        messagingTemplate.convertAndSend(destination, payload);
    }

    // --- XÓA PHƯƠNG THỨC NÀY ---
    /*
    @Override
    public void deletePlan(String shareableLink, String userEmail) {
         Plan plan = findPlanByShareableLink(shareableLink); // SẼ BỊ LỖI NẾU DÙNG @Where
        User user = findUserByEmail(userEmail);
        ensureUserIsOwner(plan, user.getId()); // Đảm bảo là chủ sở hữu
        planRepository.delete(plan); // Xóa plan (cascade xóa members, tasks, etc.)
         log.info("User {} deleted plan {}", userEmail, shareableLink);
    }
    */

    @Override
    @Transactional(readOnly = true)
    public List<PlanSummaryResponse> getMyPlans(String userEmail, String searchTerm) {
         User user = findUserByEmail(userEmail);
         // Lấy tất cả PlanMember của user, fetch kèm Plan
         // Query này KHÔNG bị ảnh hưởng bởi @Where vì nó truy vấn PlanMember
        List<PlanMember> planMembers = planMemberRepository.findByUserIdWithPlan(user.getId());

        Stream<PlanMember> filteredStream = planMembers.stream();
                // THÊM BỘ LỌC: Lọc bỏ các plan đã ARCHIVED ở tầng service
                //.filter(pm -> pm.getPlan() != null && pm.getPlan().getStatus() != PlanStatus.ARCHIVED);

        // Lọc theo searchTerm nếu có
        if (searchTerm != null && !searchTerm.isBlank()) {
            String lowerCaseSearchTerm = searchTerm.toLowerCase().trim();
            filteredStream = filteredStream.filter(pm -> pm.getPlan().getTitle() != null &&
                                                        pm.getPlan().getTitle().toLowerCase().contains(lowerCaseSearchTerm));
        }

        // Map sang DTO Summary, lọc bỏ null, sắp xếp theo ngày bắt đầu giảm dần
        return filteredStream
                .map(planMapper::toPlanSummaryResponse) // Sử dụng mapper
                .filter(Objects::nonNull) // Lọc bỏ kết quả null từ mapper (nếu plan bị lỗi)
                .sorted(Comparator.comparing(PlanSummaryResponse::getStartDate, Comparator.nullsLast(Comparator.reverseOrder()))) // Sort by start date desc
                .collect(Collectors.toList());
    }

    // --- Task Management Methods (CẬP NHẬT KIỂM TRA ARCHIVED) ---

    @Override
    public TaskResponse addTaskToPlan(String shareableLink, ManageTaskRequest request, String userEmail) {
        Plan plan = findPlanRegardlessOfStatus(shareableLink); // SỬA
        User user = findUserByEmail(userEmail);
        ensureUserIsOwner(plan, user.getId()); 
        
        ensurePlanIsNotArchived(plan); // THÊM

        // (Logic còn lại giữ nguyên)
        if (request.getTaskDate() == null) {
            throw new BadRequestException("Ngày của công việc (taskDate) là bắt buộc.");
        }
        LocalDate planEndDate = plan.getStartDate().plusDays(plan.getDurationInDays() - 1);
        if (request.getTaskDate().isBefore(plan.getStartDate()) || request.getTaskDate().isAfter(planEndDate)) {
             throw new BadRequestException("Ngày của công việc phải nằm trong thời gian của kế hoạch ("
                + plan.getStartDate() + " đến " + planEndDate + ").");
        }
        int nextOrder = taskRepository.findMaxOrderByPlanIdAndTaskDate(plan.getId().longValue(), request.getTaskDate())
                           .map(maxOrder -> maxOrder + 1) 
                           .orElse(0); 
        Task newTask = Task.builder()
                .description(request.getDescription())
                .deadlineTime(request.getDeadlineTime())
                .order(nextOrder)
                .plan(plan) 
                .taskDate(request.getTaskDate()) 
                .build();
        Task savedTask = taskRepository.save(newTask);
        TaskResponse taskResponse = taskMapper.toTaskResponse(savedTask);
        String destination = "/topic/plan/" + shareableLink + "/tasks";
        Map<String, Object> payload = Map.of(
            "type", "NEW_TASK",
            "taskDate", savedTask.getTaskDate().toString(), 
            "task", taskResponse 
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.info("Added task {} (date: {}) to plan {}. Sent WebSocket update to {}", savedTask.getId(), savedTask.getTaskDate(), shareableLink, destination);
        return taskResponse;
    }

    @Override
    public TaskResponse updateTaskInPlan(String shareableLink, Long taskId, ManageTaskRequest request, String userEmail) {
        Plan plan = findPlanRegardlessOfStatus(shareableLink); // SỬA
        User user = findUserByEmail(userEmail);
        ensureUserIsOwner(plan, user.getId());
        
        ensurePlanIsNotArchived(plan); // THÊM

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công việc với ID: " + taskId));
        if (!task.getPlan().getId().equals(plan.getId())) {
             throw new AccessDeniedException("Công việc ID " + taskId + " không thuộc kế hoạch " + shareableLink);
        }

        // (Logic còn lại giữ nguyên)
        LocalDate originalDate = task.getTaskDate(); 
        boolean dateChanged = false;
        task.setDescription(request.getDescription());
        task.setDeadlineTime(request.getDeadlineTime());
        if (request.getTaskDate() != null && !request.getTaskDate().equals(originalDate)) {
            LocalDate planEndDate = plan.getStartDate().plusDays(plan.getDurationInDays() - 1);
             if (request.getTaskDate().isBefore(plan.getStartDate()) || request.getTaskDate().isAfter(planEndDate)) {
                 throw new BadRequestException("Ngày chuyển đến ("+ request.getTaskDate() +") phải nằm trong thời gian của kế hoạch.");
             }
            int nextOrderInNewDate = taskRepository.findMaxOrderByPlanIdAndTaskDate(plan.getId().longValue(), request.getTaskDate())
                           .map(maxOrder -> maxOrder + 1)
                           .orElse(0);
            task.setTaskDate(request.getTaskDate());
            task.setOrder(nextOrderInNewDate);
            dateChanged = true;
            log.info("Task {} moved from {} to {} with new order {}", taskId, originalDate, task.getTaskDate(), task.getOrder());
        }
        Task updatedTask = taskRepository.save(task);
        TaskResponse taskResponse = taskMapper.toTaskResponse(updatedTask);
        String destination = "/topic/plan/" + shareableLink + "/tasks";
        Map<String, Object> payload = new HashMap<>(); 
        payload.put("type", dateChanged ? "MOVE_TASK" : "UPDATE_TASK");
        payload.put("taskDate", updatedTask.getTaskDate().toString()); 
        payload.put("task", taskResponse);
        if (dateChanged) {
             payload.put("originalTaskDate", originalDate.toString()); 
        }
        messagingTemplate.convertAndSend(destination, payload);
        log.info("Updated task {} in plan {}. Sent WebSocket update ({}) to {}", taskId, shareableLink, payload.get("type"), destination);
         if (dateChanged) {
             reorderTasksAfterRemoval(plan.getId().longValue(), originalDate, task.getOrder());
         }
        return taskResponse;
    }

    @Override
    public void deleteTaskFromPlan(String shareableLink, Long taskId, String userEmail) {
        Plan plan = findPlanRegardlessOfStatus(shareableLink); // SỬA
        User user = findUserByEmail(userEmail);
        ensureUserIsOwner(plan, user.getId());
        
        ensurePlanIsNotArchived(plan); // THÊM

        Task taskToRemove = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công việc với ID: " + taskId));
        if (!taskToRemove.getPlan().getId().equals(plan.getId())) {
             throw new AccessDeniedException("Công việc ID " + taskId + " không thuộc kế hoạch " + shareableLink);
        }

        // (Logic còn lại giữ nguyên)
        LocalDate taskDate = taskToRemove.getTaskDate(); 
        Integer removedOrder = taskToRemove.getOrder(); 
        taskRepository.delete(taskToRemove);
        log.info("Deleted task {} (date: {}, order: {}) from plan {}", taskId, taskDate, removedOrder, shareableLink);
        String destination = "/topic/plan/" + shareableLink + "/tasks";
        Map<String, Object> payload = Map.of(
            "type", "DELETE_TASK",
            "taskDate", taskDate != null ? taskDate.toString() : "null", 
            "taskId", taskId
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.info("Sent WebSocket update ({}) to {} for deleted task {}", payload.get("type"), destination, taskId);
        reorderTasksAfterRemoval(plan.getId().longValue(), taskDate, removedOrder);
    }

     @Override
     public List<TaskResponse> reorderTasksInPlan(String shareableLink, ReorderTasksRequest request, String ownerEmail) {
         Plan plan = findPlanRegardlessOfStatus(shareableLink); // SỬA
         User owner = findUserByEmail(ownerEmail);
         ensureUserIsOwner(plan, owner.getId());
         
         ensurePlanIsNotArchived(plan); // THÊM

         // (Logic còn lại giữ nguyên)
         LocalDate taskDate = request.getTaskDate();
         if (taskDate == null) {
            throw new BadRequestException("Ngày của công việc (taskDate) là bắt buộc để sắp xếp.");
         }
         List<Long> orderedTaskIds = request.getOrderedTaskIds();
         if (orderedTaskIds == null || orderedTaskIds.isEmpty()) {
              return Collections.emptyList();
         }
         List<Task> currentTasks = taskRepository.findAllByPlanIdAndTaskDateOrderByOrderAsc(plan.getId().longValue(), taskDate);
         if (orderedTaskIds.size() != currentTasks.size()) {
             throw new BadRequestException("Số lượng công việc không khớp. Yêu cầu: " + orderedTaskIds.size() + ", Hiện có cho ngày " + taskDate + ": " + currentTasks.size());
         }
         Set<Long> currentTaskIdsSet = currentTasks.stream().map(Task::getId).collect(Collectors.toSet());
         if (!currentTaskIdsSet.containsAll(orderedTaskIds)) {
             throw new BadRequestException("Danh sách ID công việc không hợp lệ hoặc chứa ID không thuộc ngày " + taskDate);
         }
         if (new HashSet<>(orderedTaskIds).size() != orderedTaskIds.size()) { 
              throw new BadRequestException("Danh sách ID công việc chứa ID trùng lặp.");
         }
         Map<Long, Task> taskMap = currentTasks.stream()
                                              .collect(Collectors.toMap(Task::getId, Function.identity()));
         List<Task> updatedTasksInOrder = new ArrayList<>(); 
         List<Task> tasksToSave = new ArrayList<>(); 
         boolean orderChanged = false;
         for (int i = 0; i < orderedTaskIds.size(); i++) {
             Long taskId = orderedTaskIds.get(i);
             Task task = taskMap.get(taskId);
             if (task.getOrder() == null || task.getOrder() != i) {
                 task.setOrder(i); 
                 tasksToSave.add(task);
                 orderChanged = true;
             }
             updatedTasksInOrder.add(task); 
         }
         if (orderChanged) {
             taskRepository.saveAll(tasksToSave);
             log.info("Reordered {} tasks for plan {} on date {}", tasksToSave.size(), shareableLink, taskDate);
         } else {
              log.info("No order changes detected for tasks in plan {} on date {}", shareableLink, taskDate);
         }
         String destination = "/topic/plan/" + shareableLink + "/tasks";
         Map<String, Object> payload = Map.of(
             "type", "REORDER_TASKS",
             "taskDate", taskDate.toString(), 
             "orderedTaskIds", orderedTaskIds 
         );
         messagingTemplate.convertAndSend(destination, payload);
         log.debug("Sent WebSocket update ({}) to {} for task reorder on {}", payload.get("type"), destination, taskDate);
         return updatedTasksInOrder.stream()
                    .map(taskMapper::toTaskResponse)
                    .collect(Collectors.toList());
     }

    // --- Phương thức mới: Lấy Task theo ngày ---
    @Override
    @Transactional(readOnly = true)
    public List<TaskResponse> getTasksByDate(String shareableLink, LocalDate date, String userEmail) {
        // SỬA: Dùng findPlanByShareableLink (an toàn, member không thể xem task của plan đã ARCHIVED)
        Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);

        if (!isUserMemberOfPlan(plan, user.getId())) {
             throw new AccessDeniedException("Bạn không phải là thành viên của kế hoạch này.");
        }
        
        // (Logic còn lại giữ nguyên)
        LocalDate planEndDate = plan.getStartDate().plusDays(plan.getDurationInDays() - 1);
        if (date.isBefore(plan.getStartDate()) || date.isAfter(planEndDate)) {
             log.warn("User {} requested tasks for date {} outside of plan {} duration ({} to {})",
                userEmail, date, shareableLink, plan.getStartDate(), planEndDate);
             return Collections.emptyList();
        }
        List<Task> tasks = taskRepository.findAllByPlanIdAndTaskDateOrderByOrderAsc(plan.getId().longValue(), date);
        return tasks.stream()
                .map(taskMapper::toTaskResponse)
                .collect(Collectors.toList());
    }


    // --- Member & Status Management Methods (SỬA DÙNG HELPER MỚI) ---
    @Override
    public void removeMemberFromPlan(String shareableLink, Integer memberUserId, String ownerEmail) {
        Plan plan = findPlanRegardlessOfStatus(shareableLink); // SỬA
        User owner = findUserByEmail(ownerEmail);
        ensureUserIsOwner(plan, owner.getId());

        PlanMember memberToRemove = plan.getMembers().stream()
                .filter(m -> m.getUser() != null && m.getUser().getId().equals(memberUserId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thành viên với ID: " + memberUserId + " trong kế hoạch này."));
        if (memberToRemove.getRole() == MemberRole.OWNER) {
            throw new BadRequestException("Không thể xóa chủ sở hữu kế hoạch.");
        }
        plan.getMembers().remove(memberToRemove); 
        planMemberRepository.delete(memberToRemove); 

        String destination = "/topic/plan/" + shareableLink + "/details";
        Map<String, Object> payload = Map.of(
            "type", "MEMBER_REMOVED",
            "userId", memberUserId 
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.info("Removed user {} from plan {}. Sent WebSocket update to {}", memberUserId, shareableLink, destination);
    }

    @Override
    public void transferOwnership(String shareableLink, TransferOwnershipRequest request, String currentOwnerEmail) {
        Plan plan = findPlanRegardlessOfStatus(shareableLink); // SỬA
        User currentOwnerUser = findUserByEmail(currentOwnerEmail);
        Integer newOwnerUserId = request.getNewOwnerUserId();

        PlanMember currentOwnerMember = plan.getMembers().stream()
                .filter(m -> m.getUser() != null && m.getUser().getId().equals(currentOwnerUser.getId()) && m.getRole() == MemberRole.OWNER)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Chỉ chủ sở hữu hiện tại ("+ currentOwnerEmail +") mới có quyền chuyển quyền sở hữu."));
        PlanMember newOwnerMember = plan.getMembers().stream()
                .filter(m -> m.getUser() != null && m.getUser().getId().equals(newOwnerUserId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thành viên với ID: " + newOwnerUserId + " trong kế hoạch này để chuyển quyền."));
        if (newOwnerMember.getId().equals(currentOwnerMember.getId())) {
            throw new BadRequestException("Bạn không thể chuyển quyền sở hữu cho chính mình.");
        }
        currentOwnerMember.setRole(MemberRole.MEMBER); 
        newOwnerMember.setRole(MemberRole.OWNER);   

        planMemberRepository.saveAll(Arrays.asList(currentOwnerMember, newOwnerMember));
        log.info("Ownership of plan {} transferred from user {} to user {}", shareableLink, currentOwnerUser.getId(), newOwnerUserId);

        String destination = "/topic/plan/" + shareableLink + "/details";
        Map<String, Object> payload = Map.of(
            "type", "OWNERSHIP_TRANSFERRED",
            "oldOwnerUserId", currentOwnerUser.getId(),
            "newOwnerUserId", newOwnerUserId
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.debug("Sent WebSocket update ({}) to {} for ownership transfer", payload.get("type"), destination);
    }

    @Override
    public PlanDetailResponse archivePlan(String shareableLink, String ownerEmail) {
        // SỬA: Dùng helper mới để có thể tìm thấy plan bất kể trạng thái
        Plan plan = findPlanRegardlessOfStatus(shareableLink);
        User owner = findUserByEmail(ownerEmail);
        ensureUserIsOwner(plan, owner.getId());

        if (plan.getStatus() == PlanStatus.ARCHIVED) {
            throw new BadRequestException("Kế hoạch này đã được lưu trữ.");
        }
        plan.setStatus(PlanStatus.ARCHIVED);
        Plan updatedPlan = planRepository.save(plan);
        PlanDetailResponse response = planMapper.toPlanDetailResponse(updatedPlan); 

        String destination = "/topic/plan/" + shareableLink + "/details";
        Map<String, Object> payload = Map.of(
            "type", "STATUS_CHANGED",
            "status", PlanStatus.ARCHIVED.name(), 
            "displayStatus", response.getDisplayStatus() 
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.info("Archived plan {}. Sent WebSocket update to {}", shareableLink, destination);
        return response;
    }

    @Override
    public PlanDetailResponse unarchivePlan(String shareableLink, String ownerEmail) {
        // SỬA: Dùng helper mới để tìm plan đã bị "soft delete"
        Plan plan = findPlanRegardlessOfStatus(shareableLink); 
        User owner = findUserByEmail(ownerEmail);
        ensureUserIsOwner(plan, owner.getId());

        if (plan.getStatus() != PlanStatus.ARCHIVED) {
            throw new BadRequestException("Kế hoạch này không ở trạng thái lưu trữ.");
        }
        
        // SỬA: Tính toán trạng thái mới
        // Nếu ngày kết thúc đã qua, set là COMPLETED, ngược lại là ACTIVE
        LocalDate endDate = plan.getStartDate().plusDays(plan.getDurationInDays() - 1);
        PlanStatus newStatus = LocalDate.now().isAfter(endDate) ? PlanStatus.COMPLETED : PlanStatus.ACTIVE;
        
        plan.setStatus(newStatus); 
        Plan updatedPlan = planRepository.save(plan);
         PlanDetailResponse response = planMapper.toPlanDetailResponse(updatedPlan);

        String destination = "/topic/plan/" + shareableLink + "/details";
        Map<String, Object> payload = Map.of(
            "type", "STATUS_CHANGED",
            "status", newStatus.name(), // SỬA: Gửi trạng thái mới
            "displayStatus", response.getDisplayStatus()
        );
        messagingTemplate.convertAndSend(destination, payload);
         log.info("Unarchived plan {}. New status: {}. Sent WebSocket update to {}", shareableLink, newStatus, destination);
        return response;
    }

    // --- THÊM PHƯƠNG THỨC MỚI ---
    @Override
    @Transactional
    public void deletePlanPermanently(String shareableLink, String ownerEmail) {
        // 1. Lấy Plan và các liên kết
        Plan plan = findPlanRegardlessOfStatus(shareableLink);
        User owner = findUserByEmail(ownerEmail);
        ensureUserIsOwner(plan, owner.getId());
        if (plan.getStatus() != PlanStatus.ARCHIVED) {
            throw new BadRequestException("Chỉ có thể xóa vĩnh viễn kế hoạch đã lưu trữ.");
        }

        List<Long> taskIds = plan.getDailyTasks().stream().map(Task::getId).collect(Collectors.toList());
        List<Integer> memberIds = plan.getMembers().stream().map(PlanMember::getId).collect(Collectors.toList());

        // --- BƯỚC 1: DỌN DẸP CÁC LIÊN KẾT BÊN NGOÀI (KHÔNG CASCADE) ---

        // Xóa các FeedEvent trỏ đến Plan này
        feedEventRepository.deleteAllByPlanId(plan.getId());

        // Xóa các CheckInTask trỏ đến Task (nếu có)
        if (!taskIds.isEmpty()) {
            checkInTaskRepository.deleteAllByTaskIdIn(taskIds);

            // !! QUAN TRỌNG: Bạn cũng phải xóa TaskComment và TaskAttachment
            // Bạn cần thêm 2 repository và 2 phương thức này
            // taskCommentRepository.deleteAllByTaskIdIn(taskIds);
            // taskAttachmentRepository.deleteAllByTaskIdIn(taskIds);
        }

        // Xóa các CheckInEvent trỏ đến PlanMember (nếu có)
        if (!memberIds.isEmpty()) {
            // Dùng .deleteAll() thay vì .deleteAllInBatch() để session được cập nhật!
            List<CheckInEvent> eventsToDelete = checkInEventRepository.findAllByPlanMemberIdIn(memberIds);
            if (!eventsToDelete.isEmpty()) {
                // Giả sử CheckInEvent có các con (attachments, links), bạn phải xóa chúng trước
                // checkInAttachmentRepository.deleteAllByCheckInEventIn(eventsToDelete);
                // checkInLinkRepository.deleteAllByCheckInEventIn(eventsToDelete);

                checkInEventRepository.deleteAll(eventsToDelete); // Dùng .deleteAll()
            }
        }

        // --- BƯỚC 2: XÓA PLAN CHA ---
        // CascadeType.ALL + orphanRemoval = true sẽ tự động lo phần còn lại:
        // 1. Xóa Plan ->
        // 2. Xóa PlanMember (từ plan.getMembers()) ->
        // 3. Xóa Task (từ plan.getDailyTasks()) ->
        // 4. Xóa DailyProgress (từ member.getDailyProgressList()) ->
        // 5. Xóa ProgressComment, ProgressReaction (nếu DailyProgress có cascade)

        planRepository.delete(plan);

        log.info("User {} permanently deleted plan {} (ID: {})", ownerEmail, shareableLink, plan.getId());
    }
    // --- KẾT THÚC THÊM MỚI ---

    // --- Helper Methods ---
    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + email));
    }
    
    // Helper này BỊ ẢNH HƯỞNG bởi @Where
    private Plan findPlanByShareableLink(String link) {
        return planRepository.findByShareableLink(link)
                .map(plan -> {
                    plan.getMembers().size(); // Trigger fetch members
                    return plan;
                })
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kế hoạch với link: " + link));
    }

    // --- THÊM HELPER MỚI ---
    // Helper này "VƯỢT RÀO" @Where, dùng cho các hàm quản trị
    private Plan findPlanRegardlessOfStatus(String link) {
         return planRepository.findRegardlessOfStatusByShareableLink(link)
                .map(plan -> {
                    plan.getMembers().size(); // Trigger fetch members
                    return plan;
                })
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kế hoạch với link: " + link));
    }
    
    // --- THÊM HELPER MỚI ---
    // Helper kiểm tra plan có bị lưu trữ không
    private void ensurePlanIsNotArchived(Plan plan) {
        if (plan.getStatus() == PlanStatus.ARCHIVED) {
            throw new BadRequestException("Không thể thực hiện hành động này. Kế hoạch đã được lưu trữ.");
        }
    }
    // --- KẾT THÚC THÊM HELPER ---

    // Helper kiểm tra user có phải là thành viên không
    private boolean isUserMemberOfPlan(Plan plan, Integer userId) {
        // Kiểm tra null an toàn
        return plan != null && plan.getMembers() != null && userId != null &&
               plan.getMembers().stream()
                   .anyMatch(m -> m != null && m.getUser() != null && userId.equals(m.getUser().getId()));
    }

    // Helper kiểm tra user có phải là chủ sở hữu không
    private void ensureUserIsOwner(Plan plan, Integer userId) {
         if (plan == null || plan.getMembers() == null || userId == null) {
             throw new AccessDeniedException("Không thể xác thực quyền sở hữu.");
        }
        boolean isOwner = plan.getMembers().stream()
                .anyMatch(m -> m != null && m.getUser() != null && userId.equals(m.getUser().getId()) && m.getRole() == MemberRole.OWNER);
        if (!isOwner) {
            throw new AccessDeniedException("Chỉ chủ sở hữu mới có quyền thực hiện hành động này.");
        }
    }

    // Helper cập nhật lại thứ tự các task sau khi một task bị xóa hoặc chuyển đi
    private void reorderTasksAfterRemoval(Long planId, LocalDate taskDate, Integer removedOrder) {
         if (planId == null || taskDate == null || removedOrder == null || removedOrder < 0) {
             return;
         }
        List<Task> tasksToReorder = taskRepository.findAllByPlanIdAndTaskDate(planId, taskDate)
            .stream()
            .filter(t -> t.getOrder() != null && t.getOrder() > removedOrder)
            .peek(t -> t.setOrder(t.getOrder() - 1)) // Giảm order đi 1
            .collect(Collectors.toList());
        if (!tasksToReorder.isEmpty()) {
            taskRepository.saveAll(tasksToReorder);
            log.info("Reordered {} tasks on date {} after removal/move.", tasksToReorder.size(), taskDate);
        }
    }

     // Helper map PlanMember sang DTO (dùng cho WebSocket, logs, etc.)
     private PlanDetailResponse.PlanMemberResponse toPlanMemberResponse(PlanMember member) {
        return planMapper.toPlanMemberResponse(member);
     }
     // Helper lấy tên đầy đủ của User (dùng cho WebSocket, logs, etc.)
     private String getUserFullName(User user) {
        return taskMapper.getUserFullName(user); 
     }
}