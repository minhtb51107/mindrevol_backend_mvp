//// src/test/java/com/example/demo/plan/service/impl/PlanServiceImplTest.java
//package com.example.demo.plan.service.impl;
//
//import com.example.demo.plan.dto.request.CreatePlanRequest;
//import com.example.demo.plan.dto.request.UpdatePlanRequest;
//import com.example.demo.plan.dto.response.PlanDetailResponse;
//import com.example.demo.plan.dto.response.PlanPublicResponse;
//import com.example.demo.plan.entity.MemberRole;
//import com.example.demo.plan.entity.Plan;
//import com.example.demo.plan.entity.PlanMember;
//import com.example.demo.plan.entity.PlanStatus;
//import com.example.demo.plan.mapper.PlanMapper;
//import com.example.demo.plan.mapper.TaskMapper;
//import com.example.demo.plan.repository.PlanMemberRepository;
//import com.example.demo.plan.repository.PlanRepository;
//import com.example.demo.plan.repository.TaskRepository;
//import com.example.demo.shared.exception.BadRequestException;
//import com.example.demo.shared.exception.ResourceNotFoundException;
//import com.example.demo.user.entity.User;
//import com.example.demo.user.repository.UserRepository;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.Spy;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.messaging.simp.SimpMessagingTemplate;
//import org.springframework.security.access.AccessDeniedException;
//
//import java.time.LocalDate;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.List;
//import java.util.Optional;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//class PlanServiceImplTest {
//
//    @Mock private PlanRepository planRepository;
//    @Mock private UserRepository userRepository;
//    @Mock private PlanMemberRepository planMemberRepository;
//    @Mock private TaskRepository taskRepository; // Mocked, used in updatePlan and task operations
//    @Mock private SimpMessagingTemplate messagingTemplate;
//
//    // Use Spy for mappers to test real mapping logic if needed, or Mock otherwise
//    @Spy private TaskMapper taskMapper = new TaskMapper();
//    @Spy private PlanMapper planMapper = new PlanMapper(taskMapper); // Inject dependency
//
//    @InjectMocks
//    private PlanServiceImpl planService;
//
//    private User creator;
//    private User member;
//    private Plan plan;
//    private PlanMember creatorMember;
//    private PlanMember regularMember;
//    private CreatePlanRequest createPlanRequest;
//    private UpdatePlanRequest updatePlanRequest;
//
//    @BeforeEach
//    void setUp() {
//        creator = User.builder().id(1).email("creator@example.com").build();
//        member = User.builder().id(2).email("member@example.com").build();
//
//        plan = Plan.builder()
//                .id(1)
//                .title("Original Title")
//                .shareableLink("link123")
//                .creator(creator)
//                .startDate(LocalDate.now().plusDays(1))
//                .durationInDays(7)
//                .status(PlanStatus.ACTIVE)
//                .members(new ArrayList<>()) // Initialize lists
//                .dailyTasks(new ArrayList<>())
//                .build();
//
//        creatorMember = PlanMember.builder().id(1).user(creator).plan(plan).role(MemberRole.OWNER).build();
//        regularMember = PlanMember.builder().id(2).user(member).plan(plan).role(MemberRole.MEMBER).build();
//        plan.getMembers().addAll(Arrays.asList(creatorMember, regularMember)); // Add members
//
//        createPlanRequest = new CreatePlanRequest();
//        createPlanRequest.setTitle("New Plan");
//        createPlanRequest.setDurationInDays(10);
//        createPlanRequest.setStartDate(LocalDate.now().plusDays(2));
//        // Add tasks if needed for specific tests
//
//        updatePlanRequest = new UpdatePlanRequest();
//        updatePlanRequest.setTitle("Updated Title");
//        updatePlanRequest.setDurationInDays(5);
//        // Add tasks if needed
//    }
//
//    @Test
//    void createPlan_Success() {
//        when(userRepository.findByEmail(creator.getEmail())).thenReturn(Optional.of(creator));
//        when(planRepository.save(any(Plan.class))).thenAnswer(invocation -> {
//            Plan p = invocation.getArgument(0);
//            p.setId(2); // Simulate save
//            // Simulate cascade save of owner member
//            p.getMembers().get(0).setId(3);
//            return p;
//        });
//
//        PlanDetailResponse response = planService.createPlan(createPlanRequest, creator.getEmail());
//
//        assertNotNull(response);
//        assertEquals(createPlanRequest.getTitle(), response.getTitle());
//        assertEquals(1, response.getMembers().size()); // Only creator initially
//        assertEquals(creator.getEmail(), response.getMembers().get(0).getUserEmail());
//        assertEquals("OWNER", response.getMembers().get(0).getRole());
//        verify(planRepository).save(any(Plan.class));
//    }
//
//    @Test
//    void createPlan_StartDateInPast() {
//        createPlanRequest.setStartDate(LocalDate.now().minusDays(1));
//        when(userRepository.findByEmail(creator.getEmail())).thenReturn(Optional.of(creator));
//
//        BadRequestException exception = assertThrows(BadRequestException.class,
//                () -> planService.createPlan(createPlanRequest, creator.getEmail()));
//        assertEquals("Ngày bắt đầu không thể là một ngày trong quá khứ.", exception.getMessage());
//        verify(planRepository, never()).save(any());
//    }
//
//
//    @Test
//    void joinPlan_Success() {
//        // Member wants to join the plan (plan initially only has creator)
//        plan.getMembers().remove(regularMember); // Remove member for this test
//        when(planRepository.findByShareableLink("link123")).thenReturn(Optional.of(plan));
//        when(userRepository.findByEmail(member.getEmail())).thenReturn(Optional.of(member));
//        when(planRepository.save(any(Plan.class))).thenAnswer(invocation -> {
//             // Simulate adding the member during save
//            Plan p = invocation.getArgument(0);
//            if(p.getMembers().stream().noneMatch(m -> m.getUser().getId().equals(member.getId()))){
//                 PlanMember newMember = PlanMember.builder().id(4).user(member).plan(p).role(MemberRole.MEMBER).build();
//                 p.getMembers().add(newMember);
//            }
//            return p;
//        });
//
//
//        PlanDetailResponse response = planService.joinPlan("link123", member.getEmail());
//
//        assertNotNull(response);
//        assertEquals(2, response.getMembers().size()); // Creator + new member
//        assertTrue(response.getMembers().stream().anyMatch(m -> m.getUserEmail().equals(member.getEmail())));
//        verify(planRepository).save(plan);
//        verify(messagingTemplate).convertAndSend(eq("/topic/plan/link123/details"), anyMap());
//    }
//
//    @Test
//    void joinPlan_AlreadyMember() {
//        // regularMember is already part of the plan in setUp()
//        when(planRepository.findByShareableLink("link123")).thenReturn(Optional.of(plan));
//        when(userRepository.findByEmail(member.getEmail())).thenReturn(Optional.of(member));
//
//        BadRequestException exception = assertThrows(BadRequestException.class,
//                () -> planService.joinPlan("link123", member.getEmail()));
//        assertEquals("Bạn đã tham gia kế hoạch này rồi.", exception.getMessage());
//        verify(planRepository, never()).save(any());
//        verify(messagingTemplate, never()).convertAndSend(anyString(), anyMap());
//    }
//
//     @Test
//    void joinPlan_PlanNotFound() {
//        when(planRepository.findByShareableLink("notfound")).thenReturn(Optional.empty());
//         // No need to mock userRepository if plan is not found
//
//        assertThrows(ResourceNotFoundException.class, () -> planService.joinPlan("notfound", member.getEmail()));
//        verify(planRepository, never()).save(any());
//        verify(messagingTemplate, never()).convertAndSend(anyString(), anyMap());
//    }
//
//
//    @Test
//    void getPlanDetails_AsMember() {
//        when(planRepository.findByShareableLink("link123")).thenReturn(Optional.of(plan)); // Assumes tasks are loaded if needed by mapper
//        when(userRepository.findByEmail(member.getEmail())).thenReturn(Optional.of(member));
//
//        Object response = planService.getPlanDetails("link123", member.getEmail());
//
//        assertInstanceOf(PlanDetailResponse.class, response);
//        assertEquals(plan.getTitle(), ((PlanDetailResponse) response).getTitle());
//        assertEquals(2, ((PlanDetailResponse) response).getMembers().size());
//    }
//
//     @Test
//    void getPlanDetails_AsNonMember() {
//         User nonMember = User.builder().id(3).email("nonmember@example.com").build();
//        when(planRepository.findByShareableLink("link123")).thenReturn(Optional.of(plan));
//        when(userRepository.findByEmail(nonMember.getEmail())).thenReturn(Optional.of(nonMember));
//
//        Object response = planService.getPlanDetails("link123", nonMember.getEmail());
//
//        assertInstanceOf(PlanPublicResponse.class, response);
//        assertEquals(plan.getTitle(), ((PlanPublicResponse) response).getTitle());
//         // Verify creator name mapping (assuming getUserFullName works)
//         // assertEquals(creator.getCustomer() != null ? creator.getCustomer().getFullname() : creator.getEmail(), ((PlanPublicResponse) response).getCreatorFullName());
//        assertEquals(plan.getMembers().size(), ((PlanPublicResponse) response).getMemberCount());
//    }
//
//    @Test
//    void updatePlan_Success_ByOwner() {
//        when(planRepository.findByShareableLink("link123")).thenReturn(Optional.of(plan)); // Assume tasks loaded if needed
//        when(userRepository.findByEmail(creator.getEmail())).thenReturn(Optional.of(creator));
//        when(planRepository.save(any(Plan.class))).thenReturn(plan); // Return the modified plan
//
//        PlanDetailResponse response = planService.updatePlan("link123", updatePlanRequest, creator.getEmail());
//
//        assertNotNull(response);
//        assertEquals(updatePlanRequest.getTitle(), response.getTitle());
//        assertEquals(updatePlanRequest.getDurationInDays(), response.getDurationInDays());
//        verify(planRepository).save(plan);
//        // Verify WebSocket message if non-task fields changed
//        verify(messagingTemplate).convertAndSend(eq("/topic/plan/link123/details"), anyMap());
//    }
//
//    @Test
//    void updatePlan_Fail_NotOwner() {
//        when(planRepository.findByShareableLink("link123")).thenReturn(Optional.of(plan));
//        when(userRepository.findByEmail(member.getEmail())).thenReturn(Optional.of(member)); // Use regular member
//
//        assertThrows(AccessDeniedException.class,
//                () -> planService.updatePlan("link123", updatePlanRequest, member.getEmail()));
//        verify(planRepository, never()).save(any());
//        verify(messagingTemplate, never()).convertAndSend(anyString(), anyMap());
//    }
//
//    @Test
//    void leavePlan_Success_ByMember() {
//        when(planRepository.findByShareableLink("link123")).thenReturn(Optional.of(plan));
//        when(userRepository.findByEmail(member.getEmail())).thenReturn(Optional.of(member));
//        // Find the member to remove
//        PlanMember memberToRemove = plan.getMembers().stream().filter(m -> m.getUser().getId().equals(member.getId())).findFirst().get();
//
//        planService.leavePlan("link123", member.getEmail());
//
//        // Verify member was removed from plan's list (in memory) and delete was called
//        assertFalse(plan.getMembers().contains(memberToRemove));
//        verify(planMemberRepository).delete(memberToRemove);
//    }
//
//    @Test
//    void leavePlan_Fail_ByOwner() {
//        when(planRepository.findByShareableLink("link123")).thenReturn(Optional.of(plan));
//        when(userRepository.findByEmail(creator.getEmail())).thenReturn(Optional.of(creator)); // Use owner
//
//        BadRequestException exception = assertThrows(BadRequestException.class,
//                () -> planService.leavePlan("link123", creator.getEmail()));
//        assertEquals("Chủ sở hữu không thể rời khỏi kế hoạch. Bạn cần phải xóa kế hoạch.", exception.getMessage());
//        verify(planMemberRepository, never()).delete(any());
//    }
//
//     @Test
//    void leavePlan_Fail_NotMember() {
//         User nonMember = User.builder().id(3).email("nonmember@example.com").build();
//        when(planRepository.findByShareableLink("link123")).thenReturn(Optional.of(plan));
//        when(userRepository.findByEmail(nonMember.getEmail())).thenReturn(Optional.of(nonMember));
//
//        assertThrows(BadRequestException.class, () -> planService.leavePlan("link123", nonMember.getEmail()));
//         verify(planMemberRepository, never()).delete(any());
//    }
//
//
//    @Test
//    void deletePlan_Success_ByOwner() {
//        when(planRepository.findByShareableLink("link123")).thenReturn(Optional.of(plan));
//        when(userRepository.findByEmail(creator.getEmail())).thenReturn(Optional.of(creator));
//
//        planService.deletePlan("link123", creator.getEmail());
//
//        verify(planRepository).delete(plan);
//    }
//
//     @Test
//    void deletePlan_Fail_NotOwner() {
//        when(planRepository.findByShareableLink("link123")).thenReturn(Optional.of(plan));
//        when(userRepository.findByEmail(member.getEmail())).thenReturn(Optional.of(member)); // Use regular member
//
//        assertThrows(AccessDeniedException.class, () -> planService.deletePlan("link123", member.getEmail()));
//        verify(planRepository, never()).delete(any());
//    }
//
//    // Add tests for:
//    // - getMyPlans (with and without search term)
//    // - addTaskToPlan, updateTaskInPlan, deleteTaskFromPlan (check owner, save, WS message)
//    // - reorderTasksInPlan (check owner, validation, saveAll, WS message)
//    // - removeMemberFromPlan (check owner, validation, delete, WS message)
//    // - transferOwnership (check owner, validation, saveAll, WS message)
//    // - archivePlan, unarchivePlan (check owner, save, WS message)
//}