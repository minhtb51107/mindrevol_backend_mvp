package com.example.demo.plan.service;

import com.example.demo.plan.dto.request.CreatePlanRequest;
import com.example.demo.plan.dto.request.ManageTaskRequest;
import com.example.demo.plan.dto.request.ReorderTasksRequest;
import com.example.demo.plan.dto.request.TransferOwnershipRequest;
import com.example.demo.plan.dto.request.UpdatePlanRequest;
import com.example.demo.plan.dto.response.PlanDetailResponse;
import com.example.demo.plan.dto.response.PlanSummaryResponse;
import com.example.demo.plan.dto.response.TaskResponse;

import java.time.LocalDate; // --- THÊM IMPORT ---
import java.util.List;

public interface PlanService {

    PlanDetailResponse createPlan(CreatePlanRequest request, String creatorEmail);

    PlanDetailResponse joinPlan(String shareableLink, String userEmail);

    Object getPlanDetails(String shareableLink, String userEmail);

    PlanDetailResponse updatePlan(String shareableLink, UpdatePlanRequest request, String userEmail);

    void leavePlan(String shareableLink, String userEmail);

    void deletePlan(String shareableLink, String userEmail);

    List<PlanSummaryResponse> getMyPlans(String userEmail, String searchTerm);

    // --- Task Management Methods ---
    TaskResponse addTaskToPlan(String shareableLink, ManageTaskRequest request, String userEmail);

    TaskResponse updateTaskInPlan(String shareableLink, Long taskId, ManageTaskRequest request, String userEmail);

    void deleteTaskFromPlan(String shareableLink, Long taskId, String userEmail);

    List<TaskResponse> reorderTasksInPlan(String shareableLink, ReorderTasksRequest request, String ownerEmail);

    // --- THÊM MỚI ---
    /**
     * Lấy danh sách công việc (Task List) cho Cột Phải theo ngày được chọn.
     */
    List<TaskResponse> getTasksByDate(String shareableLink, LocalDate date, String userEmail);
    // --- KẾT THÚC THÊM ---


    // --- Member & Status Management Methods ---
    void removeMemberFromPlan(String shareableLink, Integer memberUserId, String ownerEmail);

    void transferOwnership(String shareableLink, TransferOwnershipRequest request, String currentOwnerEmail);

    PlanDetailResponse archivePlan(String shareableLink, String ownerEmail);

    PlanDetailResponse unarchivePlan(String shareableLink, String ownerEmail);
}