package com.example.demo.plan.service;

import com.example.demo.plan.dto.request.CreatePlanRequest;
import com.example.demo.plan.dto.request.UpdatePlanRequest;
import com.example.demo.plan.dto.response.PlanDetailResponse;
import com.example.demo.plan.dto.response.PlanPublicResponse;

public interface PlanService {
    PlanDetailResponse createPlan(CreatePlanRequest request, String creatorEmail);
    PlanDetailResponse joinPlan(String shareableLink, String userEmail);
    Object getPlanDetails(String shareableLink, String userEmail);
    PlanDetailResponse updatePlan(String shareableLink, UpdatePlanRequest request, String userEmail);
    void leavePlan(String shareableLink, String userEmail);
    void deletePlan(String shareableLink, String userEmail);
}