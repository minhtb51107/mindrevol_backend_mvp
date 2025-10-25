// src/test/java/com/example/demo/community/service/impl/CommunityServiceImplTestNG.java
package com.example.demo.community.service.impl;

// TestNG imports
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
// Mockito TestNG Listener
import org.mockito.testng.MockitoTestNGListener;

// Other imports from JUnit version
import com.example.demo.community.dto.request.AddReactionRequest;
import com.example.demo.community.dto.request.PostCommentRequest;
import com.example.demo.community.dto.request.UpdateCommentRequest;
import com.example.demo.community.entity.ProgressComment;
import com.example.demo.community.entity.ProgressReaction;
import com.example.demo.community.entity.ReactionType;
import com.example.demo.community.repository.ProgressCommentRepository;
import com.example.demo.community.repository.ProgressReactionRepository;
import com.example.demo.notification.service.NotificationService;
import com.example.demo.plan.entity.MemberRole;
import com.example.demo.plan.entity.Plan;
import com.example.demo.plan.entity.PlanMember;
import com.example.demo.progress.dto.response.DailyProgressResponse;
import com.example.demo.progress.entity.DailyProgress;
import com.example.demo.progress.mapper.ProgressMapper;
import com.example.demo.progress.repository.DailyProgressRepository;
import com.example.demo.shared.exception.ResourceNotFoundException;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Listeners(MockitoTestNGListener.class)
public class CommunityServiceImplTestNG {

    @Mock private DailyProgressRepository progressRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProgressReactionRepository reactionRepository;
    @Mock private ProgressCommentRepository commentRepository;
    @Mock private NotificationService notificationService;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @Spy
    private ProgressMapper progressMapper = new ProgressMapper();

    @InjectMocks
    private CommunityServiceImpl communityService;

    // --- Variables giữ nguyên từ JUnit version ---
    private User owner, member1, member2;
    private Plan plan;
    private PlanMember ownerMember, member1Member, member2Member;
    private DailyProgress progress;
    private PostCommentRequest postCommentRequest;
    private UpdateCommentRequest updateCommentRequest;
    private AddReactionRequest addReactionRequest;

    @BeforeMethod // Thay @BeforeEach
    void setUp() {
        owner = User.builder().id(1).email("owner@example.com").build();
        member1 = User.builder().id(2).email("member1@example.com").build();
        member2 = User.builder().id(3).email("member2@example.com").build();

        plan = Plan.builder().id(1).shareableLink("link123").title("Test Plan").build();

        ownerMember = PlanMember.builder().id(1).user(owner).plan(plan).role(MemberRole.OWNER).build();
        member1Member = PlanMember.builder().id(2).user(member1).plan(plan).role(MemberRole.MEMBER).build();
        member2Member = PlanMember.builder().id(3).user(member2).plan(plan).role(MemberRole.MEMBER).build();
        plan.setMembers(Arrays.asList(ownerMember, member1Member, member2Member));

        progress = DailyProgress.builder()
                .id(1L)
                .planMember(member1Member)
                .date(LocalDate.now().minusDays(1))
                .comments(new ArrayList<>())
                .reactions(new HashSet<>())
                .build();

        postCommentRequest = new PostCommentRequest();
        postCommentRequest.setContent("This is a comment.");

        updateCommentRequest = new UpdateCommentRequest();
        updateCommentRequest.setContent("Updated comment content.");

        addReactionRequest = new AddReactionRequest();
        addReactionRequest.setReactionType(ReactionType.THUMBS_UP);
    }

    // --- Helper methods giữ nguyên ---
     private void mockFindsAndMembership(User commenter, DailyProgress targetProgress) {
        when(progressRepository.findById(targetProgress.getId())).thenReturn(Optional.of(targetProgress));
        when(userRepository.findByEmail(commenter.getEmail())).thenReturn(Optional.of(commenter));
    }

     private void mockFindComment(ProgressComment comment, User user) {
        when(commentRepository.findById(comment.getId())).thenReturn(Optional.of(comment));
         when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }


    @Test
    void postComment_Success_ByAnotherMember() {
        mockFindsAndMembership(member2, progress);
        when(commentRepository.save(any(ProgressComment.class))).thenAnswer(inv -> {
            ProgressComment c = inv.getArgument(0);
            c.setId(10L);
            return c;
        });
        ArgumentCaptor<ProgressComment> commentCaptor = ArgumentCaptor.forClass(ProgressComment.class);

        DailyProgressResponse.CommentResponse response = communityService.postComment(progress.getId(), member2.getEmail(), postCommentRequest);

        Assert.assertNotNull(response); // Thay assertion
        Assert.assertEquals(response.getContent(), postCommentRequest.getContent());
        Assert.assertEquals(response.getAuthorEmail(), member2.getEmail());

        verify(commentRepository).save(commentCaptor.capture());
        ProgressComment savedComment = commentCaptor.getValue();
        Assert.assertEquals(savedComment.getAuthor(), member2);
        Assert.assertEquals(savedComment.getDailyProgress(), progress);

        verify(notificationService).createNotification(eq(member1), contains("đã bình luận"), contains("#comment-10"));
        verify(messagingTemplate).convertAndSend(eq("/topic/plan/link123/community"), anyMap());
    }

    @Test
    void postComment_Success_ByOwnerThemselves() {
         mockFindsAndMembership(member1, progress); // member1 comments on their own progress
         when(commentRepository.save(any(ProgressComment.class))).thenAnswer(inv -> {
            ProgressComment c = inv.getArgument(0);
            c.setId(11L);
            return c;
        });

        communityService.postComment(progress.getId(), member1.getEmail(), postCommentRequest);

        verify(notificationService, never()).createNotification(eq(member1), anyString(), anyString());
        verify(messagingTemplate).convertAndSend(eq("/topic/plan/link123/community"), anyMap());
    }

     @Test
    void postComment_WithMention_Success() {
        mockFindsAndMembership(owner, progress);
        String mentionContent = "Check this out @[Member Two](" + member2.getId() + ")";
        postCommentRequest.setContent(mentionContent);

         when(commentRepository.save(any(ProgressComment.class))).thenAnswer(inv -> {
            ProgressComment c = inv.getArgument(0);
            c.setId(12L);
             c.setContent(mentionContent);
            return c;
        });
         when(userRepository.findById(member2.getId())).thenReturn(Optional.of(member2));

        communityService.postComment(progress.getId(), owner.getEmail(), postCommentRequest);

        verify(notificationService).createNotification(eq(member1), contains("đã bình luận"), contains("#comment-12"));
        verify(notificationService).createNotification(eq(member2), contains("đã nhắc đến bạn"), contains("#comment-12"));
        verify(messagingTemplate).convertAndSend(eq("/topic/plan/link123/community"), anyMap());
    }

    @Test(expectedExceptions = AccessDeniedException.class)
    void postComment_UserNotMember() {
         User outsider = User.builder().id(99).email("outsider@example.com").build();
         when(progressRepository.findById(progress.getId())).thenReturn(Optional.of(progress));
        when(userRepository.findByEmail(outsider.getEmail())).thenReturn(Optional.of(outsider));

        try {
            communityService.postComment(progress.getId(), outsider.getEmail(), postCommentRequest);
        } finally {
             verify(commentRepository, never()).save(any());
             verify(notificationService, never()).createNotification(any(), any(), any());
             verify(messagingTemplate, never()).convertAndSend(anyString(), anyMap());
        }
    }

    @Test
    void updateComment_Success_ByAuthor() {
         ProgressComment comment = ProgressComment.builder()
                .id(20L).author(member2).dailyProgress(progress).content("Original content").build();
        mockFindComment(comment, member2);
        when(commentRepository.save(any(ProgressComment.class))).thenReturn(comment);

        DailyProgressResponse.CommentResponse response = communityService.updateComment(comment.getId(), member2.getEmail(), updateCommentRequest);

        Assert.assertNotNull(response);
        Assert.assertEquals(response.getContent(), updateCommentRequest.getContent());
        verify(commentRepository).save(comment);
        Assert.assertEquals(comment.getContent(), updateCommentRequest.getContent());

        verify(messagingTemplate).convertAndSend(eq("/topic/plan/link123/community"), anyMap());
         verify(notificationService, never()).createNotification(any(), anyString(), anyString());
    }

    @Test(expectedExceptions = AccessDeniedException.class)
    void updateComment_Fail_NotAuthor() {
        ProgressComment comment = ProgressComment.builder()
                .id(21L).author(member2).dailyProgress(progress).content("Original content").build();
        mockFindComment(comment, owner);

        try {
             communityService.updateComment(comment.getId(), owner.getEmail(), updateCommentRequest);
        } finally {
            verify(commentRepository, never()).save(any());
            verify(messagingTemplate, never()).convertAndSend(anyString(), anyMap());
        }
    }

     @Test
    void updateComment_WithNewMention() {
        ProgressComment comment = ProgressComment.builder()
                .id(22L).author(owner).dailyProgress(progress).content("Original content").build();
        mockFindComment(comment, owner);

        String newMentionContent = "Also mentioning @[Member Two](" + member2.getId() + ")";
        updateCommentRequest.setContent(newMentionContent);

        when(commentRepository.save(any(ProgressComment.class))).thenAnswer(inv -> {
            ProgressComment c = inv.getArgument(0);
            c.setContent(newMentionContent);
            return c;
        });
        when(userRepository.findById(member2.getId())).thenReturn(Optional.of(member2));

        communityService.updateComment(comment.getId(), owner.getEmail(), updateCommentRequest);

        verify(commentRepository).save(comment);
        verify(notificationService).createNotification(eq(member2), contains("đã nhắc đến bạn trong một bình luận đã sửa"), contains("#comment-22"));
        verify(notificationService, never()).createNotification(eq(member1), anyString(), anyString());
        verify(messagingTemplate).convertAndSend(eq("/topic/plan/link123/community"), anyMap());
    }

     @Test
    void deleteComment_Success_ByAuthor() {
        ProgressComment comment = ProgressComment.builder()
                .id(30L).author(member2).dailyProgress(progress).build();
        mockFindComment(comment, member2);

        communityService.deleteComment(comment.getId(), member2.getEmail());

        verify(commentRepository).delete(comment);
        verify(messagingTemplate).convertAndSend(eq("/topic/plan/link123/community"), anyMap());
    }

    @Test
    void deleteComment_Success_ByPlanOwner() {
        ProgressComment comment = ProgressComment.builder()
                .id(31L).author(member2).dailyProgress(progress).build();
        mockFindComment(comment, owner);

        communityService.deleteComment(comment.getId(), owner.getEmail());

        verify(commentRepository).delete(comment);
        verify(messagingTemplate).convertAndSend(eq("/topic/plan/link123/community"), anyMap());
    }

    @Test(expectedExceptions = AccessDeniedException.class)
    void deleteComment_Fail_NotAuthorOrOwner() {
        ProgressComment comment = ProgressComment.builder()
                .id(32L).author(member2).dailyProgress(progress).build();
        mockFindComment(comment, member1);

        try {
             communityService.deleteComment(comment.getId(), member1.getEmail());
        } finally {
            verify(commentRepository, never()).delete(any());
            verify(messagingTemplate, never()).convertAndSend(anyString(), anyMap());
        }
    }

    @Test
    void addOrUpdateReaction_AddNew_Success() {
        mockFindsAndMembership(member2, progress);
        when(reactionRepository.findByDailyProgressIdAndUserId(progress.getId(), member2.getId())).thenReturn(Optional.empty());
        when(reactionRepository.save(any(ProgressReaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(progressRepository.findByIdWithDetails(progress.getId())).thenReturn(Optional.of(progress));

        communityService.addOrUpdateReaction(progress.getId(), member2.getEmail(), addReactionRequest);

        ArgumentCaptor<ProgressReaction> reactionCaptor = ArgumentCaptor.forClass(ProgressReaction.class);
        verify(reactionRepository).save(reactionCaptor.capture());
        ProgressReaction savedReaction = reactionCaptor.getValue();
        Assert.assertEquals(savedReaction.getUser(), member2); // Thay assertion
        Assert.assertEquals(savedReaction.getDailyProgress(), progress);
        Assert.assertEquals(savedReaction.getType(), ReactionType.THUMBS_UP);

        verify(notificationService).createNotification(eq(member1), contains("đã bày tỏ cảm xúc"), contains("#progress-1"));
        verify(messagingTemplate).convertAndSend(eq("/topic/plan/link123/community"), anyMap());
    }

    @Test
    void addOrUpdateReaction_UpdateExisting_Success() {
         ProgressReaction existingReaction = ProgressReaction.builder()
                .id(40L).user(member2).dailyProgress(progress).type(ReactionType.HEART).build();
        mockFindsAndMembership(member2, progress);
        when(reactionRepository.findByDailyProgressIdAndUserId(progress.getId(), member2.getId())).thenReturn(Optional.of(existingReaction));
        when(reactionRepository.save(any(ProgressReaction.class))).thenReturn(existingReaction);
        when(progressRepository.findByIdWithDetails(progress.getId())).thenReturn(Optional.of(progress));

        addReactionRequest.setReactionType(ReactionType.ROCKET);
        communityService.addOrUpdateReaction(progress.getId(), member2.getEmail(), addReactionRequest);

        verify(reactionRepository).save(existingReaction);
        Assert.assertEquals(existingReaction.getType(), ReactionType.ROCKET); // Thay assertion

        verify(notificationService).createNotification(eq(member1), contains("đã bày tỏ cảm xúc"), contains("#progress-1"));
        verify(messagingTemplate).convertAndSend(eq("/topic/plan/link123/community"), anyMap());
    }

    @Test
    void addOrUpdateReaction_SameType_NoNotification() {
         ProgressReaction existingReaction = ProgressReaction.builder()
                .id(41L).user(member2).dailyProgress(progress).type(ReactionType.THUMBS_UP).build();
        mockFindsAndMembership(member2, progress);
        when(reactionRepository.findByDailyProgressIdAndUserId(progress.getId(), member2.getId())).thenReturn(Optional.of(existingReaction));
        when(reactionRepository.save(any(ProgressReaction.class))).thenReturn(existingReaction);

        addReactionRequest.setReactionType(ReactionType.THUMBS_UP);
        communityService.addOrUpdateReaction(progress.getId(), member2.getEmail(), addReactionRequest);

        verify(reactionRepository).save(existingReaction);
        Assert.assertEquals(existingReaction.getType(), ReactionType.THUMBS_UP); // Thay assertion

        verify(notificationService, never()).createNotification(any(), anyString(), anyString());
        verify(messagingTemplate).convertAndSend(eq("/topic/plan/link123/community"), anyMap());
    }

    @Test
    void addOrUpdateReaction_ByOwnerThemselves() {
         mockFindsAndMembership(member1, progress);
        when(reactionRepository.findByDailyProgressIdAndUserId(progress.getId(), member1.getId())).thenReturn(Optional.empty());
        when(reactionRepository.save(any(ProgressReaction.class))).thenAnswer(inv -> inv.getArgument(0));

        communityService.addOrUpdateReaction(progress.getId(), member1.getEmail(), addReactionRequest);

        verify(reactionRepository).save(any(ProgressReaction.class));
        verify(notificationService, never()).createNotification(any(), anyString(), anyString());
        verify(messagingTemplate).convertAndSend(eq("/topic/plan/link123/community"), anyMap());
    }

    @Test
    void removeReaction_Success() {
        ProgressReaction reaction = ProgressReaction.builder()
                .id(50L).user(member2).dailyProgress(progress).type(ReactionType.THUMBS_UP).build();
        when(userRepository.findByEmail(member2.getEmail())).thenReturn(Optional.of(member2));
        when(reactionRepository.findByDailyProgressIdAndUserId(progress.getId(), member2.getId())).thenReturn(Optional.of(reaction));

        communityService.removeReaction(progress.getId(), member2.getEmail());

        verify(reactionRepository).delete(reaction);
        verify(messagingTemplate).convertAndSend(eq("/topic/plan/link123/community"), anyMap());
    }

    @Test(expectedExceptions = ResourceNotFoundException.class)
    void removeReaction_NotFound() {
         when(userRepository.findByEmail(member2.getEmail())).thenReturn(Optional.of(member2));
        when(reactionRepository.findByDailyProgressIdAndUserId(progress.getId(), member2.getId())).thenReturn(Optional.empty());

        try {
             communityService.removeReaction(progress.getId(), member2.getEmail());
        } finally {
            verify(reactionRepository, never()).delete(any());
            verify(messagingTemplate, never()).convertAndSend(anyString(), anyMap());
        }
    }
}