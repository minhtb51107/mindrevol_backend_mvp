// src/test/java/com/example/demo/plan/mapper/PlanMapperTest.java
package com.example.demo.plan.mapper;

import com.example.demo.plan.dto.response.PlanDetailResponse;
import com.example.demo.plan.dto.response.PlanPublicResponse;
import com.example.demo.plan.dto.response.PlanSummaryResponse;
import com.example.demo.plan.entity.MemberRole;
import com.example.demo.plan.entity.Plan;
import com.example.demo.plan.entity.PlanMember;
import com.example.demo.plan.entity.PlanStatus;
import com.example.demo.plan.entity.Task;
import com.example.demo.user.entity.Customer;
import com.example.demo.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;


import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // Optional if not using mocks strictly, but good practice
class PlanMapperTest {

    // If TaskMapper is complex, mock it. If simple or tightly coupled, Spy or instantiate directly.
    @Spy
    private TaskMapper taskMapper = new TaskMapper();

    @InjectMocks // Inject the spy/mock into PlanMapper
    private PlanMapper planMapper;

    private Plan plan;
    private User creator;
    private User memberUser;
    private PlanMember ownerMember;
    private PlanMember regularMember;
    private Task task1;
    private Task task2;

    @BeforeEach
    void setUp() {
        creator = User.builder().id(1).email("creator@example.com").build();
        Customer creatorCustomer = Customer.builder().id(1).fullname("Creator Name").user(creator).build();
        creator.setCustomer(creatorCustomer);

        memberUser = User.builder().id(2).email("member@example.com").build();
        Customer memberCustomer = Customer.builder().id(2).fullname("Member Name").user(memberUser).build();
        memberUser.setCustomer(memberCustomer);

        plan = Plan.builder()
                .id(1)
                .title("Test Plan")
                .description("Plan Description")
                .durationInDays(7)
                .dailyGoal("Goal")
                .shareableLink("link123")
                .creator(creator)
                .startDate(LocalDate.now())
                .status(PlanStatus.ACTIVE)
                .members(new ArrayList<>())
                .dailyTasks(new ArrayList<>())
                .build();

        ownerMember = PlanMember.builder().id(1).plan(plan).user(creator).role(MemberRole.OWNER).build();
        regularMember = PlanMember.builder().id(2).plan(plan).user(memberUser).role(MemberRole.MEMBER).build();
        plan.getMembers().addAll(Arrays.asList(ownerMember, regularMember));

        task1 = Task.builder().id(1L).description("Task 1").order(0).deadlineTime(LocalTime.of(10, 0)).plan(plan).build();
        task2 = Task.builder().id(2L).description("Task 2").order(1).plan(plan).build();
        plan.getDailyTasks().addAll(Arrays.asList(task1, task2));

        // Ensure bidirectional links are set if not done by builders/constructors
        task1.setPlan(plan);
        task2.setPlan(plan);
        ownerMember.setPlan(plan);
        regularMember.setPlan(plan);

    }

    @Test
    void toPlanDetailResponse_MapsCorrectly() {
        PlanDetailResponse response = planMapper.toPlanDetailResponse(plan);

        assertNotNull(response);
        assertEquals(plan.getId(), response.getId());
        assertEquals(plan.getTitle(), response.getTitle());
        assertEquals(plan.getStartDate(), response.getStartDate());
        assertEquals(plan.getStartDate().plusDays(plan.getDurationInDays() - 1), response.getEndDate());
        assertEquals(plan.getStatus(), response.getStatus());
        assertEquals("ACTIVE", response.getDisplayStatus()); // Assuming today is within duration
        assertEquals(2, response.getMembers().size());
        assertEquals(creator.getId(), response.getMembers().get(0).getUserId());
        assertEquals(creator.getEmail(), response.getMembers().get(0).getUserEmail());
        assertEquals("Creator Name", response.getMembers().get(0).getUserFullName());
        assertEquals("OWNER", response.getMembers().get(0).getRole());
        assertEquals(2, response.getDailyTasks().size());
        assertEquals(task1.getId(), response.getDailyTasks().get(0).getId());
        assertEquals(task1.getDescription(), response.getDailyTasks().get(0).getDescription());
        assertEquals(task1.getDeadlineTime(), response.getDailyTasks().get(0).getDeadlineTime());

        // Verify that the dependent mapper (TaskMapper) was called
        // Since it's a @Spy, we can verify calls on it.
         verify(taskMapper, times(2)).toTaskResponse(any(Task.class));
    }

     @Test
    void toPlanDetailResponse_NullPlan_ReturnsNull() {
        assertNull(planMapper.toPlanDetailResponse(null));
    }


    @Test
    void toPlanSummaryResponse_MapsCorrectly() {
        // Map from the perspective of the regular member
        PlanSummaryResponse response = planMapper.toPlanSummaryResponse(regularMember);

         assertNotNull(response);
        assertEquals(plan.getId(), response.getId());
        assertEquals(plan.getTitle(), response.getTitle());
        assertEquals(plan.getDurationInDays(), response.getDurationInDays());
        assertEquals("ACTIVE", response.getDisplayStatus());
        assertEquals(plan.getMembers().size(), response.getMemberCount());
        assertEquals("MEMBER", response.getRole()); // Role of the input PlanMember
    }

      @Test
    void toPlanSummaryResponse_NullMember_ReturnsNull() {
        assertNull(planMapper.toPlanSummaryResponse(null));
    }

     @Test
    void calculateDisplayStatus_ActiveAndNotEnded() {
         LocalDate endDate = LocalDate.now().plusDays(1);
         // Use reflection or make the method public/package-private for testing if needed
         // For now, assume PlanMapper calls it correctly. Test via toPlanDetailResponse/toPlanSummaryResponse.
         plan.setStatus(PlanStatus.ACTIVE);
         PlanDetailResponse response = planMapper.toPlanDetailResponse(plan); // Re-map with current date logic
         assertEquals("ACTIVE", response.getDisplayStatus());
    }

     @Test
    void calculateDisplayStatus_ActiveButEnded() {
         plan.setStatus(PlanStatus.ACTIVE);
         plan.setStartDate(LocalDate.now().minusDays(10)); // Started 10 days ago
         plan.setDurationInDays(7); // Ended 3 days ago
         PlanDetailResponse response = planMapper.toPlanDetailResponse(plan);
         assertEquals("COMPLETED", response.getDisplayStatus());
    }

     @Test
    void calculateDisplayStatus_Archived() {
         plan.setStatus(PlanStatus.ARCHIVED);
         PlanDetailResponse response = planMapper.toPlanDetailResponse(plan);
         assertEquals("ARCHIVED", response.getDisplayStatus());
    }


    @Test
    void toPlanPublicResponse_MapsCorrectly() {
        PlanPublicResponse response = planMapper.toPlanPublicResponse(plan);

        assertNotNull(response);
        assertEquals(plan.getTitle(), response.getTitle());
        assertEquals(plan.getDescription(), response.getDescription());
        assertEquals("Creator Name", response.getCreatorFullName());
        assertEquals(plan.getMembers().size(), response.getMemberCount());
    }

      @Test
    void toPlanPublicResponse_NullPlan_ReturnsNull() {
        assertNull(planMapper.toPlanPublicResponse(null));
    }


    @Test
    void toPlanMemberResponse_MapsCorrectly() {
        PlanDetailResponse.PlanMemberResponse response = planMapper.toPlanMemberResponse(regularMember);

        assertNotNull(response);
        assertEquals(memberUser.getId(), response.getUserId());
        assertEquals(memberUser.getEmail(), response.getUserEmail());
        assertEquals("Member Name", response.getUserFullName());
        assertEquals("MEMBER", response.getRole());
    }

     @Test
    void toPlanMemberResponse_NullMember_ReturnsNull() {
        assertNull(planMapper.toPlanMemberResponse(null));
    }

    // toTaskResponse is implicitly tested via toPlanDetailResponse, but can be tested directly too
    // getUserFullName is implicitly tested via other methods
}