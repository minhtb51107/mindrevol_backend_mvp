//// src/test/java/com/example/demo/community/service/impl/CommunityServiceImplTest.java
//package com.example.demo.community.service.impl;
//
//import com.example.demo.community.dto.request.AddReactionRequest;
//import com.example.demo.community.dto.request.PostCommentRequest;
//import com.example.demo.community.dto.request.UpdateCommentRequest;
//import com.example.demo.community.entity.ProgressComment;
//import com.example.demo.community.entity.ProgressReaction;
//import com.example.demo.community.entity.ReactionType;
//import com.example.demo.community.repository.ProgressCommentRepository;
//import com.example.demo.community.repository.ProgressReactionRepository;
//import com.example.demo.notification.service.NotificationService;
//import com.example.demo.plan.entity.MemberRole;
//import com.example.demo.plan.entity.Plan;
//import com.example.demo.plan.entity.PlanMember;
//import com.example.demo.progress.dto.response.DailyProgressResponse;
//import com.example.demo.progress.entity.DailyProgress;
//import com.example.demo.progress.mapper.ProgressMapper;
//import com.example.demo.progress.repository.DailyProgressRepository;
//import com.example.demo.shared.exception.ResourceNotFoundException;
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
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//class CommunityServiceImplTest {
//
//    @Mock private DailyProgressRepository progressRepository;
//    @Mock private UserRepository userRepository;
//    @Mock private ProgressReactionRepository reactionRepository;
//    @Mock private ProgressCommentRepository commentRepository;
//    @Mock private NotificationService notificationService;
//    @Mock private SimpMessagingTemplate messagingTemplate;
//
//    // Use @Spy for mapper if you want to test its actual mapping logic
//    // Or @Mock if you just want to verify calls and return mocked responses
//    @Spy // Using Spy to allow calling real mapper methods
//    private ProgressMapper progressMapper = new ProgressMapper();
//
//    @InjectMocks
//    private CommunityServiceImpl communityService;
//
//    private User owner, member1, member2;
//    private Plan plan;
//    private PlanMember ownerMember, member1Member, member2Member;
//    private DailyProgress progress;
//    private PostCommentRequest postCommentRequest;
//    private UpdateCommentRequest updateCommentRequest;
//    private AddReactionRequest addReactionRequest;
//
//    @BeforeEach
//    void setUp() {
//        owner = User.builder().id(1).email("owner@example.com").build();
//        member1 = User.builder().id(2).email("member1@example.com").build();
//        member2 = User.builder().id(3).email("member2@example.com").build();
//
//        plan = Plan.builder().id(1).shareableLink("link123").title("Test Plan").build();
//
//        ownerMember = PlanMember.builder().id(1).user(owner).plan(plan).role(MemberRole.OWNER).build();
//        member1Member = PlanMember.builder().id(2).user(member1).plan(plan).role(MemberRole.MEMBER).build();
//        member2Member = PlanMember.builder().id(3).user(member2).plan(plan).role(MemberRole.MEMBER).build();
//        plan.setMembers(Arrays.asList(ownerMember, member1Member, member2Member)); // Set members
//
//        progress = DailyProgress.builder()
//                .id(1L)
//                .planMember(member1Member) // Progress belongs to member1
//                .date(LocalDate.now().minusDays(1))
//                .comments(new ArrayList<>())
//                .reactions(new HashSet<>())
//                .build();
//
//        postCommentRequest = new PostCommentRequest();
//        postCommentRequest.setContent("This is a comment.");
//
//        updateCommentRequest = new UpdateCommentRequest();
//        updateCommentRequest.setContent("Updated comment content.");
//
//        addReactionRequest = new AddReactionRequest();
//        addReactionRequest.setReactionType(ReactionType.THUMBS_UP);
//    }
//
//    // Helper to mock finding user and progress, and ensure membership
//    private void mockFindsAndMembership(User commenter, DailyProgress targetProgress) {
//        when(progressRepository.findById(targetProgress.getId())).thenReturn(Optional.of(targetProgress));
//        when(userRepository.findByEmail(commenter.getEmail())).thenReturn(Optional.of(commenter));
//        // Simulate ensureUserIsMemberOfPlan check (implicitly true if they are found in plan.getMembers())
//    }
//
//     private void mockFindComment(ProgressComment comment, User user) {
//        when(commentRepository.findById(comment.getId())).thenReturn(Optional.of(comment));
//         when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
//    }
//
//    @Test
//    void postComment_Success_ByAnotherMember() {
//        mockFindsAndMembership(member2, progress); // member2 comments on member1's progress
//        when(commentRepository.save(any(ProgressComment.class))).thenAnswer(inv -> {
//            ProgressComment c = inv.getArgument(0);
//            c.setId(10L); // Simulate save
//            return c;
//        });
//
//        // Use ArgumentCaptor to capture the saved comment for verification
//        ArgumentCaptor<ProgressComment> commentCaptor = ArgumentCaptor.forClass(ProgressComment.class);
//
//        DailyProgressResponse.CommentResponse response = communityService.postComment(progress.getId(), member2.getEmail(), postCommentRequest);
//
//        assertNotNull(response);
//        assertEquals(postCommentRequest.getContent(), response.getContent());
//        assertEquals(member2.getEmail(), response.getAuthorEmail());
//
//        verify(commentRepository).save(commentCaptor.capture());
//        ProgressComment savedComment = commentCaptor.getValue();
//        assertEquals(member2, savedComment.getAuthor());
//        assertEquals(progress, savedComment.getDailyProgress());
//
//        // Verify notification sent to progress owner (member1)
//        verify(notificationService).createNotification(eq(member1), contains("đã bình luận"), contains("#comment-10"));
//
//        // Verify WebSocket message sent
//        verify(messagingTemplate).convertAndSend(eq("/topic/plan/link123/community"), anyMap());
//    }
//
//     @Test
//    void postComment_Success_ByOwnerThemselves() {
//        mockFindsAndMembership(member1, progress); // member1 comments on their own progress
//         when(commentRepository.save(any(ProgressComment.class))).thenAnswer(inv -> {
//            ProgressComment c = inv.getArgument(0);
//            c.setId(11L); // Simulate save
//            return c;
//        });
//
//        communityService.postComment(progress.getId(), member1.getEmail(), postCommentRequest);
//
//        // Verify NO notification sent to progress owner (since it's themselves)
//        verify(notificationService, never()).createNotification(eq(member1), anyString(), anyString());
//
//        // Verify WebSocket message sent
//        verify(messagingTemplate).convertAndSend(eq("/topic/plan/link123/community"), anyMap());
//    }
//
//     @Test
//    void postComment_WithMention_Success() {
//        mockFindsAndMembership(owner, progress); // owner comments on member1's progress, mentions member2
//        String mentionContent = "Check this out @[Member Two](" + member2.getId() + ")";
//        postCommentRequest.setContent(mentionContent);
//
//         when(commentRepository.save(any(ProgressComment.class))).thenAnswer(inv -> {
//            ProgressComment c = inv.getArgument(0);
//            c.setId(12L); // Simulate save
//             c.setContent(mentionContent); // Ensure content is set
//            return c;
//        });
//         when(userRepository.findById(member2.getId())).thenReturn(Optional.of(member2)); // Mock finding mentioned user
//
//        communityService.postComment(progress.getId(), owner.getEmail(), postCommentRequest);
//
//        // Verify notification sent to progress owner (member1)
//        verify(notificationService).createNotification(eq(member1), contains("đã bình luận"), contains("#comment-12"));
//        // Verify notification sent to mentioned user (member2)
//        verify(notificationService).createNotification(eq(member2), contains("đã nhắc đến bạn"), contains("#comment-12"));
//         // Verify WebSocket message sent
//        verify(messagingTemplate).convertAndSend(eq("/topic/plan/link123/community"), anyMap());
//    }
//
//     @Test
//    void postComment_UserNotMember() {
//         User outsider = User.builder().id(99).email("outsider@example.com").build();
//         // Mock finding progress and user BUT ensureUserIsMemberOfPlan will fail (simulated by not including outsider in plan.getMembers())
//         when(progressRepository.findById(progress.getId())).thenReturn(Optional.of(progress));
//        when(userRepository.findByEmail(outsider.getEmail())).thenReturn(Optional.of(outsider));
//
//        assertThrows(AccessDeniedException.class, () -> communityService.postComment(progress.getId(), outsider.getEmail(), postCommentRequest));
//         verify(commentRepository, never()).save(any());
//         verify(notificationService, never()).createNotification(any(), any(), any());
//         verify(messagingTemplate, never()).convertAndSend(anyString(), anyMap());
//    }
//
//
//    @Test
//    void updateComment_Success_ByAuthor() {
//         ProgressComment comment = ProgressComment.builder()
//                .id(20L).author(member2).dailyProgress(progress).content("Original content").build();
//        mockFindComment(comment, member2); // member2 updates their own comment
//        when(commentRepository.save(any(ProgressComment.class))).thenReturn(comment); // Return the modified comment
//
//        DailyProgressResponse.CommentResponse response = communityService.updateComment(comment.getId(), member2.getEmail(), updateCommentRequest);
//
//        assertNotNull(response);
//        assertEquals(updateCommentRequest.getContent(), response.getContent()); // Check if content updated in response
//        verify(commentRepository).save(comment);
//        assertEquals(updateCommentRequest.getContent(), comment.getContent()); // Verify content updated in entity before save
//
//        // Verify WebSocket message sent
//        verify(messagingTemplate).convertAndSend(eq("/topic/plan/link123/community"), anyMap());
//         verify(notificationService, never()).createNotification(any(), anyString(), anyString()); // No new mentions
//    }
//
//     @Test
//    void updateComment_Fail_NotAuthor() {
//        ProgressComment comment = ProgressComment.builder()
//                .id(21L).author(member2).dailyProgress(progress).content("Original content").build();
//         // owner tries to update member2's comment
//        mockFindComment(comment, owner);
//
//        assertThrows(AccessDeniedException.class, () -> communityService.updateComment(comment.getId(), owner.getEmail(), updateCommentRequest));
//        verify(commentRepository, never()).save(any());
//        verify(messagingTemplate, never()).convertAndSend(anyString(), anyMap());
//    }
//
//     @Test
//    void updateComment_WithNewMention() {
//        ProgressComment comment = ProgressComment.builder()
//                .id(22L).author(owner).dailyProgress(progress).content("Original content").build();
//        mockFindComment(comment, owner); // owner updates their comment
//
//        String newMentionContent = "Also mentioning @[Member Two](" + member2.getId() + ")";
//        updateCommentRequest.setContent(newMentionContent);
//
//        when(commentRepository.save(any(ProgressComment.class))).thenAnswer(inv -> {
//            ProgressComment c = inv.getArgument(0);
//            c.setContent(newMentionContent); // Update content
//            return c;
//        });
//        when(userRepository.findById(member2.getId())).thenReturn(Optional.of(member2)); // Mock finding mentioned user
//
//
//        communityService.updateComment(comment.getId(), owner.getEmail(), updateCommentRequest);
//
//        verify(commentRepository).save(comment);
//        // Verify notification sent ONLY to the newly mentioned user (member2)
//         // Note: Progress owner (member1) should NOT get a notification for an updated comment mention by default
//        verify(notificationService).createNotification(eq(member2), contains("đã nhắc đến bạn trong một bình luận đã sửa"), contains("#comment-22"));
//        verify(notificationService, never()).createNotification(eq(member1), anyString(), anyString()); // No notification to progress owner
//         // Verify WebSocket message sent
//        verify(messagingTemplate).convertAndSend(eq("/topic/plan/link123/community"), anyMap());
//    }
//
//
//    @Test
//    void deleteComment_Success_ByAuthor() {
//        ProgressComment comment = ProgressComment.builder()
//                .id(30L).author(member2).dailyProgress(progress).build();
//        mockFindComment(comment, member2); // member2 deletes their own comment
//
//        communityService.deleteComment(comment.getId(), member2.getEmail());
//
//        verify(commentRepository).delete(comment);
//        // Verify WebSocket message sent
//        verify(messagingTemplate).convertAndSend(eq("/topic/plan/link123/community"), anyMap());
//    }
//
//    @Test
//    void deleteComment_Success_ByPlanOwner() {
//        ProgressComment comment = ProgressComment.builder()
//                .id(31L).author(member2).dailyProgress(progress).build();
//        // owner deletes member2's comment
//        mockFindComment(comment, owner);
//
//        communityService.deleteComment(comment.getId(), owner.getEmail());
//
//        verify(commentRepository).delete(comment);
//        // Verify WebSocket message sent
//        verify(messagingTemplate).convertAndSend(eq("/topic/plan/link123/community"), anyMap());
//    }
//
//    @Test
//    void deleteComment_Fail_NotAuthorOrOwner() {
//        ProgressComment comment = ProgressComment.builder()
//                .id(32L).author(member2).dailyProgress(progress).build();
//        // member1 tries to delete member2's comment (member1 is not plan owner)
//        mockFindComment(comment, member1);
//
//        assertThrows(AccessDeniedException.class, () -> communityService.deleteComment(comment.getId(), member1.getEmail()));
//        verify(commentRepository, never()).delete(any());
//        verify(messagingTemplate, never()).convertAndSend(anyString(), anyMap());
//    }
//
//    @Test
//    void addOrUpdateReaction_AddNew_Success() {
//        mockFindsAndMembership(member2, progress); // member2 reacts to member1's progress
//        when(reactionRepository.findByDailyProgressIdAndUserId(progress.getId(), member2.getId())).thenReturn(Optional.empty()); // No existing reaction
//        when(reactionRepository.save(any(ProgressReaction.class))).thenAnswer(inv -> inv.getArgument(0));
//        // Mock finding progress with details for notification link (needed because the mapper uses it)
//        when(progressRepository.findByIdWithDetails(progress.getId())).thenReturn(Optional.of(progress));
//
//
//        communityService.addOrUpdateReaction(progress.getId(), member2.getEmail(), addReactionRequest);
//
//        ArgumentCaptor<ProgressReaction> reactionCaptor = ArgumentCaptor.forClass(ProgressReaction.class);
//        verify(reactionRepository).save(reactionCaptor.capture());
//        ProgressReaction savedReaction = reactionCaptor.getValue();
//        assertEquals(member2, savedReaction.getUser());
//        assertEquals(progress, savedReaction.getDailyProgress());
//        assertEquals(ReactionType.THUMBS_UP, savedReaction.getType());
//
//        // Verify notification sent to progress owner (member1)
//        verify(notificationService).createNotification(eq(member1), contains("đã bày tỏ cảm xúc"), contains("#progress-1"));
//        // Verify WebSocket message sent
//        verify(messagingTemplate).convertAndSend(eq("/topic/plan/link123/community"), anyMap());
//    }
//
//    @Test
//    void addOrUpdateReaction_UpdateExisting_Success() {
//         ProgressReaction existingReaction = ProgressReaction.builder()
//                .id(40L).user(member2).dailyProgress(progress).type(ReactionType.HEART).build();
//        mockFindsAndMembership(member2, progress);
//        when(reactionRepository.findByDailyProgressIdAndUserId(progress.getId(), member2.getId())).thenReturn(Optional.of(existingReaction)); // Existing reaction
//        when(reactionRepository.save(any(ProgressReaction.class))).thenReturn(existingReaction);
//        when(progressRepository.findByIdWithDetails(progress.getId())).thenReturn(Optional.of(progress));
//
//
//        addReactionRequest.setReactionType(ReactionType.ROCKET); // Change reaction type
//        communityService.addOrUpdateReaction(progress.getId(), member2.getEmail(), addReactionRequest);
//
//        verify(reactionRepository).save(existingReaction);
//        assertEquals(ReactionType.ROCKET, existingReaction.getType()); // Verify type was updated
//
//        // Verify notification sent to progress owner (member1) because type changed
//        verify(notificationService).createNotification(eq(member1), contains("đã bày tỏ cảm xúc"), contains("#progress-1"));
//        // Verify WebSocket message sent
//        verify(messagingTemplate).convertAndSend(eq("/topic/plan/link123/community"), anyMap());
//    }
//
//    @Test
//    void addOrUpdateReaction_SameType_NoNotification() {
//         ProgressReaction existingReaction = ProgressReaction.builder()
//                .id(41L).user(member2).dailyProgress(progress).type(ReactionType.THUMBS_UP).build();
//        mockFindsAndMembership(member2, progress);
//        when(reactionRepository.findByDailyProgressIdAndUserId(progress.getId(), member2.getId())).thenReturn(Optional.of(existingReaction));
//        when(reactionRepository.save(any(ProgressReaction.class))).thenReturn(existingReaction);
//         // No need to mock findByIdWithDetails if no notification is expected
//
//
//        addReactionRequest.setReactionType(ReactionType.THUMBS_UP); // Same reaction type
//        communityService.addOrUpdateReaction(progress.getId(), member2.getEmail(), addReactionRequest);
//
//        verify(reactionRepository).save(existingReaction);
//        assertEquals(ReactionType.THUMBS_UP, existingReaction.getType());
//
//        // Verify NO notification sent because type didn't change
//        verify(notificationService, never()).createNotification(any(), anyString(), anyString());
//        // Verify WebSocket message sent (still send WS update)
//        verify(messagingTemplate).convertAndSend(eq("/topic/plan/link123/community"), anyMap());
//    }
//
//     @Test
//    void addOrUpdateReaction_ByOwnerThemselves() {
//         mockFindsAndMembership(member1, progress); // member1 reacts to their own progress
//        when(reactionRepository.findByDailyProgressIdAndUserId(progress.getId(), member1.getId())).thenReturn(Optional.empty());
//        when(reactionRepository.save(any(ProgressReaction.class))).thenAnswer(inv -> inv.getArgument(0));
//        // No need to mock findByIdWithDetails if no notification is expected
//
//
//        communityService.addOrUpdateReaction(progress.getId(), member1.getEmail(), addReactionRequest);
//
//        verify(reactionRepository).save(any(ProgressReaction.class));
//        // Verify NO notification sent
//        verify(notificationService, never()).createNotification(any(), anyString(), anyString());
//        // Verify WebSocket message sent
//        verify(messagingTemplate).convertAndSend(eq("/topic/plan/link123/community"), anyMap());
//    }
//
//
//    @Test
//    void removeReaction_Success() {
//        ProgressReaction reaction = ProgressReaction.builder()
//                .id(50L).user(member2).dailyProgress(progress).type(ReactionType.THUMBS_UP).build();
//        when(userRepository.findByEmail(member2.getEmail())).thenReturn(Optional.of(member2));
//        when(reactionRepository.findByDailyProgressIdAndUserId(progress.getId(), member2.getId())).thenReturn(Optional.of(reaction));
//
//        communityService.removeReaction(progress.getId(), member2.getEmail());
//
//        verify(reactionRepository).delete(reaction);
//        // Verify WebSocket message sent
//        verify(messagingTemplate).convertAndSend(eq("/topic/plan/link123/community"), anyMap());
//    }
//
//    @Test
//    void removeReaction_NotFound() {
//         when(userRepository.findByEmail(member2.getEmail())).thenReturn(Optional.of(member2));
//        when(reactionRepository.findByDailyProgressIdAndUserId(progress.getId(), member2.getId())).thenReturn(Optional.empty()); // Reaction not found
//
//        assertThrows(ResourceNotFoundException.class, () -> communityService.removeReaction(progress.getId(), member2.getEmail()));
//        verify(reactionRepository, never()).delete(any());
//        verify(messagingTemplate, never()).convertAndSend(anyString(), anyMap());
//    }
//}