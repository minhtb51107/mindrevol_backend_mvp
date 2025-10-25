package com.example.demo.community.service.impl;

import com.example.demo.community.dto.request.AddReactionRequest;
import com.example.demo.community.dto.request.PostCommentRequest;
import com.example.demo.community.dto.request.UpdateCommentRequest;
import com.example.demo.community.entity.ProgressComment;
import com.example.demo.community.entity.ProgressReaction;
import com.example.demo.community.entity.ReactionType;
import com.example.demo.community.repository.ProgressCommentRepository;
import com.example.demo.community.repository.ProgressReactionRepository;
import com.example.demo.community.service.CommunityService;
import com.example.demo.feed.entity.FeedEventType; // *** THÊM IMPORT ***
import com.example.demo.feed.service.FeedService; // *** THÊM IMPORT ***
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap; // *** THÊM IMPORT ***
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.HashSet;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CommunityServiceImpl implements CommunityService {

    private final DailyProgressRepository progressRepository;
    private final UserRepository userRepository;
    private final ProgressReactionRepository reactionRepository;
    private final ProgressMapper progressMapper;
    private final ProgressCommentRepository commentRepository;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final FeedService feedService; // *** INJECT FeedService ***

    private static final Pattern MENTION_PATTERN = Pattern.compile("@\\[[^\\]]+?\\]\\((\\d+?)\\)");

    @Override
    public DailyProgressResponse.CommentResponse postComment(Long progressId, String userEmail, PostCommentRequest request) {
        DailyProgress progress = findProgressById(progressId);
        User author = findUserByEmail(userEmail);
        ensureUserIsMemberOfPlan(author, progress.getPlanMember().getPlan()); // Check membership using Plan
        Plan plan = progress.getPlanMember().getPlan(); // Get Plan entity

        ProgressComment comment = ProgressComment.builder()
                .dailyProgress(progress)
                .author(author)
                .content(request.getContent())
                .build();

        ProgressComment savedComment = commentRepository.save(comment);
        DailyProgressResponse.CommentResponse commentResponse = progressMapper.toCommentResponse(savedComment);
        String authorName = progressMapper.getUserFullName(author);

        // --- XỬ LÝ NOTIFICATION THÔNG THƯỜNG & MENTION (giữ nguyên) ---
        User progressOwner = progress.getPlanMember().getUser();
        if (!author.getId().equals(progressOwner.getId())) {
             String message = authorName + " đã bình luận về tiến độ ngày " + progress.getDate() + " của bạn trong kế hoạch '" + plan.getTitle() + "'.";
             String link = String.format("/plan/%s?date=%s#comment-%d", plan.getShareableLink(), progress.getDate(), savedComment.getId());
             notificationService.createNotification(progressOwner, message, link);
        }
        Set<Integer> mentionedUserIds = extractMentionedUserIds(savedComment.getContent());
        for (Integer mentionedUserId : mentionedUserIds) {
            if (!mentionedUserId.equals(author.getId()) && !mentionedUserId.equals(progressOwner.getId())) {
                userRepository.findById(mentionedUserId).ifPresent(mentionedUser -> {
                    if (isUserMemberOfPlan(mentionedUser, plan)) {
                        String mentionMessage = String.format("%s đã nhắc đến bạn trong bình luận về tiến độ ngày %s (%s).", authorName, progress.getDate(), plan.getTitle());
                        String mentionLink = String.format("/plan/%s?date=%s#comment-%d", plan.getShareableLink(), progress.getDate(), savedComment.getId());
                        notificationService.createNotification(mentionedUser, mentionMessage, mentionLink);
                        log.info("Sent mention notification to user {} from comment {}", mentionedUserId, savedComment.getId());
                    }
                });
            }
        }
        // --- KẾT THÚC XỬ LÝ NOTIFICATION ---


        // *** GỬI FEED EVENT COMMENT_ADDED ***
        Map<String, Object> details = new HashMap<>();
        details.put("commentId", savedComment.getId());
        details.put("progressDate", progress.getDate().toString());
        feedService.createAndPublishFeedEvent(FeedEventType.COMMENT_ADDED, author, plan, details);
        // *** KẾT THÚC GỬI FEED EVENT ***


        // GỬI MESSAGE WEBSOCKET NEW_COMMENT (giữ nguyên)
        String destination = "/topic/plan/" + plan.getShareableLink() + "/community";
        Map<String, Object> payload = Map.of( "type", "NEW_COMMENT", "progressId", progressId, "comment", commentResponse );
        messagingTemplate.convertAndSend(destination, payload);

        return commentResponse;
    }

    @Override
    public DailyProgressResponse.CommentResponse updateComment(Long commentId, String userEmail, UpdateCommentRequest request) {
        ProgressComment comment = findCommentById(commentId);
        User user = findUserByEmail(userEmail);

        if (!comment.getAuthor().getId().equals(user.getId())) {
            throw new AccessDeniedException("Bạn không có quyền sửa bình luận này.");
        }

        String oldContent = comment.getContent();
        comment.setContent(request.getContent());
        ProgressComment updatedComment = commentRepository.save(comment);

        DailyProgress progress = comment.getDailyProgress();
        Plan plan = progress.getPlanMember().getPlan();
        DailyProgressResponse.CommentResponse commentResponse = progressMapper.toCommentResponse(updatedComment);
        String authorName = progressMapper.getUserFullName(user);

        // --- XỬ LÝ NOTIFICATION MENTION KHI UPDATE (giữ nguyên) ---
         Set<Integer> oldMentionedIds = extractMentionedUserIds(oldContent);
         Set<Integer> newMentionedIds = extractMentionedUserIds(updatedComment.getContent());
         for (Integer mentionedUserId : newMentionedIds) {
             if (!oldMentionedIds.contains(mentionedUserId) &&
                 !mentionedUserId.equals(user.getId()) &&
                 !mentionedUserId.equals(progress.getPlanMember().getUser().getId())) {
                 userRepository.findById(mentionedUserId).ifPresent(mentionedUser -> {
                      if (isUserMemberOfPlan(mentionedUser, plan)) {
                         String mentionMessage = String.format("%s đã nhắc đến bạn trong bình luận đã sửa về tiến độ ngày %s (%s).", authorName, progress.getDate(), plan.getTitle());
                         String mentionLink = String.format("/plan/%s?date=%s#comment-%d", plan.getShareableLink(), progress.getDate(), updatedComment.getId());
                         notificationService.createNotification(mentionedUser, mentionMessage, mentionLink);
                         log.info("Sent mention notification (update) to user {} from comment {}", mentionedUserId, updatedComment.getId());
                      }
                 });
             }
         }
        // --- KẾT THÚC XỬ LÝ NOTIFICATION ---

        // GỬI MESSAGE WEBSOCKET UPDATE_COMMENT (giữ nguyên)
        String destination = "/topic/plan/" + plan.getShareableLink() + "/community";
        Map<String, Object> payload = Map.of( "type", "UPDATE_COMMENT", "progressId", progress.getId(), "comment", commentResponse );
        messagingTemplate.convertAndSend(destination, payload);
        // No feed event for comment update

        return commentResponse;
    }

    @Override
    public void deleteComment(Long commentId, String userEmail) {
        ProgressComment comment = findCommentById(commentId);
        User user = findUserByEmail(userEmail);
        DailyProgress progress = comment.getDailyProgress();
        Plan plan = progress.getPlanMember().getPlan();
        Long progressId = progress.getId(); // Store before deleting comment

        boolean isAuthor = comment.getAuthor().getId().equals(user.getId());
        // Ensure plan members are loaded before checking role
        plan.getMembers().size(); // Trigger load if necessary
        boolean isPlanOwner = plan.getMembers().stream()
                .anyMatch(m -> m.getUser() != null && m.getUser().getId().equals(user.getId()) && m.getRole() == MemberRole.OWNER);

        if (!isAuthor && !isPlanOwner) {
            throw new AccessDeniedException("Bạn không có quyền xóa bình luận này.");
        }

        commentRepository.delete(comment);
        log.info("User {} deleted comment {}", userEmail, commentId);

        // GỬI MESSAGE WEBSOCKET DELETE_COMMENT (giữ nguyên)
        String destination = "/topic/plan/" + plan.getShareableLink() + "/community";
        Map<String, Object> payload = Map.of( "type", "DELETE_COMMENT", "progressId", progressId, "commentId", commentId );
        messagingTemplate.convertAndSend(destination, payload);
        // No feed event for comment deletion
    }


    @Override
    public void addOrUpdateReaction(Long progressId, String userEmail, AddReactionRequest request) {
        DailyProgress progress = findProgressById(progressId);
        User reactor = findUserByEmail(userEmail);
        ensureUserIsMemberOfPlan(reactor, progress.getPlanMember().getPlan()); // Check membership using Plan
        Plan plan = progress.getPlanMember().getPlan(); // Get Plan

        ProgressReaction reaction = reactionRepository.findByDailyProgressIdAndUserId(progressId, reactor.getId()).orElse(new ProgressReaction());
        boolean typeChanged = reaction.getId() == null || reaction.getType() != request.getReactionType();

        reaction.setDailyProgress(progress);
        reaction.setUser(reactor);
        reaction.setType(request.getReactionType());
        ProgressReaction savedReaction = reactionRepository.save(reaction);

        // Gửi Notification (giữ nguyên)
        User progressOwner = progress.getPlanMember().getUser();
        if (typeChanged && !reactor.getId().equals(progressOwner.getId())) {
             String reactorName = progressMapper.getUserFullName(reactor);
             String message = reactorName + " đã bày tỏ cảm xúc về tiến độ ngày " + progress.getDate() + " của bạn trong kế hoạch '" + plan.getTitle() + "'.";
             String link = String.format("/plan/%s?date=%s#progress-%d", plan.getShareableLink(), progress.getDate(), progressId);
             notificationService.createNotification(progressOwner, message, link);
        }

        // *** GỬI FEED EVENT REACTION_ADDED (NẾU typeChanged) ***
        if (typeChanged) {
            Map<String, Object> details = Map.of(
                "reactionType", savedReaction.getType().name(),
                "progressDate", progress.getDate().toString()
            );
            feedService.createAndPublishFeedEvent(FeedEventType.REACTION_ADDED, reactor, plan, details);
        }
        // *** KẾT THÚC GỬI FEED EVENT ***

        // GỬI MESSAGE WEBSOCKET UPDATE_REACTION (giữ nguyên)
        String destination = "/topic/plan/" + plan.getShareableLink() + "/community";
        Map<String, Object> simplePayload = Map.of(
            "type", "UPDATE_REACTION",
            "progressId", progressId,
            "userId", reactor.getId(),
            "reactionType", request.getReactionType().name()
        );
        messagingTemplate.convertAndSend(destination, simplePayload);
    }

    @Override
    public void removeReaction(Long progressId, String userEmail) {
        User user = findUserByEmail(userEmail);
        ProgressReaction reaction = reactionRepository.findByDailyProgressIdAndUserId(progressId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Bạn chưa thả reaction nào."));
        Plan plan = reaction.getDailyProgress().getPlanMember().getPlan(); // Get plan
        Integer userIdToRemove = user.getId(); // Store before deleting

        reactionRepository.delete(reaction);
        log.info("User {} removed reaction from progress {}", userEmail, progressId);

        // GỬI MESSAGE WEBSOCKET REMOVE_REACTION (giữ nguyên)
        String destination = "/topic/plan/" + plan.getShareableLink() + "/community";
         Map<String, Object> payload = Map.of(
            "type", "REMOVE_REACTION",
            "progressId", progressId,
            "userId", userIdToRemove
        );
        messagingTemplate.convertAndSend(destination, payload);
        // No feed event for reaction removal
    }

    // --- Helper Methods ---
    private Set<Integer> extractMentionedUserIds(String content) {
        Set<Integer> userIds = new HashSet<>();
        if (content == null || content.isBlank()) return userIds;
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            try { Integer userId = Integer.parseInt(matcher.group(1)); userIds.add(userId); }
            catch (NumberFormatException e) { log.warn("Could not parse user ID from mention: {}", matcher.group(1)); }
        }
        return userIds;
    }

    // Sửa lại để nhận Plan entity
    private boolean isUserMemberOfPlan(User user, Plan plan) {
         if (plan == null || plan.getMembers() == null || user == null) return false;
         plan.getMembers().size(); // Trigger load if necessary
         return plan.getMembers().stream()
                    .anyMatch(member -> member.getUser() != null && member.getUser().getId().equals(user.getId()));
     }

    private DailyProgress findProgressById(Long id) {
        // Fetch progress with necessary associations
        return progressRepository.findById(id)
                .map(p -> {
                    // Eagerly fetch required associations if not already fetched by repository method
                    if (p.getPlanMember() != null) {
                        p.getPlanMember().getUser().getEmail(); // Fetch user
                        if (p.getPlanMember().getPlan() != null) {
                            p.getPlanMember().getPlan().getShareableLink(); // Fetch plan essentials
                            p.getPlanMember().getPlan().getMembers().size(); // Fetch plan members
                        }
                    }
                    return p;
                })
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tiến độ với ID: " + id));
    }

    private DailyProgress findProgressByIdWithDetails(Long id) {
         return progressRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tiến độ với ID: " + id));
     }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user với email: " + email));
    }

    private void ensureUserIsMemberOfPlan(User user, Plan plan) { // Sửa lại nhận Plan
        if (!isUserMemberOfPlan(user, plan)) {
            throw new AccessDeniedException("Bạn không phải thành viên của kế hoạch này.");
        }
    }

    private ProgressComment findCommentById(Long id) {
        // Fetch comment with progress and plan member/plan for context
        return commentRepository.findById(id)
                .map(c -> {
                    if (c.getDailyProgress() != null && c.getDailyProgress().getPlanMember() != null && c.getDailyProgress().getPlanMember().getPlan() != null) {
                         c.getDailyProgress().getPlanMember().getPlan().getShareableLink(); // Fetch plan essentials
                         c.getDailyProgress().getPlanMember().getPlan().getMembers().size(); // Fetch members for role check
                    }
                    return c;
                })
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bình luận với ID: " + id));
    }
}