# TeamOn5 마이크로서비스 표준 템플릿 (Notification Service)

> **이 레포지토리는 teamOn5의 모든 마이크로서비스가 복제(Replicate)하여 사용할 수 있도록 검증된 [공통 CI/CD & Eureka & Gateway 표준 템플릿]입니다.**

---

## 1. 아키텍처 및 트래픽 흐름

```
[사용자 브라우저]
       │
       ▼
[Vercel 프론트엔드] (https://onfrontend.vercel.app/)
       │
       │ (/api/notifications/* 프록시)
       ▼
[AWS ALB 로드밸런서] (포트 80)
       │
       ▼
[Spring Cloud Gateway] (EC2: 8080)
       │
       ├── 1. Eureka에서 위치 조회 ──▶ [Eureka Server] (EC2: 8761)
       │                                └── NOTIFICATION-SERVICE (포트 8084) 확인
       ▼ 2. 로드밸런싱 라우팅
[Notification Service] (EC2: 8084 컨테이너)
       │
       ▼ (AWS VPC 내부 사설망 통신)
[MySQL / Redis / Kafka] (DB EC2: 172.31.39.86)
```

---

## 2. 새 마이크로서비스 만들 때 복제 가이드 (팀원 복사 방법)

새로운 마이크로서비스(예: `order-service`, `chat-service`, `recipe-service` 등)를 만들 때는 이 레포지토리 코드를 기반으로 **딱 4곳만 본인 서비스명으로 수정**하면 즉시 자동 배포 파이프라인이 완성됩니다.

### Step 1. 레포지토리 복제 / 생성
1. GitHub Organization([teamOn5](https://github.com/teamOn5))에 새 레포지토리를 생성합니다. (Public)
2. 이 `notification-service` 프로젝트 코드를 복사하여 새 레포지토리에 붙여넣습니다.

---

### Step 2. `settings.gradle` 프로젝트 이름 변경
```groovy
rootProject.name = 'order-service'  # <-- 본인 서비스 이름으로 변경
```

---

### Step 3. `src/main/resources/application.yaml` 수정
```yaml
server:
  port: 8080

spring:
  application:
    name: order-service  # <-- 1) Eureka에 등록될 본인 서비스 이름

  # DB가 필요한 서비스는 스키마 이름 변경 (user_db, order_db, notification_db 등)
  datasource:
    url: jdbc:mysql://172.31.39.86:3306/order_db?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul
    username: user
    password: "1234"
    driver-class-name: com.mysql.cj.jdbc.Driver

# Eureka 클라이언트 등록 (그대로 유지)
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_CLIENT_SERVICEURL_DEFAULTZONE:http://172.31.29.243:8761/eureka/}
    register-with-eureka: true
    fetch-registry: true
  instance:
    prefer-ip-address: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      show-details: always
```

---

### Step 4. `.github/workflows/deploy-prod.yml` 환경변수 변경
```yaml
env:
  SERVICE_NAME: order-service  # <-- 본인 서비스 이름으로 변경!
```

---

### Step 5. 인프라 및 게이트웨이 등록 (1회성 작업)

새 서비스가 서버에 올라갈 수 있도록 **인프라와 게이트웨이 레포에 각 1줄씩만 추가**합니다:

1. **[`teamOn5/infra`](https://github.com/teamOn5/infra) 레포의 `backend-server/docker-compose.apps.yml`**
   ```yaml
     # [Service N: Order Service]
     order-service:
       image: ${DOCKER_USERNAME}/order-service:${IMAGE_TAG:-latest}
       container_name: app-order-service
       restart: always
       ports:
         - "8085:8080"  # <-- 안 겹치는 새 외부 포트 번호 지정
       environment:
         JAVA_TOOL_OPTIONS: "-Xms128m -Xmx256m -XX:+UseG1GC"
         SPRING_DATASOURCE_URL: jdbc:mysql://172.31.39.86:3306/order_db?useSSL=false&allowPublicKeyRetrieval=true
         SPRING_DATASOURCE_USERNAME: user
         SPRING_DATASOURCE_PASSWORD: "1234"
         SPRING_KAFKA_BOOTSTRAP_SERVERS: 172.31.39.86:9092
         EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://172.31.29.243:8761/eureka/
       deploy:
         resources:
           limits:
             memory: 384M
   ```

2. **[`teamOn5/gateway`](https://github.com/teamOn5/gateway) 레포의 `src/main/resources/application.yml`**
   ```yaml
           # Order Service 라우팅 추가
           - id: order-service-route
             uri: lb://ORDER-SERVICE
             predicates:
               - Path=/api/orders/**
   ```

---

## 3. 기술 스택 및 환경 정보

| 분류 | 기술 / 사양 | 비고 |
| :--- | :--- | :--- |
| **Language & Framework** | Java 17, Spring Boot, Spring Cloud Eureka Client | 경량 JRE 17 Alpine 베이스 |
| **공통 모듈 연동** | `com.teamon5:common-module:0.0.1` | GitHub Packages 자동 인증 연동 |
| **배포 환경** | AWS EC2 (t3.medium - Ubuntu 24.04), Docker Compose | 컨테이너 메모리 384MB 제한 |
| **디스커버리 & 게이트웨이** | Netflix Eureka Server, Spring Cloud Gateway | 무중단 자동 헬스체크 및 로드밸런싱 |
| **CI/CD 파이프라인** | GitHub Actions ➔ Docker Hub ➔ EC2 SSH 원격 배포 | `main` 브랜치 푸시 시 자동 실행 |

---

## 4. 검증 및 테스트 엔드포인트

- **프론트엔드 연동 확인:** `https://onfrontend.vercel.app/api/notifications/test`
- **ALB 로드밸런서 확인:** `http://app-alb-1347922990.ap-northeast-2.elb.amazonaws.com/api/notifications/test`
- **서비스 자체 헬스체크:** `http://15.164.172.146:8084/api/notifications/health`
- **Eureka 대시보드 상태:** `http://15.164.172.146:8761/`

---

## 5. 팀원 주의사항 (FAQ)

1. **GitHub Packages 권한 에러가 날 때 (`common-module` 다운로드 실패):**  
   조직 Secrets에 `GH_PACKAGES_TOKEN` (Personal Access Token, `read:packages` 권한)이 등록되어 있어야 합니다. (현재 조직 레벨에 공유되어 있으므로 자동으로 상속됩니다).
2. **배포 후 바로 호출 시 503이 뜰 때:**  
   스프링 부트가 켜지고 Eureka 서버에 등록된 후 게이트웨이가 인지하기까지 약 15~30초가 소요됩니다. 잠시 후 새로고침하시면 정상 응답이 반환됩니다.
3. **메모리(RAM) 준수:**  
   한 대의 EC2(`t3.medium`, 4GB)에서 8~10개의 마이크로서비스가 공존하므로 JVM 옵션(`-Xms128m -Xmx256m`)과 컨테이너 메모리 제한(384MB)을 반드시 유지해 주세요.
