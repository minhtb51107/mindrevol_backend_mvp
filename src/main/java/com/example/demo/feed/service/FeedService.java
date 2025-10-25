package com.example.demo.feed.service;

import com.example.demo.feed.dto.FeedEventDto;
import com.example.demo.feed.entity.FeedEventType;
import com.example.demo.plan.entity.Plan;
import com.example.demo.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public interface FeedService {

    /**
     * Lấy danh sách FeedEvent gần đây cho người dùng (từ các plan họ tham gia).
     * @param userEmail Email của người dùng hiện tại.
     * @param pageable Thông tin phân trang.
     * @return Page chứa các FeedEventDto.
     */
    Page<FeedEventDto> getRecentFeedForUser(String userEmail, Pageable pageable);

    /**
     * Tạo và lưu một FeedEvent mới, đồng thời gửi qua WebSocket.
     * @param eventType Loại sự kiện.
     * @param actor Người thực hiện (có thể null).
     * @param plan Kế hoạch liên quan (có thể null).
     * @param detailsMap Map chứa thông tin chi tiết (sẽ được chuyển thành JSON).
     */
    void createAndPublishFeedEvent(FeedEventType eventType, User actor, Plan plan, Map<String, Object> detailsMap);

}