// src/test/java/com/example/demo/plan/controller/PlanControllerTest.java
package com.example.demo.plan.controller;

import com.example.demo.config.SecurityConfig;
import com.example.demo.config.security.JwtAuthenticationFilter;
import com.example.demo.plan.dto.request.CreatePlanRequest;
import com.example.demo.plan.dto.response.PlanDetailResponse;
import com.example.demo.plan.service.PlanService;
import com.example.demo.shared.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule; // Needed for LocalDate
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PlanController.class)
//@Import(SecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false) // ⛔ Bỏ qua SecurityFilterChain hoàn toàn
class PlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Configure ObjectMapper to handle Java 8 dates
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());


    @MockBean
    private PlanService planService;

    // Mock beans required by SecurityConfig/Filters
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    private PlanDetailResponse planDetailResponse;
    private CreatePlanRequest createPlanRequest;
    private String userEmail = "test@example.com";

     @BeforeEach
    void setUp() {
        // Setup shared objects for tests
        planDetailResponse = PlanDetailResponse.builder()
                .id(1)
                .title("Test Plan")
                .shareableLink("link123")
                .startDate(LocalDate.now().plusDays(1))
                .durationInDays(7)
                .members(Collections.emptyList())
                .dailyTasks(Collections.emptyList())
                .build();

        createPlanRequest = new CreatePlanRequest();
        createPlanRequest.setTitle("New Test Plan");
        createPlanRequest.setDurationInDays(5);
        createPlanRequest.setStartDate(LocalDate.now().plusDays(2));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void createPlan_ValidRequest_ReturnsCreated() throws Exception {
        when(planService.createPlan(any(CreatePlanRequest.class), eq(userEmail))).thenReturn(planDetailResponse);

        mockMvc.perform(post("/api/v1/plans")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPlanRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(planDetailResponse.getId()))
                .andExpect(jsonPath("$.title").value(planDetailResponse.getTitle()));

        verify(planService).createPlan(any(CreatePlanRequest.class), eq(userEmail));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void createPlan_InvalidRequest_ReturnsBadRequest() throws Exception {
        CreatePlanRequest invalidRequest = new CreatePlanRequest(); // Missing required fields

        mockMvc.perform(post("/api/v1/plans")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(planService, never()).createPlan(any(), anyString());
    }

     @Test
    void createPlan_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/plans")
                         .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPlanRequest)))
                .andExpect(status().isUnauthorized());
         verify(planService, never()).createPlan(any(), anyString());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void joinPlan_ValidLink_ReturnsOk() throws Exception {
         String link = "link123";
        when(planService.joinPlan(eq(link), eq(userEmail))).thenReturn(planDetailResponse);

        mockMvc.perform(post("/api/v1/plans/{shareableLink}/join", link)
                         .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(planDetailResponse.getId()));

        verify(planService).joinPlan(eq(link), eq(userEmail));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void getPlanDetails_ValidLink_ReturnsOk() throws Exception {
         String link = "link123";
         // Service returns Object which could be PlanDetailResponse or PlanPublicResponse
        when(planService.getPlanDetails(eq(link), eq(userEmail))).thenReturn(planDetailResponse);

        mockMvc.perform(get("/api/v1/plans/{shareableLink}", link))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(planDetailResponse.getId())); // Check field from Detail response

        verify(planService).getPlanDetails(eq(link), eq(userEmail));
    }

     // Add tests for other PlanController endpoints:
     // updatePlan, leavePlan, deletePlan, getMyPlans,
     // addTaskToPlan, updateTaskInPlan, deleteTaskFromPlan, reorderTasks
     // removeMember, transferOwnership, archivePlan, unarchivePlan
     // Remember to use @WithMockUser for authenticated endpoints
     // Test different scenarios (success, failure, access denied, not found)
}