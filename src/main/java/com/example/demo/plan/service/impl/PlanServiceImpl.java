package com.example.demo.plan.service.impl;

import com.example.demo.feed.entity.FeedEventType; // *** THÊM IMPORT ***
import com.example.demo.feed.service.FeedService; // *** THÊM IMPORT ***
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
import java.util.*; // Import HashMap
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
    private final FeedService feedService; // *** INJECT FeedService ***


    @Override
    public PlanDetailResponse createPlan(CreatePlanRequest request, String creatorEmail) {
        User creator = findUserByEmail(creatorEmail);

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
                .dailyTasks(new ArrayList<>())
                .status(PlanStatus.ACTIVE) // Default status
                .build();

        // Add tasks if provided
        if (request.getDailyTasks() != null) {
            IntStream.range(0, request.getDailyTasks().size())
                    .mapToObj(i -> {
                        CreatePlanRequest.TaskRequest taskReq = request.getDailyTasks().get(i);
                        return Task.builder()
                                .description(taskReq.getDescription())
                                .deadlineTime(taskReq.getDeadlineTime())
                                .order(i)
                                .plan(newPlan) // Set bidirectional relationship
                                .build();
                    })
                    .forEach(newPlan::addTask); // Use helper method
        }

        // Add creator as the owner
        PlanMember creatorAsMember = PlanMember.builder()
                .plan(newPlan)
                .user(creator)
                .role(MemberRole.OWNER)
                .build();
        newPlan.getMembers().add(creatorAsMember); // Add owner to members list

        Plan savedPlan = planRepository.save(newPlan);
        log.info("Plan created with ID: {} and shareableLink: {}", savedPlan.getId(), savedPlan.getShareableLink());
        // No feed event for creation usually
        return planMapper.toPlanDetailResponse(savedPlan);
    }

    @Override
    public PlanDetailResponse joinPlan(String shareableLink, String userEmail) {
        Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);

        if (isUserMemberOfPlan(plan, user.getId())) {
            throw new BadRequestException("Bạn đã tham gia kế hoạch này rồi.");
        }

        PlanMember newMember = PlanMember.builder()
                .plan(plan)
                .user(user)
                .role(MemberRole.MEMBER)
                .build();
        plan.getMembers().add(newMember);

        Plan updatedPlan = planRepository.save(plan);

        // *** GỬI FEED EVENT JOIN_PLAN ***
        feedService.createAndPublishFeedEvent(FeedEventType.JOIN_PLAN, user, plan, null); // user is the one who joined
        // *** KẾT THÚC GỬI FEED EVENT ***

        // GỬI MESSAGE WEBSOCKET MEMBER_JOINED (giữ nguyên)
        String destination = "/topic/plan/" + shareableLink + "/details";
        PlanMember savedNewMember = updatedPlan.getMembers().stream()
                                            .filter(m -> m.getUser().getId().equals(user.getId()))
                                            .findFirst().orElse(newMember);
        PlanDetailResponse.PlanMemberResponse memberResponse = planMapper.toPlanMemberResponse(savedNewMember);
        Map<String, Object> payload = Map.of(
            "type", "MEMBER_JOINED",
            "member", memberResponse
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.info("User {} joined plan {}. Sent WebSocket update to {}", userEmail, shareableLink, destination);

        return planMapper.toPlanDetailResponse(updatedPlan);
    }

    @Override
    @Transactional(readOnly = true)
    public Object getPlanDetails(String shareableLink, String userEmail) {
        Plan plan = findPlanByShareableLinkWithTasks(shareableLink);
        User user = findUserByEmail(userEmail);

        if (isUserMemberOfPlan(plan, user.getId())) {
            return planMapper.toPlanDetailResponse(plan);
        } else {
            return planMapper.toPlanPublicResponse(plan);
        }
    }

    @Override
    public PlanDetailResponse updatePlan(String shareableLink, UpdatePlanRequest request, String userEmail) {
        Plan plan = findPlanByShareableLinkWithTasks(shareableLink);
        User user = findUserByEmail(userEmail);
        ensureUserIsOwner(plan, user.getId());

        boolean infoChanged = !Objects.equals(plan.getTitle(), request.getTitle()) ||
                              !Objects.equals(plan.getDescription(), request.getDescription()) ||
                              plan.getDurationInDays() != request.getDurationInDays() ||
                              !Objects.equals(plan.getDailyGoal(), request.getDailyGoal());
        PlanStatus statusBeforeUpdate = plan.getStatus(); // Store status before changes

        plan.setTitle(request.getTitle());
        plan.setDescription(request.getDescription());
        plan.setDurationInDays(request.getDurationInDays());
        plan.setDailyGoal(request.getDailyGoal());

        // Update tasks
        List<Task> oldTasks = new ArrayList<>(plan.getDailyTasks());
        // Efficiently remove old tasks: Clear relationship first, then delete orphans
        oldTasks.forEach(task -> task.setPlan(null));
        plan.getDailyTasks().clear(); // Clear the collection on the plan side
        // Note: orphanRemoval=true on Plan entity will handle deletion, or delete explicitly
        // taskRepository.deleteAll(oldTasks); // Uncomment if orphanRemoval=false or for explicit control

        boolean tasksChanged = true; // Assume tasks always change or need reordering

        if (request.getDailyTasks() != null) {
             IntStream.range(0, request.getDailyTasks().size())
                 .mapToObj(i -> {
                     UpdatePlanRequest.TaskRequest taskReq = request.getDailyTasks().get(i);
                     return Task.builder()
                             .description(taskReq.getDescription())
                             .deadlineTime(taskReq.getDeadlineTime())
                             .order(i)
                             .plan(plan) // Set relationship
                             .build();
                 })
                 .forEach(plan::addTask); // Use helper
        }

        Plan updatedPlan = planRepository.save(plan);

        // GỬI WEBSOCKET PLAN_INFO_UPDATED (giữ nguyên)
        if (infoChanged) {
            String destination = "/topic/plan/" + shareableLink + "/details";
             Map<String, Object> payload = Map.of(
                "type", "PLAN_INFO_UPDATED",
                "title", updatedPlan.getTitle(),
                "description", updatedPlan.getDescription(),
                "durationInDays", updatedPlan.getDurationInDays(),
                "dailyGoal", updatedPlan.getDailyGoal(),
                "startDate", updatedPlan.getStartDate().toString(),
                "endDate", updatedPlan.getStartDate().plusDays(updatedPlan.getDurationInDays() - 1).toString()
            );
            messagingTemplate.convertAndSend(destination, payload);
             log.info("Plan {} info updated. Sent WebSocket update to {}", shareableLink, destination);
        }

        // *** KIỂM TRA VÀ GỬI FEED EVENT PLAN_COMPLETE ***
        LocalDate endDate = updatedPlan.getStartDate().plusDays(updatedPlan.getDurationInDays() - 1);
        // Check if plan *just* became completed (status changed or passed end date while active)
        boolean justCompleted = statusBeforeUpdate == PlanStatus.ACTIVE &&
                                LocalDate.now().isAfter(endDate); // Simplified check, assumes update happens after end date

        // More robust: Add a specific action/endpoint for Owner to mark as complete
        // Or a scheduled job to check daily

        if (justCompleted) {
             // Optionally update status to COMPLETED here if not done elsewhere
             // updatedPlan.setStatus(PlanStatus.COMPLETED);
             // planRepository.save(updatedPlan); // Save status change
            log.info("Plan {} is considered completed based on end date.", shareableLink);
            Map<String, Object> details = Map.of("memberCount", updatedPlan.getMembers().size());
            feedService.createAndPublishFeedEvent(FeedEventType.PLAN_COMPLETE, null, updatedPlan, details); // Actor is null for system event
        }
        // *** KẾT THÚC KIỂM TRA PLAN_COMPLETE ***


        return planMapper.toPlanDetailResponse(updatedPlan);
    }


    @Override
    public void leavePlan(String shareableLink, String userEmail) {
        Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);

        PlanMember member = plan.getMembers().stream()
                .filter(m -> m.getUser().getId().equals(user.getId()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Bạn không phải là thành viên của kế hoạch này."));

        if (member.getRole() == MemberRole.OWNER) {
             throw new BadRequestException("Chủ sở hữu không thể rời khỏi kế hoạch. Bạn cần phải xóa hoặc chuyển quyền sở hữu kế hoạch.");
        }

        plan.getMembers().remove(member); // Remove from collection
        planMemberRepository.delete(member); // Delete entity
         log.info("User {} left plan {}", userEmail, shareableLink);
         // No Feed event needed for leaving
    }

    @Override
    public void deletePlan(String shareableLink, String userEmail) {
         Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);
        ensureUserIsOwner(plan, user.getId());
        planRepository.delete(plan);
         log.info("User {} deleted plan {}", userEmail, shareableLink);
          // No Feed event needed for deletion (plan gone)
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanSummaryResponse> getMyPlans(String userEmail, String searchTerm) {
         User user = findUserByEmail(userEmail);
        List<PlanMember> planMembers = planMemberRepository.findByUserIdWithPlan(user.getId());

        Stream<PlanMember> filteredStream = planMembers.stream();
        if (searchTerm != null && !searchTerm.isBlank()) {
            String lowerCaseSearchTerm = searchTerm.toLowerCase().trim();
            filteredStream = filteredStream.filter(pm -> pm.getPlan() != null &&
                                                        pm.getPlan().getTitle() != null &&
                                                        pm.getPlan().getTitle().toLowerCase().contains(lowerCaseSearchTerm));
        }

        return filteredStream
                .map(planMapper::toPlanSummaryResponse)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(PlanSummaryResponse::getStartDate, Comparator.nullsLast(Comparator.reverseOrder()))) // Sort by start date desc
                .collect(Collectors.toList());
    }

    // --- Task Management Methods ---
    @Override
    public TaskResponse addTaskToPlan(String shareableLink, ManageTaskRequest request, String userEmail) {
        Plan plan = findPlanByShareableLinkWithTasks(shareableLink);
        User user = findUserByEmail(userEmail);
        ensureUserIsOwner(plan, user.getId());

        int nextOrder = plan.getDailyTasks().stream()
                           .mapToInt(t -> t.getOrder() != null ? t.getOrder() : -1) // Handle potential null order
                           .max()
                           .orElse(-1) + 1;

        Task newTask = Task.builder()
                .description(request.getDescription())
                .deadlineTime(request.getDeadlineTime())
                .order(nextOrder)
                .plan(plan) // Set relationship
                .build();

        // plan.addTask(newTask); // Add to collection
        // Task savedTask = taskRepository.save(newTask); // Save explicitly to get ID immediately

        // Save plan which cascades to save the new task
         plan.addTask(newTask);
         Plan savedPlan = planRepository.save(plan);
         // Find the saved task from the updated plan's task list
         Task savedTask = savedPlan.getDailyTasks().stream()
                                  .filter(t -> t.getOrder() != null && t.getOrder() == nextOrder && t.getDescription().equals(request.getDescription()))
                                  .findFirst()
                                  .orElseThrow(() -> new IllegalStateException("Could not find the newly added task after saving."));


        TaskResponse taskResponse = taskMapper.toTaskResponse(savedTask);

        // GỬI MESSAGE WEBSOCKET NEW_TASK (giữ nguyên)
        String destination = "/topic/plan/" + shareableLink + "/tasks";
        Map<String, Object> payload = Map.of(
            "type", "NEW_TASK",
            "task", taskResponse
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.info("Added task {} to plan {}. Sent WebSocket update to {}", savedTask.getId(), shareableLink, destination);

        return taskResponse;
    }

    @Override
    public TaskResponse updateTaskInPlan(String shareableLink, Long taskId, ManageTaskRequest request, String userEmail) {
        Plan plan = findPlanByShareableLink(shareableLink); // Don't need full tasks here
        User user = findUserByEmail(userEmail);
        ensureUserIsOwner(plan, user.getId());

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công việc với ID: " + taskId));

        if (!task.getPlan().getId().equals(plan.getId())) {
             throw new AccessDeniedException("Công việc này không thuộc kế hoạch hiện tại.");
        }

        task.setDescription(request.getDescription());
        task.setDeadlineTime(request.getDeadlineTime());

        Task updatedTask = taskRepository.save(task);
        TaskResponse taskResponse = taskMapper.toTaskResponse(updatedTask);

        // GỬI MESSAGE WEBSOCKET UPDATE_TASK (giữ nguyên)
        String destination = "/topic/plan/" + shareableLink + "/tasks";
        Map<String, Object> payload = Map.of(
            "type", "UPDATE_TASK",
            "task", taskResponse
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.info("Updated task {} in plan {}. Sent WebSocket update to {}", taskId, shareableLink, destination);

        return taskResponse;
    }

    @Override
    public void deleteTaskFromPlan(String shareableLink, Long taskId, String userEmail) {
        Plan plan = findPlanByShareableLinkWithTasks(shareableLink); // Need tasks for reordering
        User user = findUserByEmail(userEmail);
        ensureUserIsOwner(plan, user.getId());

        Task taskToRemove = plan.getDailyTasks().stream()
                                .filter(t -> t.getId().equals(taskId))
                                .findFirst()
                                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công việc với ID: " + taskId + " trong kế hoạch này."));

        int removedOrder = taskToRemove.getOrder() != null ? taskToRemove.getOrder() : -1;

        // Remove task using helper method which handles bidirectional relationship
        plan.removeTask(taskToRemove);
        // Explicitly delete if not using orphanRemoval=true or want immediate deletion guarantee
        taskRepository.delete(taskToRemove); // Delete the task entity


        // Reorder remaining tasks only if an order existed
        if (removedOrder != -1) {
            plan.getDailyTasks().stream()
                .filter(t -> t.getOrder() != null && t.getOrder() > removedOrder)
                .forEach(t -> t.setOrder(t.getOrder() - 1));
            // Save changes to reordered tasks (if any)
            taskRepository.saveAll(plan.getDailyTasks().stream()
                                        .filter(t -> t.getOrder() != null && t.getOrder() >= removedOrder)
                                        .collect(Collectors.toList()));
        }


        // GỬI MESSAGE WEBSOCKET DELETE_TASK (giữ nguyên)
        String destination = "/topic/plan/" + shareableLink + "/tasks";
        Map<String, Object> payload = Map.of(
            "type", "DELETE_TASK",
            "taskId", taskId
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.info("Deleted task {} from plan {}. Sent WebSocket update to {}", taskId, shareableLink, destination);
    }

     @Override
     public List<TaskResponse> reorderTasksInPlan(String shareableLink, ReorderTasksRequest request, String ownerEmail) {
         Plan plan = findPlanByShareableLinkWithTasks(shareableLink);
         User owner = findUserByEmail(ownerEmail);
         ensureUserIsOwner(plan, owner.getId());

         List<Long> orderedTaskIds = request.getOrderedTaskIds();
         List<Task> currentTasks = plan.getDailyTasks(); // Already sorted by @OrderBy

         if (orderedTaskIds.size() != currentTasks.size()) {
             throw new BadRequestException("Số lượng công việc không khớp. Yêu cầu: " + orderedTaskIds.size() + ", Hiện có: " + currentTasks.size());
         }
         Set<Long> currentTaskIdsSet = currentTasks.stream().map(Task::getId).collect(Collectors.toSet());
         if (!currentTaskIdsSet.containsAll(orderedTaskIds)) {
             throw new BadRequestException("Danh sách ID công việc không hợp lệ hoặc chứa ID không thuộc kế hoạch này.");
         }
         if (orderedTaskIds.stream().distinct().count() != orderedTaskIds.size()) {
              throw new BadRequestException("Danh sách ID công việc chứa ID trùng lặp.");
         }

         Map<Long, Task> taskMap = currentTasks.stream()
                                              .collect(Collectors.toMap(Task::getId, task -> task));

         List<Task> updatedTasksInOrder = new ArrayList<>();
         List<Task> tasksToSave = new ArrayList<>();
         for (int i = 0; i < orderedTaskIds.size(); i++) {
             Long taskId = orderedTaskIds.get(i);
             Task task = taskMap.get(taskId);
             if (task.getOrder() == null || task.getOrder() != i) {
                 task.setOrder(i);
                 tasksToSave.add(task);
             }
             updatedTasksInOrder.add(task); // Add to the correctly ordered list
         }

         if (!tasksToSave.isEmpty()) {
             taskRepository.saveAll(tasksToSave);
             log.info("Reordered {} tasks for plan {}", tasksToSave.size(), shareableLink);
             // Update the list within the Plan entity to reflect the new order immediately
             plan.setDailyTasks(updatedTasksInOrder);
         } else {
              log.info("No order changes detected for tasks in plan {}", shareableLink);
         }

         // GỬI MESSAGE WEBSOCKET REORDER_TASKS (giữ nguyên)
         String destination = "/topic/plan/" + shareableLink + "/tasks";
         Map<String, Object> payload = Map.of(
             "type", "REORDER_TASKS",
             "orderedTaskIds", orderedTaskIds
         );
         messagingTemplate.convertAndSend(destination, payload);
         log.debug("Sent WebSocket update for task reorder to {}", destination);

         return updatedTasksInOrder.stream() // Return the correctly ordered list
                    .map(taskMapper::toTaskResponse)
                    .collect(Collectors.toList());
     }


    // --- Member & Status Management Methods ---
    @Override
    public void removeMemberFromPlan(String shareableLink, Integer memberUserId, String ownerEmail) {
        Plan plan = findPlanByShareableLink(shareableLink);
        User owner = findUserByEmail(ownerEmail);
        ensureUserIsOwner(plan, owner.getId());

        PlanMember memberToRemove = plan.getMembers().stream()
                .filter(m -> m.getUser() != null && m.getUser().getId().equals(memberUserId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thành viên với ID: " + memberUserId + " trong kế hoạch này."));

        if (memberToRemove.getRole() == MemberRole.OWNER) {
            throw new BadRequestException("Không thể xóa chủ sở hữu kế hoạch.");
        }

        plan.getMembers().remove(memberToRemove); // Remove from collection before deleting
        planMemberRepository.delete(memberToRemove); // Delete entity

        // GỬI MESSAGE WEBSOCKET MEMBER_REMOVED (giữ nguyên)
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
        Plan plan = findPlanByShareableLink(shareableLink);
        User currentOwnerUser = findUserByEmail(currentOwnerEmail);
        Integer newOwnerUserId = request.getNewOwnerUserId();

        PlanMember currentOwnerMember = plan.getMembers().stream()
                .filter(m -> m.getUser() != null && m.getUser().getId().equals(currentOwnerUser.getId()) && m.getRole() == MemberRole.OWNER)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Chỉ chủ sở hữu hiện tại mới có quyền chuyển quyền sở hữu."));

        PlanMember newOwnerMember = plan.getMembers().stream()
                .filter(m -> m.getUser() != null && m.getUser().getId().equals(newOwnerUserId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thành viên với ID: " + newOwnerUserId + " trong kế hoạch này."));

        if (newOwnerMember.getId().equals(currentOwnerMember.getId())) {
            throw new BadRequestException("Bạn không thể chuyển quyền sở hữu cho chính mình.");
        }

        currentOwnerMember.setRole(MemberRole.MEMBER);
        newOwnerMember.setRole(MemberRole.OWNER);

        planMemberRepository.saveAll(Arrays.asList(currentOwnerMember, newOwnerMember));
        log.info("Ownership of plan {} transferred from user {} to user {}", shareableLink, currentOwnerUser.getId(), newOwnerUserId);

        // GỬI MESSAGE WEBSOCKET OWNERSHIP_TRANSFERRED (giữ nguyên)
        String destination = "/topic/plan/" + shareableLink + "/details";
        Map<String, Object> payload = Map.of(
            "type", "OWNERSHIP_TRANSFERRED",
            "oldOwnerUserId", currentOwnerUser.getId(),
            "newOwnerUserId", newOwnerUserId
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.debug("Sent WebSocket update for ownership transfer to {}", destination);
    }

    @Override
    public PlanDetailResponse archivePlan(String shareableLink, String ownerEmail) {
        Plan plan = findPlanByShareableLink(shareableLink);
        User owner = findUserByEmail(ownerEmail);
        ensureUserIsOwner(plan, owner.getId());

        if (plan.getStatus() == PlanStatus.ARCHIVED) {
            throw new BadRequestException("Kế hoạch này đã được lưu trữ.");
        }

        plan.setStatus(PlanStatus.ARCHIVED);
        Plan updatedPlan = planRepository.save(plan);
        PlanDetailResponse response = planMapper.toPlanDetailResponse(updatedPlan);

        // GỬI MESSAGE WEBSOCKET STATUS_CHANGED (giữ nguyên)
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
        Plan plan = findPlanByShareableLink(shareableLink);
        User owner = findUserByEmail(ownerEmail);
        ensureUserIsOwner(plan, owner.getId());

        if (plan.getStatus() != PlanStatus.ARCHIVED) {
            throw new BadRequestException("Kế hoạch này không ở trạng thái lưu trữ.");
        }

        plan.setStatus(PlanStatus.ACTIVE);
        Plan updatedPlan = planRepository.save(plan);
         PlanDetailResponse response = planMapper.toPlanDetailResponse(updatedPlan);

        // GỬI MESSAGE WEBSOCKET STATUS_CHANGED (giữ nguyên)
        String destination = "/topic/plan/" + shareableLink + "/details";
        Map<String, Object> payload = Map.of(
            "type", "STATUS_CHANGED",
            "status", PlanStatus.ACTIVE.name(),
            "displayStatus", response.getDisplayStatus()
        );
        messagingTemplate.convertAndSend(destination, payload);
         log.info("Unarchived plan {}. Sent WebSocket update to {}", shareableLink, destination);

        return response;
    }

    // --- Helper Methods (giữ nguyên) ---
    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + email));
    }
    private Plan findPlanByShareableLink(String link) {
        return planRepository.findByShareableLink(link)
                .map(plan -> { plan.getMembers().size(); return plan; }) // Eager fetch members
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kế hoạch với link: " + link));
    }
    private Plan findPlanByShareableLinkWithTasks(String link) {
        return planRepository.findByShareableLink(link)
                .map(plan -> {
                    plan.getMembers().size();
                    plan.getDailyTasks().size(); // Eager fetch tasks
                    plan.getDailyTasks().sort(Comparator.comparing(Task::getOrder, Comparator.nullsLast(Integer::compareTo)));
                    return plan;
                })
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kế hoạch với link: " + link));
    }
    private boolean isUserMemberOfPlan(Plan plan, Integer userId) {
        return plan.getMembers() != null && plan.getMembers().stream()
                   .anyMatch(m -> m.getUser() != null && m.getUser().getId().equals(userId));
    }
    private void ensureUserIsOwner(Plan plan, Integer userId) {
        if (plan.getMembers() == null) {
             throw new AccessDeniedException("Không tìm thấy thông tin thành viên.");
        }
        boolean isOwner = plan.getMembers().stream()
                .anyMatch(m -> m.getUser() != null && m.getUser().getId().equals(userId) && m.getRole() == MemberRole.OWNER);
        if (!isOwner) {
            throw new AccessDeniedException("Chỉ chủ sở hữu mới có quyền thực hiện hành động này.");
        }
    }
     private PlanDetailResponse.PlanMemberResponse toPlanMemberResponse(PlanMember member) {
        if (member == null) return null; User user = member.getUser();
        return PlanDetailResponse.PlanMemberResponse.builder()
                .userId(user != null ? user.getId() : null).userEmail(user != null ? user.getEmail() : "N/A")
                .userFullName(getUserFullName(user)).role(member.getRole() != null ? member.getRole().name() : "N/A")
                .build();
    }
     private String getUserFullName(User user) {
        if (user == null) return "N/A";
        if (user.getCustomer() != null && user.getCustomer().getFullname() != null && !user.getCustomer().getFullname().isBlank()) { return user.getCustomer().getFullname();}
        if (user.getEmployee() != null && user.getEmployee().getFullname() != null && !user.getEmployee().getFullname().isBlank()) { return user.getEmployee().getFullname();}
        return user.getEmail();
    }
}