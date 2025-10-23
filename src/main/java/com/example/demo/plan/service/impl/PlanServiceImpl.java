package com.example.demo.plan.service.impl;

import com.example.demo.plan.dto.request.CreatePlanRequest;
import com.example.demo.plan.dto.request.ManageTaskRequest;
import com.example.demo.plan.dto.request.ReorderTasksRequest;
import com.example.demo.plan.dto.request.TransferOwnershipRequest;
import com.example.demo.plan.dto.request.UpdatePlanRequest;
import com.example.demo.plan.dto.response.PlanDetailResponse;
import com.example.demo.plan.dto.response.PlanPublicResponse;
import com.example.demo.plan.entity.MemberRole;
import com.example.demo.plan.entity.Plan;
import com.example.demo.plan.entity.PlanMember;
import com.example.demo.plan.entity.PlanStatus; // *** THÊM IMPORT ***
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
import lombok.extern.slf4j.Slf4j; // *** THÊM IMPORT ***
import org.springframework.messaging.simp.SimpMessagingTemplate; // *** THÊM IMPORT ***
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.demo.plan.dto.response.PlanSummaryResponse;
import com.example.demo.plan.dto.response.TaskResponse;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map; // *** THÊM IMPORT ***
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j // *** THÊM ANNOTATION ***
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
    private final SimpMessagingTemplate messagingTemplate; // *** INJECT SimpMessagingTemplate ***


    @Override
    public PlanDetailResponse createPlan(CreatePlanRequest request, String creatorEmail) {
        // ... (Logic tạo plan giữ nguyên) ...
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
                .build();

        if (request.getDailyTasks() != null) {
            IntStream.range(0, request.getDailyTasks().size())
                    .mapToObj(i -> {
                        CreatePlanRequest.TaskRequest taskReq = request.getDailyTasks().get(i);
                        return Task.builder()
                                .description(taskReq.getDescription())
                                .deadlineTime(taskReq.getDeadlineTime())
                                .order(i)
                                .plan(newPlan)
                                .build();
                    })
                    .forEach(newPlan::addTask);
        }

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

        Plan updatedPlan = planRepository.save(plan); // Lưu lại plan để cascade lưu member mới

        // *** GỬI MESSAGE WEBSOCKET KHI JOIN ***
        String destination = "/topic/plan/" + shareableLink + "/details";
        // Lấy thông tin member mới sau khi đã lưu (để có ID nếu cần)
        PlanMember savedNewMember = updatedPlan.getMembers().stream()
                                            .filter(m -> m.getUser().getId().equals(user.getId()))
                                            .findFirst().orElse(newMember); // Fallback nếu không tìm thấy ngay
        PlanDetailResponse.PlanMemberResponse memberResponse = planMapper.toPlanMemberResponse(savedNewMember); // Sử dụng mapper riêng

        Map<String, Object> payload = Map.of(
            "type", "MEMBER_JOINED",
            "member", memberResponse // Gửi thông tin thành viên mới
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.info("User {} joined plan {}. Sent WebSocket update to {}", userEmail, shareableLink, destination);
        // *** KẾT THÚC GỬI WEBSOCKET ***

        return planMapper.toPlanDetailResponse(updatedPlan);
    }

    @Override
    @Transactional(readOnly = true)
    public Object getPlanDetails(String shareableLink, String userEmail) {
        // ... (Giữ nguyên) ...
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
        // ... (Logic cập nhật plan giữ nguyên, không cần gửi WebSocket ở đây vì chỉ Owner thấy thay đổi ngay) ...
        Plan plan = findPlanByShareableLinkWithTasks(shareableLink);
        User user = findUserByEmail(userEmail);
        ensureUserIsOwner(plan, user.getId());

        boolean titleChanged = !Objects.equals(plan.getTitle(), request.getTitle());
        boolean descChanged = !Objects.equals(plan.getDescription(), request.getDescription());
        boolean durationChanged = plan.getDurationInDays() != request.getDurationInDays();
        boolean goalChanged = !Objects.equals(plan.getDailyGoal(), request.getDailyGoal());

        plan.setTitle(request.getTitle());
        plan.setDescription(request.getDescription());
        plan.setDurationInDays(request.getDurationInDays());
        plan.setDailyGoal(request.getDailyGoal());

        // Cập nhật tasks (logic giữ nguyên)
        List<Task> oldTasks = new ArrayList<>(plan.getDailyTasks());
        oldTasks.forEach(plan::removeTask);
        taskRepository.deleteAll(oldTasks);
        plan.getDailyTasks().clear();
        boolean tasksChanged = true; // Giả sử tasks luôn thay đổi khi gọi API này

        if (request.getDailyTasks() != null) {
             IntStream.range(0, request.getDailyTasks().size())
                 .mapToObj(i -> {
                     UpdatePlanRequest.TaskRequest taskReq = request.getDailyTasks().get(i);
                     return Task.builder()
                             .description(taskReq.getDescription())
                             .deadlineTime(taskReq.getDeadlineTime())
                             .order(i)
                             .plan(plan)
                             .build();
                 })
                 .forEach(plan::addTask);
        }

        Plan updatedPlan = planRepository.save(plan);

        // *** GỬI WEBSOCKET NẾU CÓ THAY ĐỔI THÔNG TIN CHUNG ***
        // (Không gửi nếu chỉ task thay đổi, vì task có topic riêng)
        if (titleChanged || descChanged || durationChanged || goalChanged) {
            String destination = "/topic/plan/" + shareableLink + "/details";
             Map<String, Object> payload = Map.of(
                "type", "PLAN_INFO_UPDATED",
                // Gửi các trường đã thay đổi
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
         // *** KẾT THÚC GỬI WEBSOCKET ***

        return planMapper.toPlanDetailResponse(updatedPlan);
    }

    @Override
    public void leavePlan(String shareableLink, String userEmail) {
        // Logic leavePlan giữ nguyên, không cần gửi WebSocket vì chỉ user đó biết mình rời đi
        // ... (Giữ nguyên) ...
        Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);

        PlanMember member = plan.getMembers().stream()
                .filter(m -> m.getUser().getId().equals(user.getId()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Bạn không phải là thành viên của kế hoạch này."));

        if (member.getRole() == MemberRole.OWNER) {
            throw new BadRequestException("Chủ sở hữu không thể rời khỏi kế hoạch. Bạn cần phải xóa kế hoạch.");
        }

        plan.getMembers().remove(member);
        planMemberRepository.delete(member);
         log.info("User {} left plan {}", userEmail, shareableLink);
    }

    @Override
    public void deletePlan(String shareableLink, String userEmail) {
        // Logic deletePlan giữ nguyên, không cần gửi WebSocket vì plan sẽ biến mất
        // ... (Giữ nguyên) ...
         Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);

        ensureUserIsOwner(plan, user.getId());

        planRepository.delete(plan);
         log.info("User {} deleted plan {}", userEmail, shareableLink);
         // Client sẽ tự xử lý khi không fetch được plan này nữa (vd: 404)
    }

    // Các hàm quản lý Task (addTaskToPlan, updateTaskInPlan, deleteTaskFromPlan)
    @Override
    public TaskResponse addTaskToPlan(String shareableLink, ManageTaskRequest request, String userEmail) {
        Plan plan = findPlanByShareableLinkWithTasks(shareableLink);
        User user = findUserByEmail(userEmail);
        ensureUserIsOwner(plan, user.getId());

        int nextOrder = plan.getDailyTasks().stream()
                           .mapToInt(Task::getOrder)
                           .max()
                           .orElse(-1) + 1;

        Task newTask = Task.builder()
                .description(request.getDescription())
                .deadlineTime(request.getDeadlineTime())
                .order(nextOrder)
                .plan(plan)
                .build();

        plan.addTask(newTask);
        Plan savedPlan = planRepository.save(plan);

        Task savedTask = savedPlan.getDailyTasks().stream()
                                  .filter(t -> t.getOrder() == nextOrder && t.getDescription().equals(request.getDescription()))
                                  .findFirst()
                                  .orElseThrow(() -> new IllegalStateException("Không tìm thấy task vừa thêm"));

        TaskResponse taskResponse = taskMapper.toTaskResponse(savedTask);

        // *** GỬI MESSAGE WEBSOCKET KHI THÊM TASK ***
        String destination = "/topic/plan/" + shareableLink + "/tasks";
        Map<String, Object> payload = Map.of(
            "type", "NEW_TASK",
            "task", taskResponse // Gửi thông tin task mới
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.info("Added task {} to plan {}. Sent WebSocket update to {}", savedTask.getId(), shareableLink, destination);
        // *** KẾT THÚC GỬI WEBSOCKET ***

        return taskResponse;
    }

    @Override
    public TaskResponse updateTaskInPlan(String shareableLink, Long taskId, ManageTaskRequest request, String userEmail) {
        Plan plan = findPlanByShareableLink(shareableLink);
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

        // *** GỬI MESSAGE WEBSOCKET KHI SỬA TASK ***
        String destination = "/topic/plan/" + shareableLink + "/tasks";
        Map<String, Object> payload = Map.of(
            "type", "UPDATE_TASK",
            "task", taskResponse // Gửi thông tin task đã sửa
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.info("Updated task {} in plan {}. Sent WebSocket update to {}", taskId, shareableLink, destination);
        // *** KẾT THÚC GỬI WEBSOCKET ***

        return taskResponse;
    }

    @Override
    public void deleteTaskFromPlan(String shareableLink, Long taskId, String userEmail) {
        Plan plan = findPlanByShareableLinkWithTasks(shareableLink);
        User user = findUserByEmail(userEmail);
        ensureUserIsOwner(plan, user.getId());

        Task taskToRemove = plan.getDailyTasks().stream()
                                .filter(t -> t.getId().equals(taskId))
                                .findFirst()
                                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công việc với ID: " + taskId + " trong kế hoạch này."));

        int removedOrder = taskToRemove.getOrder();
        plan.removeTask(taskToRemove);

        plan.getDailyTasks().stream()
            .filter(t -> t.getOrder() > removedOrder)
            .forEach(t -> t.setOrder(t.getOrder() - 1));

        planRepository.save(plan); // Lưu lại plan để cascade xóa task và cập nhật order

        // *** GỬI MESSAGE WEBSOCKET KHI XÓA TASK ***
        String destination = "/topic/plan/" + shareableLink + "/tasks";
        Map<String, Object> payload = Map.of(
            "type", "DELETE_TASK",
            "taskId", taskId // Chỉ cần gửi ID của task đã xóa
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.info("Deleted task {} from plan {}. Sent WebSocket update to {}", taskId, shareableLink, destination);
        // *** KẾT THÚC GỬI WEBSOCKET ***
    }

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

        plan.getMembers().remove(memberToRemove);
        planMemberRepository.delete(memberToRemove);

        // *** GỬI MESSAGE WEBSOCKET KHI XÓA MEMBER ***
        String destination = "/topic/plan/" + shareableLink + "/details";
        Map<String, Object> payload = Map.of(
            "type", "MEMBER_REMOVED",
            "userId", memberUserId // Gửi ID của user bị xóa
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.info("Removed user {} from plan {}. Sent WebSocket update to {}", memberUserId, shareableLink, destination);
        // *** KẾT THÚC GỬI WEBSOCKET ***
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
        PlanDetailResponse response = planMapper.toPlanDetailResponse(updatedPlan); // Map trước khi gửi

        // *** GỬI MESSAGE WEBSOCKET KHI ARCHIVE ***
        String destination = "/topic/plan/" + shareableLink + "/details";
        Map<String, Object> payload = Map.of(
            "type", "STATUS_CHANGED",
            "status", PlanStatus.ARCHIVED.name(), // Gửi tên trạng thái mới
            "displayStatus", response.getDisplayStatus() // Gửi cả displayStatus đã tính toán
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.info("Archived plan {}. Sent WebSocket update to {}", shareableLink, destination);
        // *** KẾT THÚC GỬI WEBSOCKET ***

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

        // *** GỬI MESSAGE WEBSOCKET KHI UNARCHIVE ***
        String destination = "/topic/plan/" + shareableLink + "/details";
        Map<String, Object> payload = Map.of(
            "type", "STATUS_CHANGED",
            "status", PlanStatus.ACTIVE.name(), // Trạng thái mới là ACTIVE
            "displayStatus", response.getDisplayStatus() // Gửi cả displayStatus đã tính toán
        );
        messagingTemplate.convertAndSend(destination, payload);
         log.info("Unarchived plan {}. Sent WebSocket update to {}", shareableLink, destination);
        // *** KẾT THÚC GỬI WEBSOCKET ***

        return response;
    }

    // --- Helper Methods (Giữ nguyên) ---
    private User findUserByEmail(String email) {
        // ... (Giữ nguyên) ...
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + email));
    }

    private Plan findPlanByShareableLink(String link) {
        // ... (Giữ nguyên) ...
         return planRepository.findByShareableLink(link)
                .map(plan -> {
                    plan.getMembers().size(); // Trigger lazy load members
                    return plan;
                })
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kế hoạch với link: " + link));
    }

    private Plan findPlanByShareableLinkWithTasks(String link) {
        // ... (Giữ nguyên) ...
         return planRepository.findByShareableLink(link)
                .map(plan -> {
                    plan.getMembers().size();
                    plan.getDailyTasks().size();
                    plan.getDailyTasks().sort(Comparator.comparing(Task::getOrder, Comparator.nullsLast(Integer::compareTo)));
                    return plan;
                })
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kế hoạch với link: " + link));
    }


    private boolean isUserMemberOfPlan(Plan plan, Integer userId) {
        // ... (Giữ nguyên) ...
        return plan.getMembers() != null && plan.getMembers().stream()
                   .anyMatch(m -> m.getUser() != null && m.getUser().getId().equals(userId));
    }


    private void ensureUserIsOwner(Plan plan, Integer userId) {
        // ... (Giữ nguyên) ...
        if (plan.getMembers() == null) {
             throw new AccessDeniedException("Không tìm thấy thông tin thành viên.");
        }
        boolean isOwner = plan.getMembers().stream()
                .anyMatch(m -> m.getUser() != null && m.getUser().getId().equals(userId) && m.getRole() == MemberRole.OWNER);

        if (!isOwner) {
            throw new AccessDeniedException("Chỉ chủ sở hữu mới có quyền thực hiện hành động này.");
        }
    }


    @Override
    @Transactional(readOnly = true)
    public List<PlanSummaryResponse> getMyPlans(String userEmail, String searchTerm) {
        // ... (Giữ nguyên logic lọc) ...
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
                .collect(Collectors.toList());
    }
    
//    @Override
//    @Transactional(readOnly = true)
//    public List<PlanSummaryResponse> getMyPlans(String userEmail) {
//        User user = findUserByEmail(userEmail);
//        List<PlanMember> planMembers = planMemberRepository.findByUserIdWithPlan(user.getId());
//
//        
//        return planMembers.stream()
//                .map(planMapper::toPlanSummaryResponse)
//                .filter(Objects::nonNull) // Lọc bỏ kết quả null nếu có lỗi mapping
//                .collect(Collectors.toList());
//    }
    
    @Override
    public void transferOwnership(String shareableLink, TransferOwnershipRequest request, String currentOwnerEmail) {
        Plan plan = findPlanByShareableLink(shareableLink); // Load plan và members
        User currentOwnerUser = findUserByEmail(currentOwnerEmail);
        Integer newOwnerUserId = request.getNewOwnerUserId();

        // 1. Đảm bảo người yêu cầu là Owner hiện tại
        PlanMember currentOwnerMember = plan.getMembers().stream()
                .filter(m -> m.getUser() != null && m.getUser().getId().equals(currentOwnerUser.getId()) && m.getRole() == MemberRole.OWNER)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Chỉ chủ sở hữu hiện tại mới có quyền chuyển quyền sở hữu."));

        // 2. Tìm thành viên sẽ trở thành Owner mới
        PlanMember newOwnerMember = plan.getMembers().stream()
                .filter(m -> m.getUser() != null && m.getUser().getId().equals(newOwnerUserId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thành viên với ID: " + newOwnerUserId + " trong kế hoạch này."));

        // 3. Không thể chuyển quyền cho chính mình hoặc cho người đã là Owner
        if (newOwnerMember.getId().equals(currentOwnerMember.getId())) {
            throw new BadRequestException("Bạn không thể chuyển quyền sở hữu cho chính mình.");
        }
        if (newOwnerMember.getRole() == MemberRole.OWNER) {
             throw new BadRequestException("Người dùng này đã là chủ sở hữu."); // Trường hợp hiếm
        }

        // 4. Thay đổi role
        currentOwnerMember.setRole(MemberRole.MEMBER); // Owner cũ thành Member
        newOwnerMember.setRole(MemberRole.OWNER);   // Member được chọn thành Owner

        // 5. Lưu lại thay đổi (Lưu cả hai PlanMember)
        planMemberRepository.saveAll(Arrays.asList(currentOwnerMember, newOwnerMember));
        log.info("Ownership of plan {} transferred from user {} to user {}", shareableLink, currentOwnerUser.getId(), newOwnerUserId);

        // 6. Gửi message WebSocket
        String destination = "/topic/plan/" + shareableLink + "/details";
        Map<String, Object> payload = Map.of(
            "type", "OWNERSHIP_TRANSFERRED",
            "oldOwnerUserId", currentOwnerUser.getId(), // ID Owner cũ
            "newOwnerUserId", newOwnerUserId   // ID Owner mới
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.debug("Sent WebSocket update for ownership transfer to {}", destination);
    }
    

     // *** THÊM HELPER MAP PlanMember SANG DTO RIÊNG ***
     // (Di chuyển từ PlanMapper sang đây hoặc giữ ở cả hai nếu cần)
     private PlanDetailResponse.PlanMemberResponse toPlanMemberResponse(PlanMember member) {
        if (member == null) return null;
        User user = member.getUser();
        return PlanDetailResponse.PlanMemberResponse.builder()
                .userId(user != null ? user.getId() : null)
                .userEmail(user != null ? user.getEmail() : "N/A")
                .userFullName(getUserFullName(user)) // Tái sử dụng helper getUserFullName
                .role(member.getRole() != null ? member.getRole().name() : "N/A")
                .build();
    }

     // *** THÊM HELPER getUserFullName (nếu chưa có) ***
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
     
  // *** THÊM IMPLEMENT REORDER TASK ***
     @Override
     public List<TaskResponse> reorderTasksInPlan(String shareableLink, ReorderTasksRequest request, String ownerEmail) {
         Plan plan = findPlanByShareableLinkWithTasks(shareableLink); // Load cả tasks
         User owner = findUserByEmail(ownerEmail);
         ensureUserIsOwner(plan, owner.getId());

         List<Long> orderedTaskIds = request.getOrderedTaskIds();
         List<Task> currentTasks = plan.getDailyTasks();

         // 1. Validate input: số lượng ID phải khớp và tất cả ID phải thuộc plan này
         if (orderedTaskIds.size() != currentTasks.size()) {
             throw new BadRequestException("Số lượng công việc không khớp. Yêu cầu: " + orderedTaskIds.size() + ", Hiện có: " + currentTasks.size());
         }
         Set<Long> currentTaskIdsSet = currentTasks.stream().map(Task::getId).collect(Collectors.toSet());
         if (!currentTaskIdsSet.containsAll(orderedTaskIds)) {
             throw new BadRequestException("Danh sách ID công việc không hợp lệ hoặc chứa ID không thuộc kế hoạch này.");
         }
         // Kiểm tra ID trùng lặp trong request (mặc dù Set check ở trên đã bao gồm)
         if (orderedTaskIds.stream().distinct().count() != orderedTaskIds.size()) {
              throw new BadRequestException("Danh sách ID công việc chứa ID trùng lặp.");
         }


         // 2. Tạo map để truy cập Task nhanh bằng ID
         Map<Long, Task> taskMap = currentTasks.stream()
                                              .collect(Collectors.toMap(Task::getId, task -> task));

         // 3. Cập nhật trường 'order' cho từng Task theo thứ tự mới
         List<Task> updatedTasks = new ArrayList<>();
         for (int i = 0; i < orderedTaskIds.size(); i++) {
             Long taskId = orderedTaskIds.get(i);
             Task task = taskMap.get(taskId);
             if (task.getOrder() != i) { // Chỉ cập nhật nếu thứ tự thay đổi
                 task.setOrder(i);
                 updatedTasks.add(task); // Thêm vào list cần lưu
             }
         }

         // 4. Lưu các Task đã thay đổi order
         if (!updatedTasks.isEmpty()) {
             taskRepository.saveAll(updatedTasks); // Lưu hàng loạt
             log.info("Reordered {} tasks for plan {}", updatedTasks.size(), shareableLink);

             // Cập nhật lại list trong entity Plan để trả về đúng thứ tự
             plan.getDailyTasks().sort(Comparator.comparing(Task::getOrder, Comparator.nullsLast(Integer::compareTo)));
         } else {
              log.info("No order changes detected for tasks in plan {}", shareableLink);
         }

         // 5. Gửi message WebSocket
         String destination = "/topic/plan/" + shareableLink + "/tasks";
         Map<String, Object> payload = Map.of(
             "type", "REORDER_TASKS",
             "orderedTaskIds", orderedTaskIds // Gửi danh sách ID theo thứ tự mới
         );
         messagingTemplate.convertAndSend(destination, payload);
         log.debug("Sent WebSocket update for task reorder to {}", destination);

         // 6. Trả về danh sách TaskResponse theo thứ tự mới
         return plan.getDailyTasks().stream() // Lấy từ list đã sắp xếp lại
                    .map(taskMapper::toTaskResponse)
                    .collect(Collectors.toList());
     }
     // *** KẾT THÚC IMPLEMENT ***
}