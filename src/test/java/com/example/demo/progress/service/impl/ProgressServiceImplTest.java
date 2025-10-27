//// src/test/java/com/example/demo/progress/service/impl/ProgressServiceImplTest.java
//package com.example.demo.progress.service.impl;
//
//import com.example.demo.plan.entity.Plan;
//import com.example.demo.plan.entity.PlanMember;
//import com.example.demo.plan.entity.Task;
//import com.example.demo.plan.repository.PlanMemberRepository;
//import com.example.demo.plan.repository.PlanRepository;
//import com.example.demo.plan.repository.TaskAttachmentRepository;
//import com.example.demo.plan.repository.TaskCommentRepository;
//import com.example.demo.plan.repository.TaskRepository;
//import com.example.demo.progress.dto.request.LogProgressRequest;
//import com.example.demo.progress.dto.response.DailyProgressResponse;
//import com.example.demo.progress.dto.response.ProgressDashboardResponse;
//import com.example.demo.progress.entity.DailyProgress;
//import com.example.demo.progress.mapper.ProgressMapper;
//import com.example.demo.progress.repository.DailyProgressRepository;
//import com.example.demo.shared.exception.BadRequestException;
//import com.example.demo.user.entity.User;
//import com.example.demo.user.repository.UserRepository;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.ArgumentCaptor;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.Spy;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.messaging.simp.SimpMessagingTemplate;
//import org.springframework.security.access.AccessDeniedException;
//
//import java.time.LocalDate;
//import java.util.*;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.anyMap;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//class ProgressServiceImplTest {
//
//    @Mock private PlanRepository planRepository;
//    @Mock private UserRepository userRepository;
//    @Mock private PlanMemberRepository planMemberRepository;
//    @Mock private DailyProgressRepository dailyProgressRepository;
//    @Mock private TaskRepository taskRepository;
//    @Mock private TaskCommentRepository taskCommentRepository;
//    @Mock private TaskAttachmentRepository taskAttachmentRepository;
//    @Mock private SimpMessagingTemplate messagingTemplate;
//
//    @Spy // Use Spy to test real mapping logic
//    private ProgressMapper progressMapper = new ProgressMapper();
//
//    @InjectMocks
//    private ProgressServiceImpl progressService;
//
//    private User user1;
//    private Plan plan;
//    private PlanMember planMember1;
//    private Task task1, task2;
//    private LogProgressRequest logRequest;
//    private LocalDate today, yesterday, dayBeforeYesterday;
//
//    @BeforeEach
//    void setUp() {
//        today = LocalDate.now();
//        yesterday = today.minusDays(1);
//        dayBeforeYesterday = today.minusDays(2);
//
//        user1 = User.builder().id(1).email("user1@example.com").build();
//        plan = Plan.builder()
//                .id(1)
//                .shareableLink("link123")
//                .startDate(today.minusDays(5))
//                .durationInDays(10)
//                .members(new ArrayList<>())
//                .dailyTasks(new ArrayList<>())
//                .build();
//        planMember1 = PlanMember.builder().id(1).user(user1).plan(plan).build();
//        plan.getMembers().add(planMember1);
//
//        task1 = Task.builder().id(1L).plan(plan).description("Task 1").order(0).build();
//        task2 = Task.builder().id(2L).plan(plan).description("Task 2").order(1).build();
//        plan.getDailyTasks().addAll(Arrays.asList(task1, task2));
//
//        logRequest = new LogProgressRequest();
//        logRequest.setDate(yesterday);
//        logRequest.setCompleted(false); // Default
//        logRequest.setNotes("Some notes");
//        logRequest.setEvidence(Collections.singletonList("http://evidence.link"));
//        logRequest.setCompletedTaskIds(new HashSet<>(Collections.singletonList(task1.getId()))); // Completed only task 1
//    }
//
//    // Helper to mock finding plan, user, member
//    private void mockFindEssentials() {
//         when(planRepository.findByShareableLink(plan.getShareableLink())).thenReturn(Optional.of(plan));
//         when(userRepository.findByEmail(user1.getEmail())).thenReturn(Optional.of(user1));
//         when(planMemberRepository.findByPlanIdAndUserId(plan.getId(), user1.getId())).thenReturn(Optional.of(planMember1));
//    }
//
//
//    @Test
//    void logOrUpdateDailyProgress_LogNew_Success() {
//        mockFindEssentials();
//        when(dailyProgressRepository.findByPlanMemberIdAndDate(planMember1.getId(), yesterday)).thenReturn(Optional.empty()); // No existing progress
//        when(dailyProgressRepository.save(any(DailyProgress.class))).thenAnswer(inv -> {
//            DailyProgress dp = inv.getArgument(0);
//            dp.setId(10L); // Simulate save
//            return dp;
//        });
//
//        DailyProgressResponse response = progressService.logOrUpdateDailyProgress(plan.getShareableLink(), user1.getEmail(), logRequest);
//
//        assertNotNull(response);
//        assertEquals(yesterday, response.getDate());
//        assertEquals(logRequest.getNotes(), response.getNotes());
//        assertFalse(response.isCompleted()); // Only 1 of 2 tasks completed
//        assertEquals(1, response.getCompletedTaskIds().size());
//        assertTrue(response.getCompletedTaskIds().contains(task1.getId()));
//       // assertTrue(response.getEvidence().contains("http://evidence.link")); // Check if evidence is mapped
//
//        ArgumentCaptor<DailyProgress> progressCaptor = ArgumentCaptor.forClass(DailyProgress.class);
//        verify(dailyProgressRepository).save(progressCaptor.capture());
//        DailyProgress savedProgress = progressCaptor.getValue();
//        assertEquals(planMember1, savedProgress.getPlanMember());
//        assertFalse(savedProgress.isCompleted());
//
//        verify(messagingTemplate).convertAndSend(eq("/topic/plan/link123/progress"), anyMap());
//        verifyNoInteractions(taskCommentRepository, taskAttachmentRepository); // No task updates in this request
//    }
//
//    @Test
//    void logOrUpdateDailyProgress_UpdateExisting_Success() {
//        DailyProgress existingProgress = DailyProgress.builder()
//                .id(11L)
//                .planMember(planMember1)
//                .date(yesterday)
//                .notes("Old notes")
//                .completed(false)
//                .completedTaskIds(new HashSet<>())
//                .build();
//
//        mockFindEssentials();
//        when(dailyProgressRepository.findByPlanMemberIdAndDate(planMember1.getId(), yesterday)).thenReturn(Optional.of(existingProgress));
//        when(dailyProgressRepository.save(any(DailyProgress.class))).thenReturn(existingProgress); // Return the updated entity
//
//        // Update request to complete all tasks
//        logRequest.setCompletedTaskIds(new HashSet<>(Arrays.asList(task1.getId(), task2.getId())));
//        logRequest.setNotes("Updated notes");
//        // logRequest.setCompleted(true); // Should be set automatically when all tasks are done
//
//        DailyProgressResponse response = progressService.logOrUpdateDailyProgress(plan.getShareableLink(), user1.getEmail(), logRequest);
//
//        assertNotNull(response);
//        assertEquals("Updated notes", response.getNotes());
//        assertTrue(response.isCompleted()); // All tasks completed
//        assertEquals(2, response.getCompletedTaskIds().size());
//
//        verify(dailyProgressRepository).save(existingProgress); // Verify existing entity was saved
//        assertTrue(existingProgress.isCompleted()); // Verify entity status updated
//        assertEquals("Updated notes", existingProgress.getNotes());
//
//        verify(messagingTemplate).convertAndSend(eq("/topic/plan/link123/progress"), anyMap());
//    }
//
//     @Test
//    void logOrUpdateDailyProgress_LogNew_AllTasksCompleteSetsProgressComplete() {
//        mockFindEssentials();
//        when(dailyProgressRepository.findByPlanMemberIdAndDate(planMember1.getId(), yesterday)).thenReturn(Optional.empty());
//        when(dailyProgressRepository.save(any(DailyProgress.class))).thenAnswer(inv -> {
//            DailyProgress dp = inv.getArgument(0);
//            dp.setId(12L);
//            return dp;
//        });
//
//        // Set request to complete all tasks
//        logRequest.setCompletedTaskIds(new HashSet<>(Arrays.asList(task1.getId(), task2.getId())));
//        logRequest.setCompleted(false); // Explicitly set to false, should be overridden
//
//        DailyProgressResponse response = progressService.logOrUpdateDailyProgress(plan.getShareableLink(), user1.getEmail(), logRequest);
//
//        assertTrue(response.isCompleted());
//        verify(dailyProgressRepository).save(argThat(dp -> dp.isCompleted())); // Verify saved entity is completed
//         verify(messagingTemplate).convertAndSend(eq("/topic/plan/link123/progress"), anyMap());
//    }
//
//    @Test
//    void logOrUpdateDailyProgress_Fail_DateTooOldToUpdate() {
//         LocalDate oldDate = today.minusDays(3);
//         DailyProgress existingProgress = DailyProgress.builder()
//                .id(13L)
//                .planMember(planMember1)
//                .date(oldDate)
//                .build();
//
//        mockFindEssentials();
//        when(dailyProgressRepository.findByPlanMemberIdAndDate(planMember1.getId(), oldDate)).thenReturn(Optional.of(existingProgress));
//
//        logRequest.setDate(oldDate); // Set request date to the old date
//
//        BadRequestException exception = assertThrows(BadRequestException.class,
//                () -> progressService.logOrUpdateDailyProgress(plan.getShareableLink(), user1.getEmail(), logRequest));
//        assertEquals("Không thể sửa đổi tiến độ cho ngày đã quá cũ.", exception.getMessage());
//         verify(dailyProgressRepository, never()).save(any());
//        verify(messagingTemplate, never()).convertAndSend(anyString(), anyMap());
//    }
//
//     @Test
//    void logOrUpdateDailyProgress_Fail_DateInFuture() {
//        mockFindEssentials(); // Need these mocks before validation
//        logRequest.setDate(today.plusDays(1)); // Future date
//
//        BadRequestException exception = assertThrows(BadRequestException.class,
//                () -> progressService.logOrUpdateDailyProgress(plan.getShareableLink(), user1.getEmail(), logRequest));
//        assertEquals("Không thể ghi nhận tiến độ cho một ngày trong tương lai.", exception.getMessage());
//        verify(dailyProgressRepository, never()).findByPlanMemberIdAndDate(any(), any());
//         verify(dailyProgressRepository, never()).save(any());
//        verify(messagingTemplate, never()).convertAndSend(anyString(), anyMap());
//    }
//
//     @Test
//    void logOrUpdateDailyProgress_Fail_DateTooFarPast() {
//        mockFindEssentials();
//        logRequest.setDate(today.minusDays(3)); // Too old to log/update
//
//        BadRequestException exception = assertThrows(BadRequestException.class,
//                () -> progressService.logOrUpdateDailyProgress(plan.getShareableLink(), user1.getEmail(), logRequest));
//         assertEquals("Bạn chỉ có thể ghi nhận tiến độ cho hôm nay, hôm qua hoặc hôm kia.", exception.getMessage()); // Updated message check
//        verify(dailyProgressRepository, never()).findByPlanMemberIdAndDate(any(), any());
//        verify(dailyProgressRepository, never()).save(any());
//        verify(messagingTemplate, never()).convertAndSend(anyString(), anyMap());
//    }
//
//     @Test
//    void logOrUpdateDailyProgress_Fail_DateOutsidePlan() {
//        mockFindEssentials();
//        logRequest.setDate(plan.getStartDate().minusDays(1)); // Before plan start
//
//        BadRequestException exception = assertThrows(BadRequestException.class,
//                () -> progressService.logOrUpdateDailyProgress(plan.getShareableLink(), user1.getEmail(), logRequest));
//        assertEquals("Ngày check-in không nằm trong thời gian diễn ra kế hoạch.", exception.getMessage());
//         verify(dailyProgressRepository, never()).findByPlanMemberIdAndDate(any(), any());
//        verify(dailyProgressRepository, never()).save(any());
//         verify(messagingTemplate, never()).convertAndSend(anyString(), anyMap());
//    }
//
//    @Test
//    void logOrUpdateDailyProgress_NotMember() {
//         User notMember = User.builder().id(99).email("notmember@example.com").build();
//         when(planRepository.findByShareableLink(plan.getShareableLink())).thenReturn(Optional.of(plan));
//         when(userRepository.findByEmail(notMember.getEmail())).thenReturn(Optional.of(notMember));
//         when(planMemberRepository.findByPlanIdAndUserId(plan.getId(), notMember.getId())).thenReturn(Optional.empty()); // Not a member
//
//        assertThrows(AccessDeniedException.class,
//                () -> progressService.logOrUpdateDailyProgress(plan.getShareableLink(), notMember.getEmail(), logRequest));
//        verify(dailyProgressRepository, never()).save(any());
//        verify(messagingTemplate, never()).convertAndSend(anyString(), anyMap());
//    }
//
//    @Test
//    void getProgressDashboard_Success() {
//        // Add another user and member for dashboard test
//        User user2 = User.builder().id(2).email("user2@example.com").build();
//        PlanMember planMember2 = PlanMember.builder().id(2).user(user2).plan(plan).build();
//        plan.getMembers().add(planMember2);
//
//         // Simulate progress data
//        DailyProgress p1_day1 = DailyProgress.builder().id(20L).planMember(planMember1).date(plan.getStartDate()).completed(true).build();
//        DailyProgress p1_day2 = DailyProgress.builder().id(21L).planMember(planMember1).date(plan.getStartDate().plusDays(1)).completed(false).build();
//        DailyProgress p2_day1 = DailyProgress.builder().id(22L).planMember(planMember2).date(plan.getStartDate()).completed(true).build();
//
//        List<DailyProgress> allProgress = Arrays.asList(p1_day1, p1_day2, p2_day1);
//
//         when(planRepository.findByShareableLink(plan.getShareableLink())).thenReturn(Optional.of(plan)); // Assume tasks/members loaded
//         when(userRepository.findByEmail(user1.getEmail())).thenReturn(Optional.of(user1)); // Requester is user1 (a member)
//         when(dailyProgressRepository.findAll()).thenReturn(allProgress); // Return all progress for the plan
//
//
//        ProgressDashboardResponse response = progressService.getProgressDashboard(plan.getShareableLink(), user1.getEmail());
//
//        assertNotNull(response);
//        assertEquals(plan.getTitle(), response.getPlanTitle());
//        assertEquals(2, response.getMembersProgress().size()); // user1 and user2
//
//        // Check user1's progress summary
//        ProgressDashboardResponse.MemberProgressResponse user1Progress = response.getMembersProgress().stream()
//                .filter(mp -> mp.getUserEmail().equals(user1.getEmail())).findFirst().orElse(null);
//        assertNotNull(user1Progress);
//        assertEquals(1, user1Progress.getCompletedDays()); // Only day1 was completed
//        assertEquals(10.0, user1Progress.getCompletionPercentage()); // 1 day / 10 total days * 100
//        assertEquals(plan.getDurationInDays(), user1Progress.getDailyStatus().size()); // Should have entries for all plan days
//        assertTrue(user1Progress.getDailyStatus().get(plan.getStartDate().toString()).isCompleted()); // Day 1 status
//        assertFalse(user1Progress.getDailyStatus().get(plan.getStartDate().plusDays(1).toString()).isCompleted()); // Day 2 status
//        assertFalse(user1Progress.getDailyStatus().get(plan.getStartDate().plusDays(2).toString()).isCompleted()); // Day 3 (no progress logged)
//
//
//        // Check user2's progress summary
//        ProgressDashboardResponse.MemberProgressResponse user2Progress = response.getMembersProgress().stream()
//                .filter(mp -> mp.getUserEmail().equals(user2.getEmail())).findFirst().orElse(null);
//        assertNotNull(user2Progress);
//        assertEquals(1, user2Progress.getCompletedDays());
//        assertEquals(10.0, user2Progress.getCompletionPercentage());
//        assertTrue(user2Progress.getDailyStatus().get(plan.getStartDate().toString()).isCompleted());
//        assertFalse(user2Progress.getDailyStatus().get(plan.getStartDate().plusDays(1).toString()).isCompleted());
//    }
//
//     @Test
//    void getProgressDashboard_NotMember() {
//         User notMember = User.builder().id(99).email("notmember@example.com").build();
//         when(planRepository.findByShareableLink(plan.getShareableLink())).thenReturn(Optional.of(plan));
//         when(userRepository.findByEmail(notMember.getEmail())).thenReturn(Optional.of(notMember));
//         // Simulate user not being in plan.getMembers()
//
//         assertThrows(AccessDeniedException.class,
//                () -> progressService.getProgressDashboard(plan.getShareableLink(), notMember.getEmail()));
//         verify(dailyProgressRepository, never()).findAll(); // Don't fetch progress if access denied
//    }
//
//    // Add tests for logOrUpdateDailyProgress with taskUpdates (commenting, attachments)
//}