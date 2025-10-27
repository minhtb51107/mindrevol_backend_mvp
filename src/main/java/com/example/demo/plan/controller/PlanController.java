package com.example.demo.plan.controller;

import com.example.demo.plan.dto.request.CreatePlanRequest;
import com.example.demo.plan.dto.request.ManageTaskRequest;
import com.example.demo.plan.dto.request.ReorderTasksRequest;
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
import java.time.LocalDate; // --- THÊM IMPORT ---
import java.util.List;
import com.example.demo.plan.dto.response.PlanSummaryResponse;
import com.example.demo.plan.dto.response.TaskResponse;
import com.example.demo.plan.dto.response.PlanDetailResponse;
import org.springframework.format.annotation.DateTimeFormat; // --- THÊM IMPORT ---

@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    // --- Endpoint tạo Plan ---
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createPlan(@Valid @RequestBody CreatePlanRequest request, Authentication authentication) {
        PlanDetailResponse createdPlan = planService.createPlan(request, authentication.getName());
        return new ResponseEntity<>(createdPlan, HttpStatus.CREATED);
    }

    // --- Endpoint tham gia Plan ---
    @PostMapping("/{shareableLink}/join")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> joinPlan(@PathVariable String shareableLink, Authentication authentication) {
        PlanDetailResponse joinedPlan = planService.joinPlan(shareableLink, authentication.getName());
        return ResponseEntity.ok(joinedPlan);
    }

    // --- Endpoint lấy chi tiết Plan ---
    @GetMapping("/{shareableLink}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getPlanDetails(@PathVariable String shareableLink, Authentication authentication) {
        // Service sẽ trả về PlanDetailResponse (nếu là member) hoặc PlanPublicResponse (nếu không phải)
        Object planDetails = planService.getPlanDetails(shareableLink, authentication.getName());
        return ResponseEntity.ok(planDetails);
    }

    // --- Endpoint cập nhật thông tin Plan ---
    @PutMapping("/{shareableLink}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updatePlan(@PathVariable String shareableLink, @Valid @RequestBody UpdatePlanRequest request, Authentication authentication) {
        PlanDetailResponse updatedPlan = planService.updatePlan(shareableLink, request, authentication.getName());
        return ResponseEntity.ok(updatedPlan);
    }

    // --- Endpoint rời Plan ---
    @DeleteMapping("/{shareableLink}/leave")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> leavePlan(@PathVariable String shareableLink, Authentication authentication) {
        planService.leavePlan(shareableLink, authentication.getName());
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    // --- Endpoint xóa Plan ---
    @DeleteMapping("/{shareableLink}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deletePlan(@PathVariable String shareableLink, Authentication authentication) {
        planService.deletePlan(shareableLink, authentication.getName());
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    // --- Endpoint lấy danh sách Plan của User ---
    @GetMapping("/my-plans")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PlanSummaryResponse>> getMyPlans(
            Authentication authentication,
            @RequestParam(required = false) String search // Tham số tìm kiếm (optional)
    ) {
        List<PlanSummaryResponse> myPlans = planService.getMyPlans(authentication.getName(), search);
        return ResponseEntity.ok(myPlans);
    }

    // --- Endpoints quản lý Task (ĐÃ CẬP NHẬT LOGIC TRONG SERVICE) ---

    // Thêm Task mới vào Plan (cần có taskDate trong request)
    @PostMapping("/{shareableLink}/tasks")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TaskResponse> addTaskToPlan(
            @PathVariable String shareableLink,
            @Valid @RequestBody ManageTaskRequest request, // Request đã có trường taskDate
            Authentication authentication) {
        TaskResponse newTask = planService.addTaskToPlan(shareableLink, request, authentication.getName());
        return new ResponseEntity<>(newTask, HttpStatus.CREATED);
    }

    // Lấy danh sách Task theo ngày cụ thể (cho Cột Phải)
    @GetMapping("/{shareableLink}/tasks-by-date")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TaskResponse>> getTasksByDate(
            @PathVariable String shareableLink,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, // Nhận ngày dạng YYYY-MM-DD
            Authentication authentication
    ) {
        List<TaskResponse> tasks = planService.getTasksByDate(shareableLink, date, authentication.getName());
        return ResponseEntity.ok(tasks);
    }

    // Cập nhật Task (có thể bao gồm cả taskDate để chuyển ngày)
    @PutMapping("/{shareableLink}/tasks/{taskId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TaskResponse> updateTaskInPlan(
            @PathVariable String shareableLink,
            @PathVariable Long taskId,
            @Valid @RequestBody ManageTaskRequest request, // Request có thể chứa taskDate mới
            Authentication authentication) {
        TaskResponse updatedTask = planService.updateTaskInPlan(shareableLink, taskId, request, authentication.getName());
        return ResponseEntity.ok(updatedTask);
    }

    // Xóa Task
    @DeleteMapping("/{shareableLink}/tasks/{taskId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteTaskFromPlan(
            @PathVariable String shareableLink,
            @PathVariable Long taskId,
            Authentication authentication) {
        planService.deleteTaskFromPlan(shareableLink, taskId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    // Sắp xếp lại thứ tự Task trong một ngày
    @PutMapping("/{shareableLink}/task-order")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TaskResponse>> reorderTasks(
            @PathVariable String shareableLink,
            @Valid @RequestBody ReorderTasksRequest request, // Request đã có trường taskDate và orderedTaskIds
            Authentication authentication) {
        String ownerEmail = authentication.getName();
        List<TaskResponse> reorderedTasks = planService.reorderTasksInPlan(shareableLink, request, ownerEmail);
        return ResponseEntity.ok(reorderedTasks);
    }
    // --- Kết thúc Endpoints quản lý Task ---


    // --- Endpoints quản lý Thành viên ---

    // Xóa thành viên khỏi Plan
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

    // Chuyển quyền sở hữu Plan
    @PatchMapping("/{shareableLink}/transfer-ownership")
    @PreAuthorize("isAuthenticated()") // Owner check is inside service
    public ResponseEntity<Void> transferOwnership(
            @PathVariable String shareableLink,
            @Valid @RequestBody TransferOwnershipRequest request,
            Authentication authentication) {
        String currentOwnerEmail = authentication.getName();
        planService.transferOwnership(shareableLink, request, currentOwnerEmail);
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    // --- Endpoints quản lý Trạng thái Plan ---

    // Lưu trữ Plan
    @PatchMapping("/{shareableLink}/archive")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PlanDetailResponse> archivePlan(
            @PathVariable String shareableLink,
            Authentication authentication) {
        String ownerEmail = authentication.getName();
        PlanDetailResponse updatedPlan = planService.archivePlan(shareableLink, ownerEmail);
        return ResponseEntity.ok(updatedPlan);
    }

    // Khôi phục Plan từ lưu trữ
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