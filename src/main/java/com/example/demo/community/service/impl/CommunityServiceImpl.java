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
import com.example.demo.progress.dto.response.DailyProgressResponse;
import com.example.demo.progress.entity.DailyProgress;
import com.example.demo.progress.mapper.ProgressMapper;
import com.example.demo.progress.repository.DailyProgressRepository;
import com.example.demo.shared.exception.ResourceNotFoundException;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CommunityServiceImpl implements CommunityService {

    private final DailyProgressRepository progressRepository;
    private final UserRepository userRepository;
    private final ProgressReactionRepository reactionRepository;
    private final ProgressMapper progressMapper; // Chỉ dùng để map comment response
    private final ProgressCommentRepository commentRepository; // Thêm
    private final NotificationService notificationService;

    @Override
    public DailyProgressResponse.CommentResponse postComment(Long progressId, String userEmail, PostCommentRequest request) {
        DailyProgress progress = findProgressById(progressId);
        User author = findUserByEmail(userEmail);
        ensureUserIsMemberOfPlan(author, progress);

        ProgressComment comment = ProgressComment.builder()
                .dailyProgress(progress)
                .author(author)
                .content(request.getContent())
                .build();
        
        progress.getComments().add(comment);
        DailyProgress savedProgress = progressRepository.save(progress);

        // **[NEW LOGIC]** Send notification
        User progressOwner = progress.getPlanMember().getUser();
        if (!author.getId().equals(progressOwner.getId())) { // Don't send notification to self
            String authorName = progressMapper.getUserFullName(author); // Use helper to get full name
            String message = authorName + " đã bình luận về tiến độ của bạn.";
            String link = "/plans/" + progress.getPlanMember().getPlan().getShareableLink() + "?date=" + progress.getDate();
            notificationService.createNotification(progressOwner, message, link);
        }
        
        ProgressComment savedComment = savedProgress.getComments().get(savedProgress.getComments().size() - 1);
        return progressMapper.toCommentResponse(savedComment);
    }

    @Override
    public void addOrUpdateReaction(Long progressId, String userEmail, AddReactionRequest request) {
        DailyProgress progress = findProgressById(progressId);
        User reactor = findUserByEmail(userEmail);
        ensureUserIsMemberOfPlan(reactor, progress);

        ProgressReaction reaction = reactionRepository.findByDailyProgressIdAndUserId(progressId, reactor.getId())
                .orElse(new ProgressReaction());
        
        reaction.setDailyProgress(progress);
        reaction.setUser(reactor);
        reaction.setType(request.getReactionType());
        
        reactionRepository.save(reaction);

        // **[NEW LOGIC]** Send notification for reaction
        User progressOwner = progress.getPlanMember().getUser();
        if (!reactor.getId().equals(progressOwner.getId())) {
            String reactorName = progressMapper.getUserFullName(reactor);
            String message = reactorName + " đã bày tỏ cảm xúc về tiến độ của bạn.";
            String link = "/plans/" + progress.getPlanMember().getPlan().getShareableLink() + "?date=" + progress.getDate();
            notificationService.createNotification(progressOwner, message, link);
        }
    }

    @Override
    public void removeReaction(Long progressId, String userEmail) {
        User user = findUserByEmail(userEmail);
        ProgressReaction reaction = reactionRepository.findByDailyProgressIdAndUserId(progressId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Bạn chưa thả reaction nào."));
        reactionRepository.delete(reaction);
    }
    
    // --- Helper Methods ---
    private DailyProgress findProgressById(Long id) {
        return progressRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tiến độ với ID: " + id));
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
    
    @Override
    public DailyProgressResponse.CommentResponse updateComment(Long commentId, String userEmail, UpdateCommentRequest request) {
        ProgressComment comment = findCommentById(commentId);
        User user = findUserByEmail(userEmail);

        // Chỉ tác giả của bình luận mới có quyền sửa
        if (!comment.getAuthor().getId().equals(user.getId())) {
            throw new AccessDeniedException("Bạn không có quyền sửa bình luận này.");
        }

        comment.setContent(request.getContent());
        ProgressComment updatedComment = commentRepository.save(comment);
        return progressMapper.toCommentResponse(updatedComment); // Giả sử toCommentResponse đã có trong ProgressMapper
    }

    @Override
    public void deleteComment(Long commentId, String userEmail) {
        ProgressComment comment = findCommentById(commentId);
        User user = findUserByEmail(userEmail);

        // Kiểm tra xem người dùng có phải là tác giả hoặc chủ kế hoạch không
        boolean isAuthor = comment.getAuthor().getId().equals(user.getId());
        boolean isPlanOwner = comment.getDailyProgress().getPlanMember().getPlan().getMembers().stream()
                .anyMatch(m -> m.getUser().getId().equals(user.getId()) && m.getRole() == MemberRole.OWNER);

        if (!isAuthor && !isPlanOwner) {
            throw new AccessDeniedException("Bạn không có quyền xóa bình luận này.");
        }

        commentRepository.delete(comment);
    }

    // --- Helper Methods ---
    private ProgressComment findCommentById(Long id) {
        return commentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bình luận với ID: " + id));
    }
}