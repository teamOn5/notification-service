package com.teamon5.livepick.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Tag(name = "Notification Service API", description = "알림 마이크로서비스 및 MySQL / Redis / MongoDB Atlas 연동 테스트 API")
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired(required = false)
    private MongoTemplate mongoTemplate;

    // ==========================================
    // 1. 기본 서비스 상태 및 유레카 연동 확인
    // ==========================================
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

    // ==========================================
    // 2. MySQL (notification_db) 목업 테스트
    // ==========================================
    @Operation(summary = "[MySQL] notifications 테이블 목업 목록 조회", description = "DB/미들웨어 EC2의 notification_db.notifications 테이블에 적재된 목업 알림 데이터를 조회합니다.")
    @GetMapping("/db/list")
    public Map<String, Object> getNotificationMockList() {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("service", "notification-service");
        response.put("database", "MySQL 8.0 (DB EC2: 172.31.39.86:3306/notification_db)");
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

    @Operation(summary = "[MySQL] 사용자별 알림 조회", description = "특정 사용자 ID의 알림 목록을 MySQL에서 조회합니다.")
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

    // ==========================================
    // 3. Redis (미들웨어 서버 6379) 테스트
    // ==========================================
    @Operation(summary = "[Redis] 핑 테스트 (연결 확인)", description = "미들웨어 EC2의 Redis 서버(172.31.39.86:6379)로 PING 명령을 보내 PONG 응답을 확인합니다.")
    @GetMapping("/redis/ping")
    public Map<String, Object> redisPing() {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "notification-service");
        response.put("target", "Redis 7 (DB EC2: 172.31.39.86:6379)");
        try {
            if (redisTemplate != null && redisTemplate.getConnectionFactory() != null) {
                RedisConnection conn = redisTemplate.getConnectionFactory().getConnection();
                String pingResult = conn.ping();
                conn.close();
                response.put("code", 200);
                response.put("ping", pingResult);
                response.put("status", "SUCCESS");
            } else {
                response.put("status", "REDIS_TEMPLATE_NOT_CONFIGURED");
            }
        } catch (Exception e) {
            response.put("code", 500);
            response.put("status", "FAIL");
            response.put("error", e.getMessage());
        }
        return response;
    }

    @Operation(summary = "[Redis] Key-Value 저장 (TTL 지정 가능)", description = "Redis에 키-값 쌍을 저장하고 만료 시간(초)을 설정합니다.")
    @PostMapping("/redis/set")
    public Map<String, Object> redisSet(
        @Parameter(description = "Redis 키 이름", example = "user:1:notification_count") @RequestParam String key,
        @Parameter(description = "저장할 값", example = "5") @RequestParam String value,
        @Parameter(description = "만료 시간(초), 기본 300초", example = "300") @RequestParam(defaultValue = "300") long ttlSeconds
    ) {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "notification-service");
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
                response.put("code", 200);
                response.put("status", "SUCCESS");
                response.put("key", key);
                response.put("value", value);
                response.put("ttlSeconds", ttlSeconds);
            } else {
                response.put("status", "REDIS_TEMPLATE_NOT_CONFIGURED");
            }
        } catch (Exception e) {
            response.put("code", 500);
            response.put("status", "FAIL");
            response.put("error", e.getMessage());
        }
        return response;
    }

    @Operation(summary = "[Redis] Key 조회", description = "Redis에 저장된 키의 값을 조회합니다.")
    @GetMapping("/redis/get")
    public Map<String, Object> redisGet(
        @Parameter(description = "조회할 키 이름", example = "user:1:notification_count") @RequestParam String key
    ) {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "notification-service");
        try {
            if (redisTemplate != null) {
                String value = redisTemplate.opsForValue().get(key);
                Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                response.put("code", 200);
                response.put("status", "SUCCESS");
                response.put("key", key);
                response.put("value", value);
                response.put("remainingTtlSeconds", expire);
            } else {
                response.put("status", "REDIS_TEMPLATE_NOT_CONFIGURED");
            }
        } catch (Exception e) {
            response.put("code", 500);
            response.put("status", "FAIL");
            response.put("error", e.getMessage());
        }
        return response;
    }

    // ==========================================
    // 4. MongoDB Atlas (live_stream_db) 테스트
    // ==========================================
    @Operation(summary = "[MongoDB Atlas] 핑 테스트 (클라우드 연결 확인)", description = "MongoDB Atlas 클러스터(cluster0.61vehwi.mongodb.net)로 ping 커맨드를 전송하여 연결을 확인합니다.")
    @GetMapping("/mongo/ping")
    public Map<String, Object> mongoPing() {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "notification-service");
        response.put("target", "MongoDB Atlas (cluster0.61vehwi.mongodb.net/live_stream_db)");
        try {
            if (mongoTemplate != null) {
                Document pingDoc = mongoTemplate.executeCommand(new Document("ping", 1));
                response.put("code", 200);
                response.put("status", "SUCCESS");
                response.put("pingResponse", pingDoc);
            } else {
                response.put("status", "MONGO_TEMPLATE_NOT_CONFIGURED");
            }
        } catch (Exception e) {
            response.put("code", 500);
            response.put("status", "FAIL");
            response.put("error", e.getMessage());
        }
        return response;
    }

    @Operation(summary = "[MongoDB Atlas] 채팅/알림 로그 목록 조회", description = "MongoDB Atlas의 live_stream_db.live_chat_logs 컬렉션에 저장된 Document 목록을 조회합니다.")
    @GetMapping("/mongo/logs")
    public Map<String, Object> getMongoLogs() {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("service", "notification-service");
        response.put("target", "MongoDB Atlas (collection: live_chat_logs)");
        try {
            if (mongoTemplate != null) {
                List<Map> logs = mongoTemplate.findAll(Map.class, "live_chat_logs");
                response.put("data", logs);
                response.put("count", logs.size());
                response.put("status", "SUCCESS");
            } else {
                response.put("status", "MONGO_TEMPLATE_NOT_CONFIGURED");
            }
        } catch (Exception e) {
            response.put("code", 500);
            response.put("status", "FAIL");
            response.put("error", e.getMessage());
        }
        return response;
    }

    @Operation(summary = "[MongoDB Atlas] 새 로그 Document 생성", description = "MongoDB Atlas의 live_chat_logs 컬렉션에 새 Document를 저장합니다.")
    @PostMapping("/mongo/logs")
    public Map<String, Object> createMongoLog(
        @Parameter(description = "방 ID", example = "room_101") @RequestParam String roomId,
        @Parameter(description = "사용자 ID", example = "1") @RequestParam Long userId,
        @Parameter(description = "닉네임", example = "집밥러버") @RequestParam String username,
        @Parameter(description = "메시지", example = "레시피 정말 유용하네요!") @RequestParam String message
    ) {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "notification-service");
        try {
            if (mongoTemplate != null) {
                Map<String, Object> doc = new HashMap<>();
                doc.put("roomId", roomId);
                doc.put("userId", userId);
                doc.put("username", username);
                doc.put("message", message);
                doc.put("timestamp", new Date());

                Map<String, Object> saved = mongoTemplate.insert(doc, "live_chat_logs");
                response.put("code", 200);
                response.put("status", "SUCCESS");
                response.put("savedDocument", saved);
            } else {
                response.put("status", "MONGO_TEMPLATE_NOT_CONFIGURED");
            }
        } catch (Exception e) {
            response.put("code", 500);
            response.put("status", "FAIL");
            response.put("error", e.getMessage());
        }
        return response;
    }
}
