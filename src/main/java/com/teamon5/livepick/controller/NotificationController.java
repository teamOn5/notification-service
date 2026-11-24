package com.teamon5.livepick.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Notification Service API", description = "알림 마이크로서비스 및 미들웨어 DB 테스트용 API")
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Operation(summary = "알림 서비스 헬스체크", description = "알림 서비스 가동 상태 확인")
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "notification-service");
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    @Operation(summary = "알림 서비스 연동 테스트", description = "게이트웨이 및 유레카 연동 확인")
    @GetMapping("/test")
    public Map<String, Object> test() {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("service", "notification-service");
        response.put("message", "Hello! notification-service is working via Gateway & Eureka!");
        return response;
    }

    @Operation(summary = "미들웨어 DB notifications 테이블 목업 목록 조회", description = "DB/미들웨어 EC2의 notification_db.notifications 테이블에 적재된 목업 알림 데이터를 조회합니다.")
    @GetMapping("/db/list")
    public Map<String, Object> getNotificationMockList() {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("service", "notification-service");
        response.put("table", "notifications (DB EC2: 172.31.39.86:3306/notification_db)");
        if (jdbcTemplate != null) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, user_id, title, message, notification_type, is_read, created_at FROM notifications ORDER BY id ASC"
            );
            response.put("data", rows);
            response.put("count", rows.size());
            response.put("status", "SUCCESS");
        } else {
            response.put("status", "JDBC_TEMPLATE_NOT_CONFIGURED");
        }
        return response;
    }

    @Operation(summary = "사용자별 알림 조회", description = "특정 사용자 ID의 알림 목록을 DB에서 조회합니다.")
    @GetMapping("/db/user/{userId}")
    public Map<String, Object> getUserNotifications(@PathVariable Long userId) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("service", "notification-service");
        response.put("userId", userId);
        if (jdbcTemplate != null) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, user_id, title, message, notification_type, is_read, created_at FROM notifications WHERE user_id = ? ORDER BY id ASC",
                userId
            );
            response.put("data", rows);
            response.put("count", rows.size());
            response.put("status", "SUCCESS");
        } else {
            response.put("status", "JDBC_TEMPLATE_NOT_CONFIGURED");
        }
        return response;
    }
}
