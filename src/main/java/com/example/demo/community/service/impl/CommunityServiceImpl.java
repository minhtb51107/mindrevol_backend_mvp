package com.example.demo.community.service.impl;

import com.example.demo.community.dto.request.AddReactionRequest;
import com.example.demo.community.dto.request.PostCommentRequest;
import com.example.demo.community.dto.request.UpdateCommentRequest;
import com.example.demo.community.entity.ProgressComment;
import com.example.demo.community.entity.ProgressReaction;
import com.example.demo.community.repository.ProgressCommentRepository;
import com.example.demo.community.repository.ProgressReactionRepository;
import com.example.demo.community.service.CommunityService;
import com.example.demo.notification.service.NotificationService;
import com.example.demo.plan.entity.MemberRole;
import com.example.demo.plan.entity.Plan; // *** THÊM IMPORT ***
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

import java.util.Map; // *** THÊM IMPORT ***

import com.example.demo.plan.entity.PlanMember; // Đảm bảo có import này
import java.util.regex.Matcher; // *** THÊM IMPORT ***
import java.util.regex.Pattern; // *** THÊM IMPORT ***
import java.util.Set; // *** THÊM IMPORT ***
import java.util.HashSet; // *** THÊM IMPORT ***

@Slf4j // *** THÊM @Slf4j NẾU CHƯA CÓ ***
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
    private final SimpMessagingTemplate messagingTemplate; // *** INJECT SimpMessagingTemplate ***
    
 // *** Định nghĩa Pattern để tìm mention dạng @[Display Name](userId) ***
    // Pattern này tìm: @[ ký tự bất kỳ trừ ] ]( कैप्चर group là userId )
    private static final Pattern MENTION_PATTERN = Pattern.compile("@\\[[^\\]]+?\\]\\((\\d+?)\\)"); // Giả sử userId là số (Integer)

    @Override
    public DailyProgressResponse.CommentResponse postComment(Long progressId, String userEmail, PostCommentRequest request) {
        DailyProgress progress = findProgressById(progressId);
        User author = findUserByEmail(userEmail);
        ensureUserIsMemberOfPlan(author, progress);

        ProgressComment comment = ProgressComment.builder()
                .dailyProgress(progress)
                .author(author)
                .content(request.getContent()) // Nội dung có thể chứa mention
                .build();

        ProgressComment savedComment = commentRepository.save(comment);
        Plan plan = progress.getPlanMember().getPlan();
        String authorName = progressMapper.getUserFullName(author);

        // --- XỬ LÝ NOTIFICATION THÔNG THƯỜNG (CHO CHỦ SỞ HỮU PROGRESS) ---
        User progressOwner = progress.getPlanMember().getUser();
        if (!author.getId().equals(progressOwner.getId())) {
            String message = authorName + " đã bình luận về tiến độ ngày " + progress.getDate() + " của bạn.";
            // Tạo link với comment ID để có thể scroll tới
            String link = String.format("/plan/%s?date=%s#comment-%d",
                                        plan.getShareableLink(), progress.getDate(), savedComment.getId());
            notificationService.createNotification(progressOwner, message, link);
        }

        // --- XỬ LÝ NOTIFICATION CHO MENTION ---
        Set<Integer> mentionedUserIds = extractMentionedUserIds(savedComment.getContent());
        log.debug("Mentioned User IDs in comment {}: {}", savedComment.getId(), mentionedUserIds);
        for (Integer mentionedUserId : mentionedUserIds) {
            // Không gửi notification nếu tự mention hoặc mention chủ sở hữu progress (đã nhận noti ở trên)
            if (!mentionedUserId.equals(author.getId()) && !mentionedUserId.equals(progressOwner.getId())) {
                userRepository.findById(mentionedUserId).ifPresent(mentionedUser -> {
                    // Kiểm tra xem người được mention có còn là thành viên không (đề phòng)
                    if (isUserMemberOfPlan(mentionedUser, progress)) {
                        String mentionMessage = String.format("%s đã nhắc đến bạn trong một bình luận về tiến độ ngày %s.",
                                                            authorName, progress.getDate());
                        // Link tương tự như trên
                        String mentionLink = String.format("/plan/%s?date=%s#comment-%d",
                                                         plan.getShareableLink(), progress.getDate(), savedComment.getId());
                        notificationService.createNotification(mentionedUser, mentionMessage, mentionLink);
                        log.info("Sent mention notification to user {} from comment {}", mentionedUserId, savedComment.getId());
                    } else {
                         log.warn("User {} mentioned in comment {} is no longer a member of plan {}.", mentionedUserId, savedComment.getId(), plan.getId());
                    }
                });
            }
        }
        // --- KẾT THÚC XỬ LÝ MENTION ---


        // --- GỬI MESSAGE WEBSOCKET (giữ nguyên) ---
        String destination = "/topic/plan/" + plan.getShareableLink() + "/community";
        DailyProgressResponse.CommentResponse commentResponse = progressMapper.toCommentResponse(savedComment);
        Map<String, Object> payload = Map.of( "type", "NEW_COMMENT", "progressId", progressId, "comment", commentResponse );
        messagingTemplate.convertAndSend(destination, payload);
        // --- KẾT THÚC GỬI WEBSOCKET ---

        return commentResponse;
    }

    @Override
    public DailyProgressResponse.CommentResponse updateComment(Long commentId, String userEmail, UpdateCommentRequest request) {
        ProgressComment comment = findCommentById(commentId);
        User user = findUserByEmail(userEmail);

        if (!comment.getAuthor().getId().equals(user.getId())) {
            throw new AccessDeniedException("Bạn không có quyền sửa bình luận này.");
        }

        String oldContent = comment.getContent(); // Lưu lại nội dung cũ để so sánh mention
        comment.setContent(request.getContent()); // Cập nhật nội dung mới
        ProgressComment updatedComment = commentRepository.save(comment);

        DailyProgress progress = comment.getDailyProgress();
        Plan plan = progress.getPlanMember().getPlan();
        String authorName = progressMapper.getUserFullName(user);

        // --- XỬ LÝ NOTIFICATION CHO MENTION KHI UPDATE ---
        Set<Integer> oldMentionedIds = extractMentionedUserIds(oldContent);
        Set<Integer> newMentionedIds = extractMentionedUserIds(updatedComment.getContent());
        // Chỉ gửi cho những người mới được mention (chưa có trong oldMentionedIds)
        for (Integer mentionedUserId : newMentionedIds) {
            if (!oldMentionedIds.contains(mentionedUserId) && // Là mention mới
                !mentionedUserId.equals(user.getId()) &&    // Không phải tự mention
                !mentionedUserId.equals(progress.getPlanMember().getUser().getId())) { // Không phải chủ progress
                userRepository.findById(mentionedUserId).ifPresent(mentionedUser -> {
                     if (isUserMemberOfPlan(mentionedUser, progress)) { // Kiểm tra là thành viên
                        String mentionMessage = String.format("%s đã nhắc đến bạn trong một bình luận đã sửa về tiến độ ngày %s.",
                                                            authorName, progress.getDate());
                        String mentionLink = String.format("/plan/%s?date=%s#comment-%d",
                                                         plan.getShareableLink(), progress.getDate(), updatedComment.getId());
                        notificationService.createNotification(mentionedUser, mentionMessage, mentionLink);
                        log.info("Sent mention notification (update) to user {} from comment {}", mentionedUserId, updatedComment.getId());
                     }
                });
            }
        }
        // --- KẾT THÚC XỬ LÝ MENTION ---

        // --- GỬI MESSAGE WEBSOCKET (giữ nguyên) ---
        DailyProgressResponse.CommentResponse commentResponse = progressMapper.toCommentResponse(updatedComment);
        String destination = "/topic/plan/" + plan.getShareableLink() + "/community";
        Map<String, Object> payload = Map.of( "type", "UPDATE_COMMENT", "progressId", progress.getId(), "comment", commentResponse );
        messagingTemplate.convertAndSend(destination, payload);
        // --- KẾT THÚC GỬI WEBSOCKET ---

        return commentResponse;
    }

    @Override
    public void deleteComment(Long commentId, String userEmail) {
        // ... (Logic xóa và gửi WebSocket giữ nguyên) ...
        ProgressComment comment = findCommentById(commentId);
        User user = findUserByEmail(userEmail);
        DailyProgress progress = comment.getDailyProgress();
        Plan plan = progress.getPlanMember().getPlan();
        Long progressId = progress.getId();

        boolean isAuthor = comment.getAuthor().getId().equals(user.getId());
        boolean isPlanOwner = comment.getDailyProgress().getPlanMember().getPlan().getMembers().stream()
                .anyMatch(m -> m.getUser().getId().equals(user.getId()) && m.getRole() == MemberRole.OWNER);

        if (!isAuthor && !isPlanOwner) {
            throw new AccessDeniedException("Bạn không có quyền xóa bình luận này.");
        }

        commentRepository.delete(comment);

        // --- GỬI MESSAGE WEBSOCKET (giữ nguyên) ---
        String destination = "/topic/plan/" + plan.getShareableLink() + "/community";
        Map<String, Object> payload = Map.of( "type", "DELETE_COMMENT", "progressId", progressId, "commentId", commentId );
        messagingTemplate.convertAndSend(destination, payload);
        // --- KẾT THÚC GỬI WEBSOCKET ---
    }


    @Override
    public void addOrUpdateReaction(Long progressId, String userEmail, AddReactionRequest request) {
        // ... (Logic reaction và gửi WebSocket giữ nguyên) ...
        DailyProgress progress = findProgressById(progressId);
        User reactor = findUserByEmail(userEmail);
        ensureUserIsMemberOfPlan(reactor, progress);
        ProgressReaction reaction = reactionRepository.findByDailyProgressIdAndUserId(progressId, reactor.getId()).orElse(new ProgressReaction());
        boolean typeChanged = reaction.getId() == null || reaction.getType() != request.getReactionType();
        reaction.setDailyProgress(progress); reaction.setUser(reactor); reaction.setType(request.getReactionType());
        reactionRepository.save(reaction);
        User progressOwner = progress.getPlanMember().getUser();
        if (typeChanged && !reactor.getId().equals(progressOwner.getId())) {
             String reactorName = progressMapper.getUserFullName(reactor);
             String message = reactorName + " đã bày tỏ cảm xúc về tiến độ ngày " + progress.getDate() + " của bạn.";
             String link = String.format("/plan/%s?date=%s#progress-%d", // Link đến progress thay vì comment
                                        progress.getPlanMember().getPlan().getShareableLink(), progress.getDate(), progressId);
             notificationService.createNotification(progressOwner, message, link);
        }
        DailyProgress updatedProgress = findProgressByIdWithDetails(progressId);
        Plan plan = updatedProgress.getPlanMember().getPlan();
        String destination = "/topic/plan/" + plan.getShareableLink() + "/community";
        Map<String, Object> simplePayload = Map.of( "type", "UPDATE_REACTION", "progressId", progressId, "userId", reactor.getId(), "reactionType", request.getReactionType().name() );
        messagingTemplate.convertAndSend(destination, simplePayload);
    }

    @Override
    public void removeReaction(Long progressId, String userEmail) {
       // ... (Logic reaction và gửi WebSocket giữ nguyên) ...
        User user = findUserByEmail(userEmail);
        ProgressReaction reaction = reactionRepository.findByDailyProgressIdAndUserId(progressId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Bạn chưa thả reaction nào."));
        DailyProgress progress = reaction.getDailyProgress();
        Plan plan = progress.getPlanMember().getPlan();
        Integer userIdToRemove = user.getId();
        reactionRepository.delete(reaction);
        String destination = "/topic/plan/" + plan.getShareableLink() + "/community";
         Map<String, Object> payload = Map.of( "type", "REMOVE_REACTION", "progressId", progressId, "userId", userIdToRemove );
        messagingTemplate.convertAndSend(destination, payload);
    }

    // --- Helper Methods ---

    // *** THÊM HELPER TRÍCH XUẤT MENTION ***
    private Set<Integer> extractMentionedUserIds(String content) {
        Set<Integer> userIds = new HashSet<>();
        if (content == null || content.isBlank()) {
            return userIds;
        }
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            try {
                // Group 1 là phần userId trong dấu ngoặc đơn
                Integer userId = Integer.parseInt(matcher.group(1));
                userIds.add(userId);
            } catch (NumberFormatException e) {
                log.warn("Could not parse user ID from mention: {}", matcher.group(1));
                // Bỏ qua nếu không parse được ID
            }
        }
        return userIds;
    }

    // *** SỬA HELPER isUserMemberOfPlan ĐỂ NHẬN User entity ***
    // (Hoặc tạo helper mới nếu cần giữ cái cũ)
    private boolean isUserMemberOfPlan(User user, DailyProgress progress) {
        // Dùng Plan entity lấy từ progress để kiểm tra
        return progress.getPlanMember().getPlan().getMembers().stream()
                .anyMatch(member -> member.getUser() != null && member.getUser().getId().equals(user.getId()));
    }
    // --- Helper Methods ---
    private DailyProgress findProgressById(Long id) {
        return progressRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tiến độ với ID: " + id));
    }

    // Helper mới để fetch cả comment/reaction nếu cần (ví dụ cho reaction update)
    private DailyProgress findProgressByIdWithDetails(Long id) {
         return progressRepository.findByIdWithDetails(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tiến độ với ID: " + id));
     }


    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user với email: " + email));
    }

    private void ensureUserIsMemberOfPlan(User user, DailyProgress progress) {
        boolean isMember = progress.getPlanMember().getPlan().getMembers().stream()
                .anyMatch(member -> member.getUser().getId().equals(user.getId()));
        if (!isMember) {
            throw new AccessDeniedException("Bạn không phải thành viên của kế hoạch này.");
        }
    }

    private ProgressComment findCommentById(Long id) {
        return commentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bình luận với ID: " + id));
    }
}