package com.example.demo.plan.service;

import com.example.demo.plan.dto.request.CreatePlanRequest;
import com.example.demo.plan.dto.request.ManageTaskRequest;
import com.example.demo.plan.dto.request.ReorderTasksRequest; // *** THÊM IMPORT ***
import com.example.demo.plan.dto.request.TransferOwnershipRequest;
import com.example.demo.plan.dto.request.UpdatePlanRequest;
import com.example.demo.plan.dto.response.PlanDetailResponse;
import com.example.demo.plan.dto.response.PlanPublicResponse;
import com.example.demo.plan.dto.response.PlanSummaryResponse;
import com.example.demo.plan.dto.response.TaskResponse;
import java.util.List;


public interface PlanService {
    PlanDetailResponse createPlan(CreatePlanRequest request, String creatorEmail);
    PlanDetailResponse joinPlan(String shareableLink, String userEmail);
    Object getPlanDetails(String shareableLink, String userEmail);
    PlanDetailResponse updatePlan(String shareableLink, UpdatePlanRequest request, String userEmail);
    void leavePlan(String shareableLink, String userEmail);
    void deletePlan(String shareableLink, String userEmail);
    List<PlanSummaryResponse> getMyPlans(String userEmail, String searchTerm);

    // Quản lý Task
    TaskResponse addTaskToPlan(String shareableLink, ManageTaskRequest request, String userEmail);
    TaskResponse updateTaskInPlan(String shareableLink, Long taskId, ManageTaskRequest request, String userEmail);
    void deleteTaskFromPlan(String shareableLink, Long taskId, String userEmail);
    // *** THÊM PHƯƠNG THỨC REORDER TASK ***
    /**
     * Owner sắp xếp lại thứ tự các công việc trong kế hoạch.
     * @param shareableLink Link của kế hoạch.
     * @param request DTO chứa danh sách ID công việc theo thứ tự mới.
     * @param ownerEmail Email của người yêu cầu (phải là Owner).
     * @return Danh sách các TaskResponse theo thứ tự mới (hoặc void nếu không cần trả về).
     */
    List<TaskResponse> reorderTasksInPlan(String shareableLink, ReorderTasksRequest request, String ownerEmail);
    // *** KẾT THÚC THÊM ***

    // Quản lý Thành viên
    void removeMemberFromPlan(String shareableLink, Integer memberUserId, String ownerEmail);
    
    /**
     * Chủ sở hữu hiện tại chuyển quyền sở hữu kế hoạch cho một thành viên khác.
     * @param shareableLink Link của kế hoạch.
     * @param request DTO chứa ID của chủ sở hữu mới.
     * @param currentOwnerEmail Email của chủ sở hữu hiện tại.
     */
    void transferOwnership(String shareableLink, TransferOwnershipRequest request, String currentOwnerEmail);

    // Quản lý Trạng thái
    PlanDetailResponse archivePlan(String shareableLink, String ownerEmail);
    PlanDetailResponse unarchivePlan(String shareableLink, String ownerEmail);
}