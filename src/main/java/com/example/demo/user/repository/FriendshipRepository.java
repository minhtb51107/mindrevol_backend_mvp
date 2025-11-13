package com.example.demo.user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.user.entity.Friendship;
import com.example.demo.user.entity.FriendshipStatus;
import com.example.demo.user.entity.User;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    // Kiểm tra xem đã có quan hệ chưa (để tránh spam request)
    boolean existsByRequesterAndReceiver(User requester, User receiver);

    // Lấy danh sách lời mời ĐANG CHỜ XỬ LÝ của user hiện tại (để hiển thị tab "Lời mời")
    List<Friendship> findByReceiverAndStatus(User receiver, FriendshipStatus status);

    // Lấy danh sách bạn bè (Cần query cả 2 chiều: mình gửi HOẶC người ta gửi mà đã ACCEPTED)
    @Query("SELECT f FROM Friendship f WHERE (f.requester = :user OR f.receiver = :user) AND f.status = 'ACCEPTED'")
    List<Friendship> findAllAcceptedFriendships(@Param("user") User user);
}