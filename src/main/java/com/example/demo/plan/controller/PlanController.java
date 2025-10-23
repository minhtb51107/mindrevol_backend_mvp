package com.example.demo.plan.controller;

import com.example.demo.plan.dto.request.CreatePlanRequest;
import com.example.demo.plan.dto.request.ManageTaskRequest;
import com.example.demo.plan.dto.request.ReorderTasksRequest; // Giữ import này
import com.example.demo.plan.dto.request.TransferOwnershipRequest;
import com.example.demo.plan.dto.request.UpdatePlanRequest;
import com.example.demo.plan.service.PlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.example.demo.plan.dto.response.PlanSummaryResponse;
import com.example.demo.plan.dto.response.TaskResponse;
import com.example.demo.plan.dto.response.PlanDetailResponse;

@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    // ... (Các endpoints khác giữ nguyên) ...

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createPlan(@Valid @RequestBody CreatePlanRequest request, Authentication authentication) {
        PlanDetailResponse createdPlan = planService.createPlan(request, authentication.getName());
        return new ResponseEntity<>(createdPlan, HttpStatus.CREATED);
    }

    @PostMapping("/{shareableLink}/join")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> joinPlan(@PathVariable String shareableLink, Authentication authentication) {
        PlanDetailResponse joinedPlan = planService.joinPlan(shareableLink, authentication.getName());
        return ResponseEntity.ok(joinedPlan);
    }

    @GetMapping("/{shareableLink}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getPlanDetails(@PathVariable String shareableLink, Authentication authentication) {
        Object planDetails = planService.getPlanDetails(shareableLink, authentication.getName());
        return ResponseEntity.ok(planDetails);
    }

    @PutMapping("/{shareableLink}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updatePlan(@PathVariable String shareableLink, @Valid @RequestBody UpdatePlanRequest request, Authentication authentication) {
        PlanDetailResponse updatedPlan = planService.updatePlan(shareableLink, request, authentication.getName());
        return ResponseEntity.ok(updatedPlan);
    }

    @DeleteMapping("/{shareableLink}/leave")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> leavePlan(@PathVariable String shareableLink, Authentication authentication) {
        planService.leavePlan(shareableLink, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{shareableLink}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deletePlan(@PathVariable String shareableLink, Authentication authentication) {
        planService.deletePlan(shareableLink, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my-plans")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PlanSummaryResponse>> getMyPlans(
            Authentication authentication,
            @RequestParam(required = false) String search
    ) {
        List<PlanSummaryResponse> myPlans = planService.getMyPlans(authentication.getName(), search);
        return ResponseEntity.ok(myPlans);
    }

    // --- Endpoints quản lý Task ---
    @PostMapping("/{shareableLink}/tasks")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TaskResponse> addTaskToPlan(
            @PathVariable String shareableLink,
            @Valid @RequestBody ManageTaskRequest request,
            Authentication authentication) {
        TaskResponse newTask = planService.addTaskToPlan(shareableLink, request, authentication.getName());
        return new ResponseEntity<>(newTask, HttpStatus.CREATED);
    }

    @PutMapping("/{shareableLink}/tasks/{taskId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TaskResponse> updateTaskInPlan(
            @PathVariable String shareableLink,
            @PathVariable Long taskId,
            @Valid @RequestBody ManageTaskRequest request,
            Authentication authentication) {
        TaskResponse updatedTask = planService.updateTaskInPlan(shareableLink, taskId, request, authentication.getName());
        return ResponseEntity.ok(updatedTask);
    }

    @DeleteMapping("/{shareableLink}/tasks/{taskId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteTaskFromPlan(
            @PathVariable String shareableLink,
            @PathVariable Long taskId,
            Authentication authentication) {
        planService.deleteTaskFromPlan(shareableLink, taskId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    // *** THAY ĐỔI ĐƯỜNG DẪN Ở ĐÂY ***
    @PutMapping("/{shareableLink}/task-order") // Sử dụng /task-order thay vì /tasks/order
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TaskResponse>> reorderTasks(
            @PathVariable String shareableLink,
            @Valid @RequestBody ReorderTasksRequest request,
            Authentication authentication) {
        String ownerEmail = authentication.getName();
        List<TaskResponse> reorderedTasks = planService.reorderTasksInPlan(shareableLink, request, ownerEmail);
        return ResponseEntity.ok(reorderedTasks);
    }
    // --- Kết thúc Endpoints quản lý Task ---


    // Endpoints xóa thành viên
    @DeleteMapping("/{shareableLink}/members/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> removeMember(
            @PathVariable String shareableLink,
            @PathVariable Integer userId,
            Authentication authentication) {
        String ownerEmail = authentication.getName();
        planService.removeMemberFromPlan(shareableLink, userId, ownerEmail);
        return ResponseEntity.noContent().build();
    }
    
    @PatchMapping("/{shareableLink}/transfer-ownership") // Dùng PATCH vì cập nhật một phần
    @PreAuthorize("isAuthenticated()") // Owner check is inside service
    public ResponseEntity<Void> transferOwnership(
            @PathVariable String shareableLink,
            @Valid @RequestBody TransferOwnershipRequest request,
            Authentication authentication) {
        String currentOwnerEmail = authentication.getName();
        planService.transferOwnership(shareableLink, request, currentOwnerEmail);
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    // Endpoints quản lý trạng thái
    @PatchMapping("/{shareableLink}/archive")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PlanDetailResponse> archivePlan(
            @PathVariable String shareableLink,
            Authentication authentication) {
        String ownerEmail = authentication.getName();
        PlanDetailResponse updatedPlan = planService.archivePlan(shareableLink, ownerEmail);
        return ResponseEntity.ok(updatedPlan);
    }

    @PatchMapping("/{shareableLink}/unarchive")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PlanDetailResponse> unarchivePlan(
            @PathVariable String shareableLink,
            Authentication authentication) {
        String ownerEmail = authentication.getName();
        PlanDetailResponse updatedPlan = planService.unarchivePlan(shareableLink, ownerEmail);
        return ResponseEntity.ok(updatedPlan);
    }
}