package com.example.demo.user.service.impl;

import com.example.demo.plan.service.impl.PlanServiceImpl;
import com.example.demo.shared.exception.BadRequestException;
import com.example.demo.shared.exception.ResourceNotFoundException;
import com.example.demo.user.dto.response.FriendResponseDto;
import com.example.demo.user.entity.Friendship;
import com.example.demo.user.entity.FriendshipStatus;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.FriendshipRepository;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.service.FriendService;
import com.example.demo.user.service.UserService; // Giả sử bạn có cái này
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder; // Cần để lấy user hiện tại
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class FriendServiceImpl implements FriendService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    // private final UserService userService; // Hoặc một cách nào đó để lấy user hiện tại

    // Hàm helper để lấy user đang đăng nhập
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + email));
    }

    // Hàm helper để map User sang DTO
    private FriendResponseDto mapToFriendResponseDto(Friendship friendship, User friendUser) {
        return FriendResponseDto.builder()
                .friendshipId(friendship.getId())
                .userId(friendUser.getId().longValue())
                .fullName(friendUser.getFullname())
                .email(friendUser.getEmail())
                .photoUrl(friendUser.getPhoto())
                .build();
    }

    @Override
    public void sendFriendRequest(String receiverEmail) {
        User requester = getCurrentUser();
        User receiver = userRepository.findByEmail(receiverEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + receiverEmail));

        // ... (Toàn bộ logic validation của bạn giữ nguyên) ...
        if (Objects.equals(requester.getId(), receiver.getId())) {
            throw new BadRequestException("Bạn không thể tự kết bạn với chính mình.");
        }
        boolean exists = friendshipRepository.existsByRequesterAndReceiver(requester, receiver) ||
                         friendshipRepository.existsByRequesterAndReceiver(receiver, requester);
        if (exists) {
            throw new BadRequestException("Đã gửi lời mời hoặc đã là bạn.");
        }
        // ... (Kết thúc logic validation) ...

        Friendship friendship = new Friendship();
        friendship.setRequester(requester);
        friendship.setReceiver(receiver);
        friendship.setStatus(FriendshipStatus.PENDING);
        friendshipRepository.save(friendship);
        
        // === BẮT ĐẦU NÂNG CẤP WEBSOCKET ===
        try {
            // Định nghĩa topic cá nhân của NGƯỜI NHẬN
            String userTopic = "/topic/user/" + receiver.getId() + "/friends"; 
            
            // Tạo payload (nội dung tin nhắn)
            Map<String, Object> payload = Map.of(
                "type", "NEW_FRIEND_REQUEST",
                "fromUserFullName", requester.getFullname() // Lấy tên người gửi
            );
            
            // Gửi tin nhắn
            messagingTemplate.convertAndSend(userTopic, payload);
            log.info("Đã gửi thông báo NEW_FRIEND_REQUEST (WebSocket) đến user ID: {}", receiver.getId());

        } catch (Exception e) {
            log.error("Gửi WebSocket NEW_FRIEND_REQUEST thất bại: {}", e.getMessage());
            // Không ném lỗi ra, vì luồng chính (lưu CSDL) đã thành công
        }
        // === KẾT THÚC NÂNG CẤP WEBSOCKET ===
    }

    @Override
    public void acceptFriendRequest(Long friendshipId) {
        User currentUser = getCurrentUser();
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lời mời."));

        // Chỉ người nhận (receiver) mới có quyền chấp nhận
        if (!Objects.equals(friendship.getReceiver().getId(), currentUser.getId())) {
            throw new BadRequestException("Bạn không có quyền thực hiện hành động này.");
        }

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new BadRequestException("Lời mời này đã được xử lý.");
        }

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(friendship);
    }

    @Override
    public void declineFriendRequest(Long friendshipId) {
        User currentUser = getCurrentUser();
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lời mời."));

        // Cả người gửi (hủy) hoặc người nhận (từ chối) đều có thể
        if (!Objects.equals(friendship.getReceiver().getId(), currentUser.getId()) &&
            !Objects.equals(friendship.getRequester().getId(), currentUser.getId())) {
            throw new BadRequestException("Bạn không có quyền thực hiện hành động này.");
        }
        
        // Thay vì xóa, chúng ta chỉ cập nhật status (hoặc xóa)
        // friendship.setStatus(FriendshipStatus.DECLINED);
        // friendshipRepository.save(friendship);
        // Hoặc xóa luôn để cho gọn
        friendshipRepository.delete(friendship);
    }

    @Override
    public void removeFriend(Long friendUserId) {
        User currentUser = getCurrentUser();
        User friendUser = userRepository.findById(friendUserId.intValue())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng."));

        // Tìm quan hệ (bất kể chiều nào) mà đã ACCEPTED
        Optional<Friendship> friendshipOpt = friendshipRepository.findAllAcceptedFriendships(currentUser).stream()
            .filter(f -> f.getRequester().equals(friendUser) || f.getReceiver().equals(friendUser))
            .findFirst();

        if (friendshipOpt.isEmpty()) {
            throw new BadRequestException("Bạn và người này không phải là bạn bè.");
        }

        friendshipRepository.delete(friendshipOpt.get());
    }

    @Override
    public List<FriendResponseDto> getMyFriends() {
        User currentUser = getCurrentUser();
        List<Friendship> friendships = friendshipRepository.findAllAcceptedFriendships(currentUser);

        return friendships.stream().map(f -> {
            // Nếu tôi là người gửi, thì bạn bè là người nhận
            User friendUser = f.getRequester().equals(currentUser) ? f.getReceiver() : f.getRequester();
            return mapToFriendResponseDto(f, friendUser);
        }).collect(Collectors.toList());
    }

    @Override
    public List<FriendResponseDto> getPendingRequests() {
        User currentUser = getCurrentUser();
        // Lấy danh sách lời mời MÀ TÔI LÀ NGƯỜI NHẬN
        List<Friendship> friendships = friendshipRepository.findByReceiverAndStatus(currentUser, FriendshipStatus.PENDING);

        return friendships.stream().map(f -> {
            // Hiển thị thông tin của người gửi (requester)
            return mapToFriendResponseDto(f, f.getRequester());
        }).collect(Collectors.toList());
    }
}