package com.example.demo.plan.service.impl;

import com.example.demo.feed.entity.FeedEventType;
import com.example.demo.feed.service.FeedService;
import com.example.demo.plan.dto.request.CreatePlanRequest;
import com.example.demo.plan.dto.request.ManageTaskRequest;
import com.example.demo.plan.dto.request.ReorderTasksRequest;
import com.example.demo.plan.dto.request.TransferOwnershipRequest;
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


    @Override
    public PlanDetailResponse createPlan(CreatePlanRequest request, String creatorEmail) {
        User creator = findUserByEmail(creatorEmail);

        // Validate ngày bắt đầu không phải quá khứ
        if (request.getStartDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("Ngày bắt đầu không thể là một ngày trong quá khứ.");
        }

        Plan newPlan = Plan.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .durationInDays(request.getDurationInDays())
                .dailyGoal(request.getDailyGoal())
                .startDate(request.getStartDate())
                .creator(creator)
                .members(new ArrayList<>())
                .dailyTasks(new ArrayList<>()) // Mặc dù không dùng list này nhiều nữa, vẫn khởi tạo
                .status(PlanStatus.ACTIVE) // Trạng thái mặc định
                .build();

        // Thêm các task ban đầu (nếu có) cho ngày bắt đầu
        if (request.getDailyTasks() != null) {
            IntStream.range(0, request.getDailyTasks().size())
                    .mapToObj(i -> {
                        CreatePlanRequest.TaskRequest taskReq = request.getDailyTasks().get(i);
                        return Task.builder()
                                .description(taskReq.getDescription())
                                .deadlineTime(taskReq.getDeadlineTime())
                                .order(i)
                                .plan(newPlan)
                                .taskDate(newPlan.getStartDate()) // Gán task cho ngày bắt đầu
                                .build();
                    })
                    .forEach(newPlan::addTask); // Vẫn dùng helper của Plan để duy trì mqh 2 chiều (dù list dailyTasks ít dùng)
        }

        // Thêm người tạo làm chủ sở hữu
        PlanMember creatorAsMember = PlanMember.builder()
                .plan(newPlan)
                .user(creator)
                .role(MemberRole.OWNER)
                .build();
        newPlan.getMembers().add(creatorAsMember);

        // Lưu Plan (bao gồm cả Member và Task nhờ CascadeType.ALL)
        Plan savedPlan = planRepository.save(newPlan);
        log.info("Plan created with ID: {} and shareableLink: {}", savedPlan.getId(), savedPlan.getShareableLink());
        // Map sang DTO để trả về
        return planMapper.toPlanDetailResponse(savedPlan);
    }

    @Override
    public PlanDetailResponse joinPlan(String shareableLink, String userEmail) {
        Plan plan = findPlanByShareableLink(shareableLink); // Fetch plan và members
        User user = findUserByEmail(userEmail);

        // Kiểm tra xem user đã là thành viên chưa
        if (isUserMemberOfPlan(plan, user.getId())) {
            throw new BadRequestException("Bạn đã tham gia kế hoạch này rồi.");
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
        // Fetch plan và members (không cần fetch task ở đây nữa)
        Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);

        // Nếu là thành viên, trả về chi tiết đầy đủ (PlanDetailResponse)
        if (isUserMemberOfPlan(plan, user.getId())) {
            // PlanDetailResponse sẽ không còn list 'dailyTasks' nữa
            return planMapper.toPlanDetailResponse(plan);
        } else {
            // Nếu không phải thành viên, trả về thông tin công khai (PlanPublicResponse)
            return planMapper.toPlanPublicResponse(plan);
        }
    }

    @Override
    public PlanDetailResponse updatePlan(String shareableLink, UpdatePlanRequest request, String userEmail) {
        // Fetch plan và members (không cần fetch task)
        Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);
        ensureUserIsOwner(plan, user.getId()); // Đảm bảo là chủ sở hữu

        // Kiểm tra xem thông tin cơ bản có thay đổi không
        boolean infoChanged = !Objects.equals(plan.getTitle(), request.getTitle()) ||
                              !Objects.equals(plan.getDescription(), request.getDescription()) ||
                              plan.getDurationInDays() != request.getDurationInDays() ||
                              !Objects.equals(plan.getDailyGoal(), request.getDailyGoal());
        PlanStatus statusBeforeUpdate = plan.getStatus(); // Lưu trạng thái cũ để kiểm tra hoàn thành

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
            plan.getDailyTasks().removeAll(oldTasksOnStartDate); // Xóa khỏi collection của Plan entity (dù ít dùng)
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
                 .forEach(plan::addTask); // Thêm vào collection và thiết lập mqh 2 chiều
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

        // Kiểm tra xem plan có vừa mới hoàn thành không (dựa trên ngày kết thúc)
        LocalDate endDate = updatedPlan.getStartDate().plusDays(updatedPlan.getDurationInDays() - 1);
        boolean justCompleted = statusBeforeUpdate == PlanStatus.ACTIVE &&
                                LocalDate.now().isAfter(endDate); // Điều kiện đơn giản: active và đã qua ngày kết thúc
        // Lưu ý: Logic hoàn thành có thể phức tạp hơn (VD: chủ plan bấm nút hoàn thành)
        if (justCompleted) {
            log.info("Plan {} is considered completed based on end date.", shareableLink);
            Map<String, Object> details = Map.of("memberCount", updatedPlan.getMembers().size());
            // Gửi Feed Event PLAN_COMPLETE (actor là null vì đây là sự kiện hệ thống)
            feedService.createAndPublishFeedEvent(FeedEventType.PLAN_COMPLETE, null, updatedPlan, details);
            // Có thể cập nhật trạng thái plan thành COMPLETED ở đây nếu muốn
            // updatedPlan.setStatus(PlanStatus.COMPLETED);
            // planRepository.save(updatedPlan);
        }

        // Trả về chi tiết Plan đã cập nhật
        return planMapper.toPlanDetailResponse(updatedPlan);
    }


    @Override
    public void leavePlan(String shareableLink, String userEmail) {
        Plan plan = findPlanByShareableLink(shareableLink);
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
         // Không cần Feed event
         // Không cần WebSocket vì user rời đi sẽ không nhận được nữa
    }

    @Override
    public void deletePlan(String shareableLink, String userEmail) {
         Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);
        ensureUserIsOwner(plan, user.getId()); // Đảm bảo là chủ sở hữu
        planRepository.delete(plan); // Xóa plan (cascade xóa members, tasks, etc.)
         log.info("User {} deleted plan {}", userEmail, shareableLink);
          // Không cần Feed event hay WebSocket
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanSummaryResponse> getMyPlans(String userEmail, String searchTerm) {
         User user = findUserByEmail(userEmail);
         // Lấy tất cả PlanMember của user, fetch kèm Plan
        List<PlanMember> planMembers = planMemberRepository.findByUserIdWithPlan(user.getId());

        Stream<PlanMember> filteredStream = planMembers.stream();
        // Lọc theo searchTerm nếu có
        if (searchTerm != null && !searchTerm.isBlank()) {
            String lowerCaseSearchTerm = searchTerm.toLowerCase().trim();
            filteredStream = filteredStream.filter(pm -> pm.getPlan() != null &&
                                                        pm.getPlan().getTitle() != null &&
                                                        pm.getPlan().getTitle().toLowerCase().contains(lowerCaseSearchTerm));
        }

        // Map sang DTO Summary, lọc bỏ null, sắp xếp theo ngày bắt đầu giảm dần
        return filteredStream
                .map(planMapper::toPlanSummaryResponse) // Sử dụng mapper
                .filter(Objects::nonNull) // Lọc bỏ kết quả null từ mapper (nếu plan bị lỗi)
                .sorted(Comparator.comparing(PlanSummaryResponse::getStartDate, Comparator.nullsLast(Comparator.reverseOrder()))) // Sort by start date desc
                .collect(Collectors.toList());
    }

    // --- Task Management Methods (ĐÃ CẬP NHẬT THEO LOGIC MỚI) ---

    @Override
    public TaskResponse addTaskToPlan(String shareableLink, ManageTaskRequest request, String userEmail) {
        Plan plan = findPlanByShareableLink(shareableLink); // Không cần fetch task
        User user = findUserByEmail(userEmail);
        ensureUserIsOwner(plan, user.getId()); // Đảm bảo là chủ sở hữu

        // Validate taskDate là bắt buộc và hợp lệ
        if (request.getTaskDate() == null) {
            throw new BadRequestException("Ngày của công việc (taskDate) là bắt buộc.");
        }
        LocalDate planEndDate = plan.getStartDate().plusDays(plan.getDurationInDays() - 1);
        if (request.getTaskDate().isBefore(plan.getStartDate()) || request.getTaskDate().isAfter(planEndDate)) {
             throw new BadRequestException("Ngày của công việc phải nằm trong thời gian của kế hoạch ("
                + plan.getStartDate() + " đến " + planEndDate + ").");
        }

        // Tìm thứ tự (order) lớn nhất hiện tại của ngày đó để gán order mới
        int nextOrder = taskRepository.findMaxOrderByPlanIdAndTaskDate(plan.getId().longValue(), request.getTaskDate())
                           .map(maxOrder -> maxOrder + 1) // Nếu có thì +1
                           .orElse(0); // Nếu chưa có task nào thì bắt đầu từ 0

        // Tạo Task entity mới
        Task newTask = Task.builder()
                .description(request.getDescription())
                .deadlineTime(request.getDeadlineTime())
                .order(nextOrder)
                .plan(plan) // Thiết lập mqh với Plan
                .taskDate(request.getTaskDate()) // Gán ngày từ request
                .build();

        // Lưu task mới vào DB
        Task savedTask = taskRepository.save(newTask);
        // Lưu ý: Không cần add vào plan.getDailyTasks() nữa

        // Map sang DTO Response
        TaskResponse taskResponse = taskMapper.toTaskResponse(savedTask);

        // Gửi WebSocket thông báo có task mới
        String destination = "/topic/plan/" + shareableLink + "/tasks";
        Map<String, Object> payload = Map.of(
            "type", "NEW_TASK",
            "taskDate", savedTask.getTaskDate().toString(), // Thêm ngày để Frontend biết cập nhật ngày nào
            "task", taskResponse // Gửi thông tin task mới
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.info("Added task {} (date: {}) to plan {}. Sent WebSocket update to {}", savedTask.getId(), savedTask.getTaskDate(), shareableLink, destination);

        return taskResponse;
    }

    @Override
    public TaskResponse updateTaskInPlan(String shareableLink, Long taskId, ManageTaskRequest request, String userEmail) {
        Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);
        ensureUserIsOwner(plan, user.getId());

        // Tìm task cần cập nhật
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công việc với ID: " + taskId));

        // Kiểm tra task có thuộc plan này không
        if (!task.getPlan().getId().equals(plan.getId())) {
             throw new AccessDeniedException("Công việc ID " + taskId + " không thuộc kế hoạch " + shareableLink);
        }

        LocalDate originalDate = task.getTaskDate(); // Lưu lại ngày cũ
        boolean dateChanged = false;

        // Cập nhật description và deadlineTime
        task.setDescription(request.getDescription());
        task.setDeadlineTime(request.getDeadlineTime());

        // Xử lý nếu có taskDate mới (chuyển task sang ngày khác)
        if (request.getTaskDate() != null && !request.getTaskDate().equals(originalDate)) {
            // Validate ngày mới
            LocalDate planEndDate = plan.getStartDate().plusDays(plan.getDurationInDays() - 1);
             if (request.getTaskDate().isBefore(plan.getStartDate()) || request.getTaskDate().isAfter(planEndDate)) {
                 throw new BadRequestException("Ngày chuyển đến ("+ request.getTaskDate() +") phải nằm trong thời gian của kế hoạch.");
             }

            // Tìm order mới ở ngày mới
            int nextOrderInNewDate = taskRepository.findMaxOrderByPlanIdAndTaskDate(plan.getId().longValue(), request.getTaskDate())
                           .map(maxOrder -> maxOrder + 1)
                           .orElse(0);
            task.setTaskDate(request.getTaskDate());
            task.setOrder(nextOrderInNewDate);
            dateChanged = true;
            log.info("Task {} moved from {} to {} with new order {}", taskId, originalDate, task.getTaskDate(), task.getOrder());
        }

        // Lưu lại task đã cập nhật
        Task updatedTask = taskRepository.save(task);

        // Map sang DTO Response
        TaskResponse taskResponse = taskMapper.toTaskResponse(updatedTask);

        // Gửi WebSocket: Type là MOVE_TASK nếu ngày thay đổi, UPDATE_TASK nếu không
        String destination = "/topic/plan/" + shareableLink + "/tasks";
        Map<String, Object> payload = new HashMap<>(); // Dùng HashMap để dễ thêm key
        payload.put("type", dateChanged ? "MOVE_TASK" : "UPDATE_TASK");
        payload.put("taskDate", updatedTask.getTaskDate().toString()); // Ngày hiện tại của task
        payload.put("task", taskResponse);
        if (dateChanged) {
             payload.put("originalTaskDate", originalDate.toString()); // Gửi thêm ngày gốc nếu task bị move
        }
        messagingTemplate.convertAndSend(destination, payload);
        log.info("Updated task {} in plan {}. Sent WebSocket update ({}) to {}", taskId, shareableLink, payload.get("type"), destination);

        // Nếu ngày thay đổi, cần cập nhật lại thứ tự của các task ở ngày cũ (sau khi task này bị dời đi)
         if (dateChanged) {
             reorderTasksAfterRemoval(plan.getId().longValue(), originalDate, task.getOrder());
             // Gửi thêm WebSocket REORDER cho ngày cũ? (Có thể không cần nếu client fetch lại khi nhận MOVE_TASK)
             // Hoặc client tự xử lý UI khi nhận MOVE_TASK
         }

        return taskResponse;
    }

    @Override
    public void deleteTaskFromPlan(String shareableLink, Long taskId, String userEmail) {
        Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);
        ensureUserIsOwner(plan, user.getId());

        // Tìm task cần xóa
        Task taskToRemove = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công việc với ID: " + taskId));

        // Kiểm tra task có thuộc plan này không
        if (!taskToRemove.getPlan().getId().equals(plan.getId())) {
             throw new AccessDeniedException("Công việc ID " + taskId + " không thuộc kế hoạch " + shareableLink);
        }

        LocalDate taskDate = taskToRemove.getTaskDate(); // Lấy ngày của task bị xóa
        Integer removedOrder = taskToRemove.getOrder(); // Lấy thứ tự của task bị xóa

        // Xóa task khỏi DB
        taskRepository.delete(taskToRemove);
        log.info("Deleted task {} (date: {}, order: {}) from plan {}", taskId, taskDate, removedOrder, shareableLink);


        // Gửi WebSocket thông báo xóa task
        String destination = "/topic/plan/" + shareableLink + "/tasks";
        Map<String, Object> payload = Map.of(
            "type", "DELETE_TASK",
            "taskDate", taskDate != null ? taskDate.toString() : "null", // Gửi kèm ngày
            "taskId", taskId
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.info("Sent WebSocket update ({}) to {} for deleted task {}", payload.get("type"), destination, taskId);

        // Cập nhật lại thứ tự các task còn lại CÙNG NGÀY
        reorderTasksAfterRemoval(plan.getId().longValue(), taskDate, removedOrder);
        // Có thể gửi thêm WebSocket REORDER cho ngày đó nếu cần,
        // nhưng client nên fetch lại khi nhận DELETE_TASK.
    }

     @Override
     public List<TaskResponse> reorderTasksInPlan(String shareableLink, ReorderTasksRequest request, String ownerEmail) {
         Plan plan = findPlanByShareableLink(shareableLink);
         User owner = findUserByEmail(ownerEmail);
         ensureUserIsOwner(plan, owner.getId());

         // Validate taskDate từ request
         LocalDate taskDate = request.getTaskDate();
         if (taskDate == null) {
            throw new BadRequestException("Ngày của công việc (taskDate) là bắt buộc để sắp xếp.");
         }
         // Optional: Validate date nằm trong plan duration

         List<Long> orderedTaskIds = request.getOrderedTaskIds();
         if (orderedTaskIds == null || orderedTaskIds.isEmpty()) {
              // Nếu danh sách rỗng, không có gì để sắp xếp
              return Collections.emptyList();
              // Hoặc throw lỗi tùy logic: throw new BadRequestException("Danh sách ID công việc rỗng.");
         }

         // Lấy danh sách task hiện tại CỦA NGÀY ĐÓ từ DB
         List<Task> currentTasks = taskRepository.findAllByPlanIdAndTaskDateOrderByOrderAsc(plan.getId().longValue(), taskDate);

         // Kiểm tra tính hợp lệ của danh sách ID mới
         if (orderedTaskIds.size() != currentTasks.size()) {
             throw new BadRequestException("Số lượng công việc không khớp. Yêu cầu: " + orderedTaskIds.size() + ", Hiện có cho ngày " + taskDate + ": " + currentTasks.size());
         }
         Set<Long> currentTaskIdsSet = currentTasks.stream().map(Task::getId).collect(Collectors.toSet());
         if (!currentTaskIdsSet.containsAll(orderedTaskIds)) {
             throw new BadRequestException("Danh sách ID công việc không hợp lệ hoặc chứa ID không thuộc ngày " + taskDate);
         }
         if (new HashSet<>(orderedTaskIds).size() != orderedTaskIds.size()) { // Kiểm tra ID trùng lặp
              throw new BadRequestException("Danh sách ID công việc chứa ID trùng lặp.");
         }

         // Tạo map để truy cập nhanh Task entity bằng ID
         Map<Long, Task> taskMap = currentTasks.stream()
                                              .collect(Collectors.toMap(Task::getId, Function.identity()));

         List<Task> updatedTasksInOrder = new ArrayList<>(); // List chứa task theo thứ tự mới
         List<Task> tasksToSave = new ArrayList<>(); // Chỉ chứa task có order thay đổi cần lưu
         boolean orderChanged = false;
         for (int i = 0; i < orderedTaskIds.size(); i++) {
             Long taskId = orderedTaskIds.get(i);
             Task task = taskMap.get(taskId);
             // Nếu order mới khác order cũ
             if (task.getOrder() == null || task.getOrder() != i) {
                 task.setOrder(i); // Cập nhật order mới
                 tasksToSave.add(task);
                 orderChanged = true;
             }
             updatedTasksInOrder.add(task); // Thêm vào list theo đúng thứ tự mới
         }

         // Chỉ lưu vào DB nếu có thay đổi thứ tự
         if (orderChanged) {
             taskRepository.saveAll(tasksToSave);
             log.info("Reordered {} tasks for plan {} on date {}", tasksToSave.size(), shareableLink, taskDate);
         } else {
              log.info("No order changes detected for tasks in plan {} on date {}", shareableLink, taskDate);
         }

         // Gửi WebSocket thông báo reorder (luôn gửi để đảm bảo client đồng bộ, ngay cả khi DB không đổi)
         String destination = "/topic/plan/" + shareableLink + "/tasks";
         Map<String, Object> payload = Map.of(
             "type", "REORDER_TASKS",
             "taskDate", taskDate.toString(), // Gửi kèm ngày
             "orderedTaskIds", orderedTaskIds // Gửi danh sách ID theo thứ tự mới
         );
         messagingTemplate.convertAndSend(destination, payload);
         log.debug("Sent WebSocket update ({}) to {} for task reorder on {}", payload.get("type"), destination, taskDate);

         // Trả về danh sách TaskResponse theo thứ tự mới
         return updatedTasksInOrder.stream()
                    .map(taskMapper::toTaskResponse)
                    .collect(Collectors.toList());
     }

    // --- Phương thức mới: Lấy Task theo ngày ---
    @Override
    @Transactional(readOnly = true)
    public List<TaskResponse> getTasksByDate(String shareableLink, LocalDate date, String userEmail) {
        Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);

        // Kiểm tra quyền truy cập (là thành viên)
        if (!isUserMemberOfPlan(plan, user.getId())) {
             throw new AccessDeniedException("Bạn không phải là thành viên của kế hoạch này.");
        }

        // Validate ngày nằm trong khoảng thời gian của plan (optional but recommended)
        LocalDate planEndDate = plan.getStartDate().plusDays(plan.getDurationInDays() - 1);
        if (date.isBefore(plan.getStartDate()) || date.isAfter(planEndDate)) {
             log.warn("User {} requested tasks for date {} outside of plan {} duration ({} to {})",
                userEmail, date, shareableLink, plan.getStartDate(), planEndDate);
             // Trả về list rỗng nếu ngày không hợp lệ thay vì lỗi
             return Collections.emptyList();
        }

        // Lấy task từ repository (đã có sắp xếp theo order)
        List<Task> tasks = taskRepository.findAllByPlanIdAndTaskDateOrderByOrderAsc(plan.getId().longValue(), date);

        // Map sang DTO Response
        return tasks.stream()
                .map(taskMapper::toTaskResponse)
                .collect(Collectors.toList());
    }


    // --- Member & Status Management Methods ---
    @Override
    public void removeMemberFromPlan(String shareableLink, Integer memberUserId, String ownerEmail) {
        Plan plan = findPlanByShareableLink(shareableLink);
        User owner = findUserByEmail(ownerEmail);
        ensureUserIsOwner(plan, owner.getId());

        // Tìm thành viên cần xóa
        PlanMember memberToRemove = plan.getMembers().stream()
                .filter(m -> m.getUser() != null && m.getUser().getId().equals(memberUserId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thành viên với ID: " + memberUserId + " trong kế hoạch này."));

        // Không thể xóa chủ sở hữu
        if (memberToRemove.getRole() == MemberRole.OWNER) {
            throw new BadRequestException("Không thể xóa chủ sở hữu kế hoạch.");
        }

        // Xóa thành viên
        plan.getMembers().remove(memberToRemove); // Xóa khỏi collection
        planMemberRepository.delete(memberToRemove); // Xóa khỏi DB

        // Gửi WebSocket thông báo thành viên bị xóa
        String destination = "/topic/plan/" + shareableLink + "/details";
        Map<String, Object> payload = Map.of(
            "type", "MEMBER_REMOVED",
            "userId", memberUserId // Gửi ID của user bị xóa
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.info("Removed user {} from plan {}. Sent WebSocket update to {}", memberUserId, shareableLink, destination);
    }

    @Override
    public void transferOwnership(String shareableLink, TransferOwnershipRequest request, String currentOwnerEmail) {
        Plan plan = findPlanByShareableLink(shareableLink);
        User currentOwnerUser = findUserByEmail(currentOwnerEmail);
        Integer newOwnerUserId = request.getNewOwnerUserId();

        // Tìm PlanMember của chủ sở hữu hiện tại
        PlanMember currentOwnerMember = plan.getMembers().stream()
                .filter(m -> m.getUser() != null && m.getUser().getId().equals(currentOwnerUser.getId()) && m.getRole() == MemberRole.OWNER)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Chỉ chủ sở hữu hiện tại ("+ currentOwnerEmail +") mới có quyền chuyển quyền sở hữu."));

        // Tìm PlanMember của người sẽ nhận quyền
        PlanMember newOwnerMember = plan.getMembers().stream()
                .filter(m -> m.getUser() != null && m.getUser().getId().equals(newOwnerUserId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thành viên với ID: " + newOwnerUserId + " trong kế hoạch này để chuyển quyền."));

        // Không thể chuyển cho chính mình
        if (newOwnerMember.getId().equals(currentOwnerMember.getId())) {
            throw new BadRequestException("Bạn không thể chuyển quyền sở hữu cho chính mình.");
        }

        // Đổi vai trò
        currentOwnerMember.setRole(MemberRole.MEMBER); // Chủ cũ thành thành viên
        newOwnerMember.setRole(MemberRole.OWNER);   // Người mới thành chủ sở hữu

        // Lưu cả hai thay đổi
        planMemberRepository.saveAll(Arrays.asList(currentOwnerMember, newOwnerMember));
        log.info("Ownership of plan {} transferred from user {} to user {}", shareableLink, currentOwnerUser.getId(), newOwnerUserId);

        // Gửi WebSocket thông báo chuyển quyền
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
        Plan plan = findPlanByShareableLink(shareableLink);
        User owner = findUserByEmail(ownerEmail);
        ensureUserIsOwner(plan, owner.getId());

        // Kiểm tra nếu đã lưu trữ rồi
        if (plan.getStatus() == PlanStatus.ARCHIVED) {
            throw new BadRequestException("Kế hoạch này đã được lưu trữ.");
        }

        // Đổi trạng thái và lưu
        plan.setStatus(PlanStatus.ARCHIVED);
        Plan updatedPlan = planRepository.save(plan);
        PlanDetailResponse response = planMapper.toPlanDetailResponse(updatedPlan); // Map sang DTO

        // Gửi WebSocket thông báo thay đổi trạng thái
        String destination = "/topic/plan/" + shareableLink + "/details";
        Map<String, Object> payload = Map.of(
            "type", "STATUS_CHANGED",
            "status", PlanStatus.ARCHIVED.name(), // Trạng thái mới
            "displayStatus", response.getDisplayStatus() // Trạng thái hiển thị (có thể khác)
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.info("Archived plan {}. Sent WebSocket update to {}", shareableLink, destination);

        return response;
    }

    @Override
    public PlanDetailResponse unarchivePlan(String shareableLink, String ownerEmail) {
        Plan plan = findPlanByShareableLink(shareableLink);
        User owner = findUserByEmail(ownerEmail);
        ensureUserIsOwner(plan, owner.getId());

        // Kiểm tra nếu không phải đang lưu trữ
        if (plan.getStatus() != PlanStatus.ARCHIVED) {
            throw new BadRequestException("Kế hoạch này không ở trạng thái lưu trữ.");
        }

        // Đổi trạng thái về ACTIVE và lưu
        plan.setStatus(PlanStatus.ACTIVE); // Chuyển về Active (hoặc trạng thái phù hợp khác)
        Plan updatedPlan = planRepository.save(plan);
         PlanDetailResponse response = planMapper.toPlanDetailResponse(updatedPlan);

        // Gửi WebSocket thông báo thay đổi trạng thái
        String destination = "/topic/plan/" + shareableLink + "/details";
        Map<String, Object> payload = Map.of(
            "type", "STATUS_CHANGED",
            "status", PlanStatus.ACTIVE.name(), // Trạng thái mới
            "displayStatus", response.getDisplayStatus()
        );
        messagingTemplate.convertAndSend(destination, payload);
         log.info("Unarchived plan {}. Sent WebSocket update to {}", shareableLink, destination);

        return response;
    }

    // --- Helper Methods ---
    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + email));
    }
    // Helper này chỉ fetch Plan và Members
    private Plan findPlanByShareableLink(String link) {
        return planRepository.findByShareableLink(link)
                .map(plan -> {
                    plan.getMembers().size(); // Trigger fetch members
                    return plan;
                })
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kế hoạch với link: " + link));
    }

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
             // Không cần reorder nếu thiếu thông tin hoặc order không hợp lệ
             return;
         }

        // Tìm tất cả các task còn lại trong ngày đó có order lớn hơn order bị xóa/chuyển
        List<Task> tasksToReorder = taskRepository.findAllByPlanIdAndTaskDate(planId, taskDate)
            .stream()
            .filter(t -> t.getOrder() != null && t.getOrder() > removedOrder)
            .peek(t -> t.setOrder(t.getOrder() - 1)) // Giảm order đi 1
            .collect(Collectors.toList());

        // Nếu có task cần cập nhật order, lưu lại
        if (!tasksToReorder.isEmpty()) {
            taskRepository.saveAll(tasksToReorder);
            log.info("Reordered {} tasks on date {} after removal/move.", tasksToReorder.size(), taskDate);
        }
    }

     // Helper map PlanMember sang DTO (dùng cho WebSocket)
     private PlanDetailResponse.PlanMemberResponse toPlanMemberResponse(PlanMember member) {
        // Sử dụng PlanMapper nếu có, hoặc map thủ công
        return planMapper.toPlanMemberResponse(member);
     }
     // Helper lấy tên đầy đủ của User (dùng cho WebSocket, logs, etc.)
     private String getUserFullName(User user) {
        // Sử dụng TaskMapper helper nếu có, hoặc map thủ công
        return taskMapper.getUserFullName(user); // Giả sử TaskMapper có helper này
     }
}