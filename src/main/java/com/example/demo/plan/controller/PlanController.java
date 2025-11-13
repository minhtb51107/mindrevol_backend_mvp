package com.example.demo.plan.controller;

import com.example.demo.plan.dto.request.*;
import com.example.demo.plan.dto.response.PlanDetailResponse;
import com.example.demo.plan.dto.response.PlanPublicResponse;
import com.example.demo.plan.dto.response.PlanSummaryResponse;
import com.example.demo.plan.dto.response.TaskResponse;
import com.example.demo.plan.service.PlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.example.demo.plan.dto.request.CreateJourneyRequest;
import com.example.demo.plan.dto.response.JourneySummaryResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;
    
    @PostMapping("/journeys")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<JourneySummaryResponse> createJourney(@Valid @RequestBody CreateJourneyRequest request) {
        JourneySummaryResponse response = planService.createJourney(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // Endpoint này dùng cho "Tạo Nhanh"
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PlanDetailResponse> createPlan(
            @Valid @RequestBody CreatePlanRequest request,
            Authentication authentication
    ) {
        String email = authentication.getName();
        PlanDetailResponse response = planService.createPlan(request, email);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // Endpoint này dùng cho "Wizard Bước 2"
    @PostMapping("/with-schedule")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PlanDetailResponse> createPlanWithSchedule(
            @Valid @RequestBody CreatePlanWithScheduleRequest request,
            Authentication authentication
    ) {
        String email = authentication.getName();
        PlanDetailResponse response = planService.createPlanWithSchedule(request, email);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/{shareableLink}/join")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PlanDetailResponse> joinPlan(
            @PathVariable String shareableLink,
            Authentication authentication
    ) {
        String email = authentication.getName();
        PlanDetailResponse response = planService.joinPlan(shareableLink, email);
        return ResponseEntity.ok(response);
    }

    // Dùng cho cả public và member (PlanService sẽ tự xử lý)
    @GetMapping("/{shareableLink}")
    @PreAuthorize("isAuthenticated()") // Vẫn yêu cầu đăng nhập để xem
    public ResponseEntity<?> getPlanDetails(
            @PathVariable String shareableLink,
            Authentication authentication
    ) {
        String email = authentication.getName();
        Object response = planService.getPlanDetails(shareableLink, email);
        
        // Trả về DTO tương ứng (Public hoặc Detail)
        if (response instanceof PlanDetailResponse) {
            return ResponseEntity.ok((PlanDetailResponse) response);
        } else if (response instanceof PlanPublicResponse) {
            return ResponseEntity.ok((PlanPublicResponse) response);
        } else {
            // Trường hợp không mong muốn
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Dùng cho trang Dashboard (lấy plan của tôi)
    @GetMapping("/my-plans")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PlanSummaryResponse>> getMyPlans(
            @RequestParam(value = "search", required = false, defaultValue = "") String searchTerm,
            Authentication authentication
    ) {
        String email = authentication.getName();
        List<PlanSummaryResponse> plans = planService.getMyPlans(email, searchTerm);
        return ResponseEntity.ok(plans);
    }

    // === PHƯƠNG THỨC ĐÃ SỬA LỖI ===
    @GetMapping("/{shareableLink}/tasks")
    // SỬA DÒNG DƯỚI ĐÂY: "isPlanMember" -> "isMember"
    @PreAuthorize("@planSecurity.isMember(#shareableLink, authentication.name)")
    public ResponseEntity<List<TaskResponse>> getTasksForDay(
            @PathVariable String shareableLink,
            @RequestParam("date") LocalDate date,
            Authentication authentication
    ) {
        String email = authentication.getName();
        List<TaskResponse> tasks = planService.getTasksByDate(shareableLink, date, email);
        return ResponseEntity.ok(tasks);
    }
    // === KẾT THÚC PHẦN SỬA LỖI ===

    @PatchMapping("/{shareableLink}/details")
    // SỬA DÒNG DƯỚI ĐÂY: "isPlanOwner" -> "isOwner"
    @PreAuthorize("@planSecurity.isOwner(#shareableLink, authentication.name)")
    public ResponseEntity<PlanDetailResponse> updatePlanDetails(
            @PathVariable String shareableLink,
            @Valid @RequestBody UpdatePlanDetailsRequest request,
            Authentication authentication
    ) {
        String email = authentication.getName();
        PlanDetailResponse response = planService.updatePlanDetails(shareableLink, request, email);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{shareableLink}/leave")
    // SỬA DÒNG DƯỚI ĐÂY: Dùng hàm "isMemberAndNotOwner" đã có trong file PlanSecurity
    @PreAuthorize("@planSecurity.isMemberAndNotOwner(#shareableLink, authentication.name)")
    public ResponseEntity<Void> leavePlan(
            @PathVariable String shareableLink,
            Authentication authentication
    ) {
        String email = authentication.getName();
        planService.leavePlan(shareableLink, email);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{shareableLink}/members/{userId}")
    // SỬA DÒNG DƯỚI ĐÂY: "isPlanOwner" -> "isOwner"
    @PreAuthorize("@planSecurity.isOwner(#shareableLink, authentication.name)")
    public ResponseEntity<Void> removeMember(
            @PathVariable String shareableLink,
            @PathVariable Integer userId,
            Authentication authentication
    ) {
        String email = authentication.getName();
        planService.removeMemberFromPlan(shareableLink, userId, email);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{shareableLink}/transfer-ownership")
    // SỬA DÒNG DƯỚI ĐÂY: "isPlanOwner" -> "isOwner"
    @PreAuthorize("@planSecurity.isOwner(#shareableLink, authentication.name)")
    public ResponseEntity<Void> transferOwnership(
            @PathVariable String shareableLink,
            @Valid @RequestBody TransferOwnershipRequest request,
            Authentication authentication
    ) {
        String email = authentication.getName();
        planService.transferOwnership(shareableLink, request, email);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{shareableLink}/archive")
    // SỬA DÒNG DƯỚI ĐÂY: "isPlanOwner" -> "isOwner"
    @PreAuthorize("@planSecurity.isOwner(#shareableLink, authentication.name)")
    public ResponseEntity<PlanDetailResponse> archivePlan(
            @PathVariable String shareableLink,
            Authentication authentication
    ) {
        String email = authentication.getName();
        PlanDetailResponse response = planService.archivePlan(shareableLink, email);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{shareableLink}/unarchive")
    // SỬA DÒNG DƯỚI ĐÂY: "isPlanOwner" -> "isOwnerRegardlessOfStatus" (vì plan đang bị archived)
    @PreAuthorize("@planSecurity.isOwnerRegardlessOfStatus(#shareableLink, authentication.name)")
    public ResponseEntity<PlanDetailResponse> unarchivePlan(
            @PathVariable String shareableLink,
            Authentication authentication
    ) {
        String email = authentication.getName();
        PlanDetailResponse response = planService.unarchivePlan(shareableLink, email);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{shareableLink}/permanent-delete")
    // SỬA DÒNG DƯỚI ĐÂY: "isPlanOwner" -> "isOwnerRegardlessOfStatus"
    @PreAuthorize("@planSecurity.isOwnerRegardlessOfStatus(#shareableLink, authentication.name)")
    public ResponseEntity<Map<String, String>> deletePlanPermanently(
            @PathVariable String shareableLink,
            Authentication authentication
    ) {
        String email = authentication.getName();
        planService.deletePlanPermanently(shareableLink, email);
        return ResponseEntity.ok(Map.of("message", "Kế hoạch đã được xóa vĩnh viễn."));
    }
    
    // ----- CÁC ENDPOINT LIÊN QUAN ĐẾN TASK -----

    @PostMapping("/{shareableLink}/tasks")
    // SỬA DÒNG DƯỚI ĐÂY: "isPlanOwner" -> "isOwner"
    @PreAuthorize("@planSecurity.isOwner(#shareableLink, authentication.name)")
    public ResponseEntity<TaskResponse> addTaskToPlan(
            @PathVariable String shareableLink,
            @Valid @RequestBody ManageTaskRequest request,
            Authentication authentication
    ) {
        String email = authentication.getName();
        TaskResponse response = planService.addTaskToPlan(shareableLink, request, email);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{shareableLink}/tasks/{taskId}")
    // SỬA DÒNG DƯỚI ĐÂY: "isPlanOwner" -> "isOwner"
    @PreAuthorize("@planSecurity.isOwner(#shareableLink, authentication.name)")
    public ResponseEntity<TaskResponse> updateTask(
            @PathVariable String shareableLink,
            @PathVariable Long taskId,
            @Valid @RequestBody ManageTaskRequest request,
            Authentication authentication
    ) {
        String email = authentication.getName();
        TaskResponse response = planService.updateTaskInPlan(shareableLink, taskId, request, email);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{shareableLink}/tasks/{taskId}")
    // SỬA DÒNG DƯỚI ĐÂY: "isPlanOwner" -> "isOwner"
    @PreAuthorize("@planSecurity.isOwner(#shareableLink, authentication.name)")
    public ResponseEntity<Void> deleteTask(
            @PathVariable String shareableLink,
            @PathVariable Long taskId,
            Authentication authentication
    ) {
        String email = authentication.getName();
        planService.deleteTaskFromPlan(shareableLink, taskId, email);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{shareableLink}/tasks/reorder")
    // SỬA DÒNG DƯỚI ĐÂY: "isPlanOwner" -> "isOwner"
    @PreAuthorize("@planSecurity.isOwner(#shareableLink, authentication.name)")
    public ResponseEntity<List<TaskResponse>> reorderTasks(
            @PathVariable String shareableLink,
            @Valid @RequestBody ReorderTasksRequest request,
            Authentication authentication
    ) {
        String email = authentication.getName();
        List<TaskResponse> responses = planService.reorderTasksInPlan(shareableLink, request, email);
        return ResponseEntity.ok(responses);
    }
    
    @PostMapping("/{shareableLink}/nudge/{userId}")
    @PreAuthorize("@planSecurity.isMember(#shareableLink, authentication.name)")
    public ResponseEntity<Void> nudgeMember(
            @PathVariable String shareableLink,
            @PathVariable Integer userId,
            Authentication authentication
    ) {
        String email = authentication.getName();
        planService.nudgeMember(shareableLink, userId, email);
        return ResponseEntity.ok().build();
    }
}