package com.example.demo.plan.controller;

import com.example.demo.plan.dto.request.CreatePlanRequest;
import com.example.demo.plan.dto.request.UpdatePlanRequest;
import com.example.demo.plan.service.PlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createPlan(@Valid @RequestBody CreatePlanRequest request, Authentication authentication) {
        return new ResponseEntity<>(planService.createPlan(request, authentication.getName()), HttpStatus.CREATED);
    }

    @PostMapping("/{shareableLink}/join")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> joinPlan(@PathVariable String shareableLink, Authentication authentication) {
        return ResponseEntity.ok(planService.joinPlan(shareableLink, authentication.getName()));
    }

    @GetMapping("/{shareableLink}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getPlanDetails(@PathVariable String shareableLink, Authentication authentication) {
        return ResponseEntity.ok(planService.getPlanDetails(shareableLink, authentication.getName()));
    }
    
    @PutMapping("/{shareableLink}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updatePlan(@PathVariable String shareableLink, @Valid @RequestBody UpdatePlanRequest request, Authentication authentication) {
        return ResponseEntity.ok(planService.updatePlan(shareableLink, request, authentication.getName()));
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
}