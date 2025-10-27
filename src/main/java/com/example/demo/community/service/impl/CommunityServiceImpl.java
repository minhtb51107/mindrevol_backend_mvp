package com.example.demo.community.service.impl;

import com.example.demo.community.dto.request.AddReactionRequest;
import com.example.demo.community.dto.request.PostCommentRequest;
import com.example.demo.community.dto.request.UpdateCommentRequest;
import com.example.demo.community.dto.response.CommentResponse;
// *** THAY ĐỔI IMPORT DTO ***
// import com.example.demo.progress.dto.response.DailyProgressResponse; // Không dùng DTO lồng nhau của DailyProgressResponse nữa
// *** KẾT THÚC THAY ĐỔI IMPORT DTO ***
import com.example.demo.community.entity.ProgressComment;
import com.example.demo.community.entity.ProgressReaction;
import com.example.demo.community.entity.ReactionType;
import com.example.demo.community.repository.ProgressCommentRepository;
import com.example.demo.community.repository.ProgressReactionRepository;
import com.example.demo.community.service.CommunityService;
import com.example.demo.feed.entity.FeedEventType;
import com.example.demo.feed.service.FeedService;
import com.example.demo.notification.service.NotificationService;
import com.example.demo.plan.entity.MemberRole;
import com.example.demo.plan.entity.Plan;
import com.example.demo.plan.entity.PlanMember;
// import com.example.demo.progress.mapper.ProgressMapper; // *** KHÔNG IMPORT ProgressMapper NỮA ***
import com.example.demo.progress.entity.DailyProgress; // Vẫn cần entity này vì comment/reaction đang gắn vào nó
import com.example.demo.progress.repository.DailyProgressRepository; // Vẫn cần repo này
import com.example.demo.shared.exception.ResourceNotFoundException;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
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
    // private final ProgressMapper progressMapper; // *** BỎ DEPENDENCY NÀY ***
    private final ProgressCommentRepository commentRepository;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final FeedService feedService;

    private static final Pattern MENTION_PATTERN = Pattern.compile("@\\[[^\\]]+?\\]\\((\\d+?)\\)");

    @Override
    public CommentResponse postComment(Long progressId, String userEmail, PostCommentRequest request) { // *** THAY ĐỔI KIỂU TRẢ VỀ ***
        DailyProgress progress = findProgressById(progressId);
        User author = findUserByEmail(userEmail);
        ensureUserIsMemberOfPlan(author, progress.getPlanMember().getPlan());
        Plan plan = progress.getPlanMember().getPlan();

        ProgressComment comment = ProgressComment.builder()
                .dailyProgress(progress)
                .author(author)
                .content(request.getContent())
                .build();

        ProgressComment savedComment = commentRepository.save(comment);
        // *** THAY ĐỔI: Gọi helper method nội bộ ***
        CommentResponse commentResponse = toCommentResponse(savedComment);
        String authorName = getUserFullName(author); // Gọi helper method nội bộ
        // *** KẾT THÚC THAY ĐỔI ***

        // --- XỬ LÝ NOTIFICATION THÔNG THƯỜNG & MENTION ---
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


        // GỬI MESSAGE WEBSOCKET NEW_COMMENT
        String destination = "/topic/plan/" + plan.getShareableLink() + "/community";
        Map<String, Object> payload = Map.of( "type", "NEW_COMMENT", "progressId", progressId, "comment", commentResponse );
        messagingTemplate.convertAndSend(destination, payload);

        return commentResponse;
    }

    @Override
    public CommentResponse updateComment(Long commentId, String userEmail, UpdateCommentRequest request) { // *** THAY ĐỔI KIỂU TRẢ VỀ ***
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
        // *** THAY ĐỔI: Gọi helper method nội bộ ***
        CommentResponse commentResponse = toCommentResponse(updatedComment);
        String authorName = getUserFullName(user); // Gọi helper method nội bộ
        // *** KẾT THÚC THAY ĐỔI ***

        // --- XỬ LÝ NOTIFICATION MENTION KHI UPDATE ---
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

        // GỬI MESSAGE WEBSOCKET UPDATE_COMMENT
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
        plan.getMembers().size(); // Trigger load if necessary
        boolean isPlanOwner = plan.getMembers().stream()
                .anyMatch(m -> m.getUser() != null && m.getUser().getId().equals(user.getId()) && m.getRole() == MemberRole.OWNER);

        if (!isAuthor && !isPlanOwner) {
            throw new AccessDeniedException("Bạn không có quyền xóa bình luận này.");
        }

        commentRepository.delete(comment);
        log.info("User {} deleted comment {}", userEmail, commentId);

        // GỬI MESSAGE WEBSOCKET DELETE_COMMENT
        String destination = "/topic/plan/" + plan.getShareableLink() + "/community";
        Map<String, Object> payload = Map.of( "type", "DELETE_COMMENT", "progressId", progressId, "commentId", commentId );
        messagingTemplate.convertAndSend(destination, payload);
        // No feed event for comment deletion
    }


    @Override
    public void addOrUpdateReaction(Long progressId, String userEmail, AddReactionRequest request) {
        DailyProgress progress = findProgressById(progressId);
        User reactor = findUserByEmail(userEmail);
        ensureUserIsMemberOfPlan(reactor, progress.getPlanMember().getPlan());
        Plan plan = progress.getPlanMember().getPlan();

        ProgressReaction reaction = reactionRepository.findByDailyProgressIdAndUserId(progressId, reactor.getId()).orElse(new ProgressReaction());
        boolean typeChanged = reaction.getId() == null || reaction.getType() != request.getReactionType();

        reaction.setDailyProgress(progress);
        reaction.setUser(reactor);
        reaction.setType(request.getReactionType());
        ProgressReaction savedReaction = reactionRepository.save(reaction);

        // Gửi Notification
        User progressOwner = progress.getPlanMember().getUser();
        if (typeChanged && !reactor.getId().equals(progressOwner.getId())) {
             // *** THAY ĐỔI: Gọi helper method nội bộ ***
             String reactorName = getUserFullName(reactor);
             // *** KẾT THÚC THAY ĐỔI ***
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

        // GỬI MESSAGE WEBSOCKET UPDATE_REACTION
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
        Plan plan = reaction.getDailyProgress().getPlanMember().getPlan();
        Integer userIdToRemove = user.getId();

        reactionRepository.delete(reaction);
        log.info("User {} removed reaction from progress {}", userEmail, progressId);

        // GỬI MESSAGE WEBSOCKET REMOVE_REACTION
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

    private boolean isUserMemberOfPlan(User user, Plan plan) {
         if (plan == null || plan.getMembers() == null || user == null) return false;
         plan.getMembers().size(); // Trigger load if necessary
         return plan.getMembers().stream()
                    .anyMatch(member -> member.getUser() != null && member.getUser().getId().equals(user.getId()));
     }

    private DailyProgress findProgressById(Long id) {
        // Fetch progress with necessary associations needed by this service
        return progressRepository.findById(id)
                .map(p -> {
                    if (p.getPlanMember() != null) {
                        p.getPlanMember().getUser().getEmail(); // Fetch user
                        if (p.getPlanMember().getPlan() != null) {
                            p.getPlanMember().getPlan().getShareableLink(); // Fetch plan link
                            p.getPlanMember().getPlan().getTitle(); // Fetch plan title
                            p.getPlanMember().getPlan().getMembers().size(); // Fetch plan members for role check
                        }
                    }
                    return p;
                })
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tiến độ với ID: " + id));
    }

    // findProgressByIdWithDetails có thể không cần thiết nữa nếu findProgressById đã fetch đủ
    // private DailyProgress findProgressByIdWithDetails(Long id) { ... }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user với email: " + email));
    }

    private void ensureUserIsMemberOfPlan(User user, Plan plan) {
        if (!isUserMemberOfPlan(user, plan)) {
            throw new AccessDeniedException("Bạn không phải thành viên của kế hoạch này.");
        }
    }

    private ProgressComment findCommentById(Long id) {
        return commentRepository.findById(id)
                .map(c -> {
                    // Fetch các association cần thiết
                    if (c.getDailyProgress() != null) {
                        c.getDailyProgress().getDate(); // Fetch date
                        if (c.getDailyProgress().getPlanMember() != null) {
                            c.getDailyProgress().getPlanMember().getUser().getEmail(); // Fetch user
                             if (c.getDailyProgress().getPlanMember().getPlan() != null) {
                                 c.getDailyProgress().getPlanMember().getPlan().getShareableLink();
                                 c.getDailyProgress().getPlanMember().getPlan().getTitle();
                                 c.getDailyProgress().getPlanMember().getPlan().getMembers().size();
                             }
                        }
                    }
                    return c;
                })
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bình luận với ID: " + id));
    }

    // --- THÊM CÁC HELPER METHOD TỪ ProgressMapper CŨ VÀO ĐÂY ---

    /**
     * Chuyển đổi ProgressComment entity sang CommentResponse DTO.
     */
    private CommentResponse toCommentResponse(ProgressComment comment) {
         if (comment == null) return null;
         User author = comment.getAuthor();
         return CommentResponse.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .authorEmail(author != null ? author.getEmail() : "N/A")
                .authorFullName(author != null ? getUserFullName(author) : "Người dùng ẩn danh")
                // Thêm createdAt nếu có trong entity và DTO
                // .createdAt(comment.getCreatedAt())
                .build();
    }

    /**
     * Lấy tên đầy đủ của User (ưu tiên Customer, sau đó Employee, cuối cùng là email).
     */
    private String getUserFullName(User user) {
        if (user == null) return "N/A";
        // Ưu tiên Customer trước
        if (user.getCustomer() != null && user.getCustomer().getFullname() != null && !user.getCustomer().getFullname().isBlank()) {
            return user.getCustomer().getFullname();
        }
        // Sau đó Employee
        if (user.getEmployee() != null && user.getEmployee().getFullname() != null && !user.getEmployee().getFullname().isBlank()) {
            return user.getEmployee().getFullname();
        }
        return user.getEmail(); // Fallback về email
    }
    // --- KẾT THÚC THÊM HELPER METHOD ---
}