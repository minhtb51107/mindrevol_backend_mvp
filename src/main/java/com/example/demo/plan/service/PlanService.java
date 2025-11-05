package com.example.demo.plan.service;

import com.example.demo.plan.dto.request.CreatePlanRequest;
import com.example.demo.plan.dto.request.CreatePlanWithScheduleRequest;
import com.example.demo.plan.dto.request.ManageTaskRequest;
import com.example.demo.plan.dto.request.ReorderTasksRequest;
import com.example.demo.plan.dto.request.TransferOwnershipRequest;
import com.example.demo.plan.dto.request.UpdatePlanDetailsRequest;
import com.example.demo.plan.dto.request.UpdatePlanRequest;
import com.example.demo.plan.dto.response.PlanDetailResponse;
import com.example.demo.plan.dto.response.PlanSummaryResponse;
import com.example.demo.plan.dto.response.TaskResponse;

import java.time.LocalDate;
import java.util.List;

public interface PlanService {

    PlanDetailResponse createPlan(CreatePlanRequest request, String creatorEmail);

    PlanDetailResponse joinPlan(String shareableLink, String userEmail);

    Object getPlanDetails(String shareableLink, String userEmail);

    PlanDetailResponse updatePlan(String shareableLink, UpdatePlanRequest request, String userEmail);

    PlanDetailResponse updatePlanDetails(String shareableLink, UpdatePlanDetailsRequest request, String userEmail);

    void leavePlan(String shareableLink, String userEmail);

    // void deletePlan(String shareableLink, String userEmail); // Đã comment out

    List<PlanSummaryResponse> getMyPlans(String userEmail, String searchTerm);

    TaskResponse addTaskToPlan(String shareableLink, ManageTaskRequest request, String userEmail);

    TaskResponse updateTaskInPlan(String shareableLink, Long taskId, ManageTaskRequest request, String userEmail);

    void deleteTaskFromPlan(String shareableLink, Long taskId, String userEmail);

    List<TaskResponse> reorderTasksInPlan(String shareableLink, ReorderTasksRequest request, String ownerEmail);

    List<TaskResponse> getTasksByDate(String shareableLink, LocalDate date, String userEmail);

    void removeMemberFromPlan(String shareableLink, Integer memberUserId, String ownerEmail);

    void transferOwnership(String shareableLink, TransferOwnershipRequest request, String currentOwnerEmail);

    PlanDetailResponse archivePlan(String shareableLink, String ownerEmail);

    PlanDetailResponse unarchivePlan(String shareableLink, String ownerEmail);

    // --- THÊM PHƯƠNG THỨC MỚI ---
    /**
     * Xóa vĩnh viễn một kế hoạch (Hard Delete).
     * Chỉ thực hiện được nếu user là Owner VÀ kế hoạch đang ở trạng thái ARCHIVED.
     *
     * @param shareableLink Link của kế hoạch
     * @param ownerEmail    Email của người thực hiện
     */
    void deletePlanPermanently(String shareableLink, String ownerEmail);
    
    PlanDetailResponse createPlanWithSchedule(CreatePlanWithScheduleRequest request, String creatorEmail);
    
    void nudgeMember(String shareableLink, Integer targetUserId, String nudgerEmail);
}