package com.example.demo.community.service.impl;

import com.example.demo.community.dto.request.AddReactionRequest;
import com.example.demo.community.dto.request.PostCommentRequest;
import com.example.demo.community.dto.request.UpdateCommentRequest;
import com.example.demo.community.dto.response.CommentResponse;
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
// import com.example.demo.progress.entity.DailyProgress; // <-- XÓA BỎ
// import com.example.demo.progress.repository.DailyProgressRepository; // <-- XÓA BỎ
import com.example.demo.progress.entity.checkin.CheckInEvent; // <-- THÊM MỚI
import com.example.demo.progress.repository.CheckInEventRepository; // <-- THÊM MỚI
import com.example.demo.shared.exception.ResourceNotFoundException;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId; // <-- THÊM IMPORT NÀY
import java.time.format.DateTimeFormatter; // <-- THÊM IMPORT NÀY
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

    // === THAY ĐỔI DEPENDENCIES ===
    // private final DailyProgressRepository progressRepository; // <-- XÓA BỎ
    private final CheckInEventRepository checkInEventRepository; // <-- THAY THẾ
    private final UserRepository userRepository;
    private final ProgressReactionRepository reactionRepository;
    private final ProgressCommentRepository commentRepository;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final FeedService feedService;
    // === KẾT THÚC THAY ĐỔI ===

    private static final Pattern MENTION_PATTERN = Pattern.compile("@\\[[^\\]]+?\\]\\((\\d+?)\\)");
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh"); // Giữ lại Timezone

    @Override
    public CommentResponse postComment(Long checkInEventId, String userEmail, PostCommentRequest request) { // <-- SỬA TÊN THAM SỐ
        CheckInEvent checkInEvent = findCheckInEventById(checkInEventId); // <-- SỬA LOGIC FIND
        User author = findUserByEmail(userEmail);
        Plan plan = checkInEvent.getPlanMember().getPlan(); // Lấy plan từ checkInEvent
        ensureUserIsMemberOfPlan(author, plan);

        ProgressComment comment = ProgressComment.builder()
                .checkInEvent(checkInEvent) // <-- SỬA TRƯỜNG
                .author(author)
                .content(request.getContent())
                .build();
        // (Trường createdAt trong ProgressComment đã có @Builder.Default nên tự động được set)

        ProgressComment savedComment = commentRepository.save(comment);
        CommentResponse commentResponse = toCommentResponse(savedComment);
        String authorName = getUserFullName(author);

        // --- XỬ LÝ NOTIFICATION THÔNG THƯỜNG & MENTION ---
        User progressOwner = checkInEvent.getPlanMember().getUser();
        String progressDateStr = checkInEvent.getCheckInTimestamp().atZone(VIETNAM_ZONE).toLocalDate().toString();
        
        if (!author.getId().equals(progressOwner.getId())) {
             String message = authorName + " đã bình luận về check-in (" + formatTime(checkInEvent) + ") của bạn trong kế hoạch '" + plan.getTitle() + "'.";
             String link = String.format("/plan/%s?date=%s#comment-%d", plan.getShareableLink(), progressDateStr, savedComment.getId());
             notificationService.createNotification(progressOwner, message, link);
        }
        Set<Integer> mentionedUserIds = extractMentionedUserIds(savedComment.getContent());
        for (Integer mentionedUserId : mentionedUserIds) {
            if (!mentionedUserId.equals(author.getId()) && !mentionedUserId.equals(progressOwner.getId())) {
                userRepository.findById(mentionedUserId).ifPresent(mentionedUser -> {
                    if (isUserMemberOfPlan(mentionedUser, plan)) {
                        String mentionMessage = String.format("%s đã nhắc đến bạn trong bình luận về check-in (%s) trong %s.", authorName, formatTime(checkInEvent), plan.getTitle());
                        String mentionLink = String.format("/plan/%s?date=%s#comment-%d", plan.getShareableLink(), progressDateStr, savedComment.getId());
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
        details.put("checkInEventId", checkInEventId); // Sửa progressId -> checkInEventId
        feedService.createAndPublishFeedEvent(FeedEventType.COMMENT_ADDED, author, plan, details);
        // *** KẾT THÚC GỬI FEED EVENT ***

        // GỬI MESSAGE WEBSOCKET NEW_COMMENT
        String destination = "/topic/plan/" + plan.getShareableLink() + "/community";
        Map<String, Object> payload = Map.of( "type", "NEW_COMMENT", "checkInEventId", checkInEventId, "comment", commentResponse ); // Sửa progressId
        messagingTemplate.convertAndSend(destination, payload);

        return commentResponse;
    }

    @Override
    public CommentResponse updateComment(Long commentId, String userEmail, UpdateCommentRequest request) {
        ProgressComment comment = findCommentById(commentId);
        User user = findUserByEmail(userEmail);

        if (!comment.getAuthor().getId().equals(user.getId())) {
            throw new AccessDeniedException("Bạn không có quyền sửa bình luận này.");
        }

        String oldContent = comment.getContent();
        comment.setContent(request.getContent());
        ProgressComment updatedComment = commentRepository.save(comment);

        CheckInEvent checkInEvent = comment.getCheckInEvent(); // Sửa
        Plan plan = checkInEvent.getPlanMember().getPlan(); // Sửa
        CommentResponse commentResponse = toCommentResponse(updatedComment);
        String authorName = getUserFullName(user);
        
        String progressDateStr = checkInEvent.getCheckInTimestamp().atZone(VIETNAM_ZONE).toLocalDate().toString();

        // --- XỬ LÝ NOTIFICATION MENTION KHI UPDATE ---
         Set<Integer> oldMentionedIds = extractMentionedUserIds(oldContent);
         Set<Integer> newMentionedIds = extractMentionedUserIds(updatedComment.getContent());
         for (Integer mentionedUserId : newMentionedIds) {
             if (!oldMentionedIds.contains(mentionedUserId) &&
                 !mentionedUserId.equals(user.getId()) &&
                 !mentionedUserId.equals(checkInEvent.getPlanMember().getUser().getId())) { // Sửa
                 userRepository.findById(mentionedUserId).ifPresent(mentionedUser -> {
                      if (isUserMemberOfPlan(mentionedUser, plan)) {
                         String mentionMessage = String.format("%s đã nhắc đến bạn trong bình luận đã sửa về check-in (%s) trong %s.", authorName, formatTime(checkInEvent), plan.getTitle()); // Sửa
                         String mentionLink = String.format("/plan/%s?date=%s#comment-%d", plan.getShareableLink(), progressDateStr, updatedComment.getId()); // Sửa
                         notificationService.createNotification(mentionedUser, mentionMessage, mentionLink);
                         log.info("Sent mention notification (update) to user {} from comment {}", mentionedUserId, updatedComment.getId());
                      }
                 });
             }
         }
        // --- KẾT THÚC XỬ LÝ NOTIFICATION ---

        // GỬI MESSAGE WEBSOCKET UPDATE_COMMENT
        String destination = "/topic/plan/" + plan.getShareableLink() + "/community";
        Map<String, Object> payload = Map.of( "type", "UPDATE_COMMENT", "checkInEventId", checkInEvent.getId(), "comment", commentResponse ); // Sửa
        messagingTemplate.convertAndSend(destination, payload);
        
        return commentResponse;
    }

    @Override
    public void deleteComment(Long commentId, String userEmail) {
        ProgressComment comment = findCommentById(commentId);
        User user = findUserByEmail(userEmail);
        CheckInEvent checkInEvent = comment.getCheckInEvent(); // Sửa
        Plan plan = checkInEvent.getPlanMember().getPlan(); // Sửa
        Long checkInEventId = checkInEvent.getId(); // Store before deleting comment

        boolean isAuthor = comment.getAuthor().getId().equals(user.getId());
        plan.getMembers().size(); 
        boolean isPlanOwner = plan.getMembers().stream()
                .anyMatch(m -> m.getUser() != null && m.getUser().getId().equals(user.getId()) && m.getRole() == MemberRole.OWNER);

        if (!isAuthor && !isPlanOwner) {
            throw new AccessDeniedException("Bạn không có quyền xóa bình luận này.");
        }

        commentRepository.delete(comment);
        log.info("User {} deleted comment {}", userEmail, commentId);

        // GỬI MESSAGE WEBSOCKET DELETE_COMMENT
        String destination = "/topic/plan/" + plan.getShareableLink() + "/community";
        Map<String, Object> payload = Map.of( "type", "DELETE_COMMENT", "checkInEventId", checkInEventId, "commentId", commentId ); // Sửa
        messagingTemplate.convertAndSend(destination, payload);
    }


    @Override
    public void addOrUpdateReaction(Long checkInEventId, String userEmail, AddReactionRequest request) { // Sửa
        CheckInEvent checkInEvent = findCheckInEventById(checkInEventId); // Sửa
        User reactor = findUserByEmail(userEmail);
        Plan plan = checkInEvent.getPlanMember().getPlan(); // Sửa
        ensureUserIsMemberOfPlan(reactor, plan);

        // Sửa query
        ProgressReaction reaction = reactionRepository.findByCheckInEventIdAndUserId(checkInEventId, reactor.getId()) 
                .orElse(new ProgressReaction());
        boolean typeChanged = reaction.getId() == null || reaction.getType() != request.getReactionType();

        reaction.setCheckInEvent(checkInEvent); // Sửa
        reaction.setUser(reactor);
        reaction.setType(request.getReactionType());
        ProgressReaction savedReaction = reactionRepository.save(reaction);

        // Gửi Notification
        User progressOwner = checkInEvent.getPlanMember().getUser();
        String progressDateStr = checkInEvent.getCheckInTimestamp().atZone(VIETNAM_ZONE).toLocalDate().toString();
        
        if (typeChanged && !reactor.getId().equals(progressOwner.getId())) {
             String reactorName = getUserFullName(reactor);
             String message = reactorName + " đã bày tỏ cảm xúc về check-in (" + formatTime(checkInEvent) + ") của bạn trong kế hoạch '" + plan.getTitle() + "'."; // Sửa
             String link = String.format("/plan/%s?date=%s#checkin-%d", plan.getShareableLink(), progressDateStr, checkInEventId); // Sửa
             notificationService.createNotification(progressOwner, message, link);
        }

        // *** GỬI FEED EVENT REACTION_ADDED (NẾU typeChanged) ***
        if (typeChanged) {
            Map<String, Object> details = Map.of(
                "reactionType", savedReaction.getType().name(),
                "checkInEventId", checkInEventId, // Sửa
                "progressDate", progressDateStr // Thêm ngày
            );
            feedService.createAndPublishFeedEvent(FeedEventType.REACTION_ADDED, reactor, plan, details);
        }
        // *** KẾT THÚC GỬI FEED EVENT ***

        // GỬI MESSAGE WEBSOCKET UPDATE_REACTION
        String destination = "/topic/plan/" + plan.getShareableLink() + "/community";
        Map<String, Object> simplePayload = Map.of(
            "type", "UPDATE_REACTION",
            "checkInEventId", checkInEventId, // Sửa
            "userId", reactor.getId(),
            "reactionType", request.getReactionType().name()
        );
        messagingTemplate.convertAndSend(destination, simplePayload);
    }

    @Override
    public void removeReaction(Long checkInEventId, String userEmail) { // Sửa
        User user = findUserByEmail(userEmail);
        // Sửa query
        ProgressReaction reaction = reactionRepository.findByCheckInEventIdAndUserId(checkInEventId, user.getId()) 
                .orElseThrow(() -> new ResourceNotFoundException("Bạn chưa thả reaction nào."));
        Plan plan = reaction.getCheckInEvent().getPlanMember().getPlan(); // Sửa
        Integer userIdToRemove = user.getId();

        reactionRepository.delete(reaction);
        log.info("User {} removed reaction from check-in {}", userEmail, checkInEventId);

        // GỬI MESSAGE WEBSOCKET REMOVE_REACTION
        String destination = "/topic/plan/" + plan.getShareableLink() + "/community";
         Map<String, Object> payload = Map.of(
            "type", "REMOVE_REACTION",
            "checkInEventId", checkInEventId, // Sửa
            "userId", userIdToRemove
        );
        messagingTemplate.convertAndSend(destination, payload);
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

    // === THÊM HELPER MỚI ===
    private CheckInEvent findCheckInEventById(Long id) {
        // Fetch check-in event với các associations cần thiết
        return checkInEventRepository.findById(id)
                .map(event -> {
                    // Eagerly fetch associations
                    event.getPlanMember().getUser().getEmail(); 
                    Plan plan = event.getPlanMember().getPlan();
                    plan.getShareableLink(); 
                    plan.getTitle(); 
                    plan.getMembers().size(); 
                    return event;
                })
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự kiện check-in với ID: " + id));
    }
    // === KẾT THÚC THÊM HELPER MỚI ===


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
                    // Fetch các association cần thiết (đã đổi sang checkInEvent)
                    if (c.getCheckInEvent() != null) {
                        c.getCheckInEvent().getCheckInTimestamp(); // Fetch timestamp
                        if (c.getCheckInEvent().getPlanMember() != null) {
                            c.getCheckInEvent().getPlanMember().getUser().getEmail(); // Fetch user
                             if (c.getCheckInEvent().getPlanMember().getPlan() != null) {
                                 c.getCheckInEvent().getPlanMember().getPlan().getShareableLink();
                                 c.getCheckInEvent().getPlanMember().getPlan().getTitle();
                                 c.getCheckInEvent().getPlanMember().getPlan().getMembers().size();
                             }
                        }
                    }
                    return c;
                })
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bình luận với ID: " + id));
    }

    // --- HELPER METHODS (ĐÃ SỬA LỖI) ---

    /**
     * Chuyển đổi ProgressComment entity sang CommentResponse DTO.
     * SỬA: Bỏ .createdAt() vì DTO không có.
     */
    private CommentResponse toCommentResponse(ProgressComment comment) {
         if (comment == null) return null;
         User author = comment.getAuthor();
         return CommentResponse.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .authorEmail(author != null ? author.getEmail() : "N/A")
                .authorFullName(author != null ? getUserFullName(author) : "Người dùng ẩn danh")
                // .createdAt(comment.getCreatedAt()) // <-- LỖI GÂY RA DO TÔI "ĐOÁN", DTO KHÔNG CÓ TRƯỜNG NÀY
                .build();
    }

    /**
     * Lấy tên đầy đủ của User (ưu tiên Customer, sau đó Employee, cuối cùng là email).
     * SỬA: Bỏ .getFullName() vì User entity không có.
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
        // LỖI GÂY RA DO TÔI "ĐOÁN":
        // if (user.getFullName() != null && !user.getFullName().isBlank()) {
        //     return user.getFullName();
        // }
        return user.getEmail(); // Fallback về email (luôn tồn tại)
    }
    
    /**
     * Helper để format thời gian (HH:mm)
     */
    private String formatTime(CheckInEvent event) {
        if (event == null || event.getCheckInTimestamp() == null) return "N/A";
        return event.getCheckInTimestamp().atZone(VIETNAM_ZONE).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
    }
}