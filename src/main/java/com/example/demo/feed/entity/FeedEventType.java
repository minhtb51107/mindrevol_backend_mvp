package com.example.demo.feed.entity;

// Enum định nghĩa các loại sự kiện có thể xuất hiện trên feed
public enum FeedEventType {
    CHECK_IN,           // Ai đó đã check-in
    STREAK_ACHIEVED,    // Ai đó đạt mốc streak mới
    JOIN_PLAN,          // Ai đó tham gia kế hoạch
    PLAN_COMPLETE,      // Kế hoạch đã hoàn thành
    COMMENT_ADDED,      // Bình luận mới (trên progress ngày)
    REACTION_ADDED,     // Reaction mới (trên progress ngày)
    TASK_COMMENT_ADDED, // Bình luận mới (trên task) - Tùy chọn, có thể gộp với COMMENT_ADDED
    // Thêm các loại sự kiện khác nếu cần
}