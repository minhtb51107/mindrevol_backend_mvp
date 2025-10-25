package com.example.demo.feed.service.impl;

import com.example.demo.feed.dto.FeedEventDto;
import com.example.demo.feed.entity.FeedEvent;
import com.example.demo.feed.entity.FeedEventType;
import com.example.demo.feed.mapper.FeedMapper;
import com.example.demo.feed.repository.FeedEventRepository;
import com.example.demo.feed.service.FeedService;
import com.example.demo.plan.entity.Plan;
import com.example.demo.plan.entity.PlanMember; // Import PlanMember
import com.example.demo.shared.exception.ResourceNotFoundException;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async; // Cho việc gửi WS bất đồng bộ
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set; // Import Set
import java.util.stream.Collectors; // Import Collectors

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FeedServiceImpl implements FeedService {

    private final FeedEventRepository feedEventRepository;
    private final FeedMapper feedMapper;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper; // Inject ObjectMapper
    private final SimpMessagingTemplate messagingTemplate; // Inject SimpMessagingTemplate

    @Override
    @Transactional(readOnly = true)
    public Page<FeedEventDto> getRecentFeedForUser(String userEmail, Pageable pageable) {
        User user = findUserByEmail(userEmail);
        // Gọi query đã tạo trong repository
        Page<FeedEvent> feedPage = feedEventRepository.findFeedForUserPlans(user.getId(), pageable);
        // Map Page<FeedEvent> sang Page<FeedEventDto>
        return feedPage.map(feedMapper::toDto);
    }

    @Override
    @Async("taskExecutor") // Chạy bất đồng bộ để không làm chậm luồng chính
    @Transactional // Vẫn cần transaction để lưu entity
    public void createAndPublishFeedEvent(FeedEventType eventType, User actor, Plan plan, Map<String, Object> detailsMap) {
        // 1. Chuyển đổi Map details thành chuỗi JSON
        String detailsJson = null;
        if (detailsMap != null && !detailsMap.isEmpty()) {
            try {
                // Thêm thông tin actorFullName và planTitle vào detailsMap nếu có
                if (actor != null && !detailsMap.containsKey("actorFullName")) {
                    detailsMap.put("actorFullName", feedMapper.getUserFullName(actor));
                }
                if (plan != null && !detailsMap.containsKey("planTitle")) {
                    detailsMap.put("planTitle", plan.getTitle());
                }
                detailsJson = objectMapper.writeValueAsString(detailsMap);
            } catch (JsonProcessingException e) {
                log.error("Error converting feed details map to JSON: {}", e.getMessage(), e);
                // Quyết định xử lý lỗi: bỏ qua details, ghi log, hay ném exception?
                // Ở đây chọn bỏ qua details nếu lỗi
            }
        }

        // 2. Tạo và lưu FeedEvent Entity
        FeedEvent feedEvent = FeedEvent.builder()
                .eventType(eventType)
                .actor(actor)
                .plan(plan)
                .details(detailsJson)
                // timestamp tự động được gán
                .build();
        FeedEvent savedEvent = feedEventRepository.save(feedEvent);
        log.info("Created FeedEvent ID: {}, Type: {}", savedEvent.getId(), eventType);

        // 3. Map sang DTO để gửi WebSocket
        FeedEventDto feedEventDto = feedMapper.toDto(savedEvent);

        // 4. Gửi WebSocket đến các user liên quan
        publishFeedEventViaWebSocket(feedEventDto, plan); // Gọi hàm helper
    }

    // --- Helper Methods ---

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + email));
    }

    // Hàm helper để gửi WebSocket
    private void publishFeedEventViaWebSocket(FeedEventDto feedEventDto, Plan plan) {
        if (feedEventDto == null) return;

        // Xác định danh sách người nhận (tất cả thành viên của plan liên quan)
        Set<Integer> recipientUserIds;
        if (plan != null && plan.getMembers() != null) {
            recipientUserIds = plan.getMembers().stream()
                                  .map(PlanMember::getUser)
                                  .filter(user -> user != null && user.getId() != null) // Lọc user null
                                  .map(User::getId)
                                  .collect(Collectors.toSet());
        } else {
            // Nếu không có plan (ví dụ: sự kiện hệ thống?), quyết định gửi cho ai?
            // Hoặc có thể chỉ gửi cho actor nếu có?
            if (feedEventDto.getActorId() != null) {
                 recipientUserIds = Set.of(feedEventDto.getActorId());
            } else {
                 log.warn("Cannot determine recipients for FeedEvent ID: {}", feedEventDto.getId());
                 return; // Không gửi nếu không xác định được người nhận
            }
        }


        // Gửi message tới topic của từng user nhận
        for (Integer userId : recipientUserIds) {
            String userTopic = "/topic/user/" + userId + "/feed";
            try {
                messagingTemplate.convertAndSend(userTopic, feedEventDto);
                log.debug("Sent feed event {} to user topic: {}", feedEventDto.getId(), userTopic);
            } catch (Exception e) {
                log.error("Error sending feed event {} to user topic {}: {}", feedEventDto.getId(), userTopic, e.getMessage());
            }
        }
    }
}