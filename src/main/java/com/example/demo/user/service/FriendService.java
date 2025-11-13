package com.example.demo.user.service;

import com.example.demo.user.dto.response.FriendResponseDto;
import java.util.List;

public interface FriendService {
    void sendFriendRequest(String receiverEmail);
    void acceptFriendRequest(Long friendshipId);
    void declineFriendRequest(Long friendshipId);
    void removeFriend(Long friendUserId); // Xóa bạn bằng userId của họ
    List<FriendResponseDto> getMyFriends();
    List<FriendResponseDto> getPendingRequests();
}