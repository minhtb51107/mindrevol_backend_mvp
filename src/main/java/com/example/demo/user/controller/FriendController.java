package com.example.demo.user.controller;

import com.example.demo.user.dto.request.FriendRequestDto;
import com.example.demo.user.dto.response.FriendResponseDto;
import com.example.demo.user.service.FriendService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/friends") // Dùng /api/v1/ cho nhất quán
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()") // Tất cả API này đều yêu cầu đăng nhập
public class FriendController {

    private final FriendService friendService;

    @PostMapping("/request")
    public ResponseEntity<Void> sendFriendRequest(@Valid @RequestBody FriendRequestDto requestDto) {
        friendService.sendFriendRequest(requestDto.getEmail());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/accept/{friendshipId}")
    public ResponseEntity<Void> acceptFriendRequest(@PathVariable Long friendshipId) {
        friendService.acceptFriendRequest(friendshipId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/decline/{friendshipId}") // Dùng DELETE cho rõ ràng
    public ResponseEntity<Void> declineFriendRequest(@PathVariable Long friendshipId) {
        friendService.declineFriendRequest(friendshipId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/remove/{friendUserId}")
    public ResponseEntity<Void> removeFriend(@PathVariable Long friendUserId) {
        friendService.removeFriend(friendUserId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my-friends")
    public ResponseEntity<List<FriendResponseDto>> getMyFriends() {
        return ResponseEntity.ok(friendService.getMyFriends());
    }

    @GetMapping("/pending-requests")
    public ResponseEntity<List<FriendResponseDto>> getPendingRequests() {
        return ResponseEntity.ok(friendService.getPendingRequests());
    }
}