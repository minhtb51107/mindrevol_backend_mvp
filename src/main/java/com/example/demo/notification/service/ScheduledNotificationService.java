package com.example.demo.notification.service;

public interface ScheduledNotificationService {
    /**
     * Kiểm tra và gửi thông báo nhắc nhở check-in cho ngày hôm qua.
     */
    void sendCheckinReminders();

	void sendEncouragementNotifications();

    /**
     * (Tùy chọn) Gửi thông báo động viên/nhắc nhở khi có thành viên chậm tiến độ.
     */
    // void sendEncouragementNotifications();
}