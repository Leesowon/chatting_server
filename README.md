# WebSocket Chat Server (STOMP)

Spring Boot 3.5.10 기반 STOMP 프로토콜을 사용한 실시간 채팅 서버

**주요 기능**: RabbitMQ 기반 다중 서버 분산 아키텍처, Redis 메시지 히스토리, 사용자별 개인 메시지

## 아키텍처

### 단일 서버 vs 다중 서버

| 구분 | SimpleBroker (단일 서버) | StompBrokerRelay (다중 서버) |
|------|-------------------------|----------------------------|
| **브로커** | 서버 내장 메모리 | RabbitMQ 외부 브로커 |
| **구독 관리** | 각 서버별 독립적 | RabbitMQ 중앙 관리 |
| **확장성** | 단일 서버 제한 | 수평 확장 가능 |
| **메시지 공유** | 같은 서버 내에서만 | 모든 서버 간 공유 |
| **설정** | 별도 인프라 불필요 | RabbitMQ 필요 |

**현재 설정**: RabbitMQ StompBrokerRelay (다중 서버 지원)

### 다중 서버 메시지 흐름

```
[Server 1]                     [RabbitMQ]                    [Server 2]
Client A → /app/chat.send  →  STOMP Relay  →  /topic/chat.room  → Client B
                                    ↓
                          모든 서버로 브로드캐스트
                                    ↓
Client A ← /topic/chat.room ←  STOMP Relay  ←  Server 1
```

- 서버 1의 Client A가 메시지 전송 → RabbitMQ로 라우팅
- RabbitMQ가 모든 서버(Server 1, 2, ...)로 메시지 분산
- 서버 2의 Client B가 실시간 수신

## 실행 방법

### 방법 1: Docker Compose 사용 (권장)

**RabbitMQ + Redis를 Docker로 로컬 실행**

```bash
# 1. Docker 컨테이너 시작 (RabbitMQ + Redis)
docker-compose up -d

# 2. RabbitMQ STOMP 플러그인 활성화 (최초 1회)
docker exec -it rabbitmq-stomp rabbitmq-plugins enable rabbitmq_stomp

# 3. 컨테이너 상태 확인
docker-compose ps

# 4. 관리 UI 접속
# RabbitMQ: http://localhost:15672 (guest/guest)
# Redis: redis-cli -h localhost -p 6379
```

**포트 매핑**:
- `5672` → RabbitMQ AMQP
- `61613` → RabbitMQ STOMP
- `15672` → RabbitMQ 관리 UI
- `6379` → Redis

**컨테이너 중지/재시작**:
```bash
# 중지
docker-compose down

# 재시작 (데이터 유지)
docker-compose up -d

# 데이터까지 완전 삭제
docker-compose down -v
```

### 방법 2: 외부 RabbitMQ/Redis 사용

**1. 외부 서버 설정 (최초 1회)**

```bash
# RabbitMQ 서버에서 STOMP 플러그인 활성화
rabbitmq-plugins enable rabbitmq_stomp
rabbitmq-plugins enable rabbitmq_management

# RabbitMQ 재시작
rabbitmq-server restart
```

**2. application.properties 수정**

```properties
# localhost → 외부 서버 IP로 변경
spring.redis.host=10.11.12.13
spring.rabbitmq.host=10.11.12.13
spring.websocket.stomp.relay.host=10.11.12.13
```

### 애플리케이션 실행

**단일 서버**:
```bash
# 빌드 및 실행
./gradlew bootRun

# 채팅 UI 접속
http://localhost:8080/chat-server.html
```

**다중 서버** (수평 확장 테스트):
```bash
# 빌드
./gradlew clean build

# 서버 1 시작 (포트 8080)
./gradlew bootRun

# 서버 2 시작 (포트 8081) - 새 터미널
java -jar build/libs/chat_server-0.0.1-SNAPSHOT.jar --server.port=8081

# 서버 3 시작 (포트 8082) - 새 터미널
java -jar build/libs/chat_server-0.0.1-SNAPSHOT.jar --server.port=8082
```

### 크로스 서버 메시징 테스트

| 단계 | 동작 | 결과 |
|------|------|------|
| 1 | 브라우저 탭 1: `http://localhost:8080/chat-server.html`<br>Username: Alice, Room: general | 서버 1 연결 |
| 2 | 브라우저 탭 2: `http://localhost:8081/chat-server.html`<br>Username: Bob, Room: general | 서버 2 연결<br>Alice가 "Bob joined" 알림 수신 |
| 3 | Bob: "Hello from Server 2!" 전송 | Alice가 실시간 수신 ✅ |
| 4 | Alice: "Hi from Server 1!" 전송 | Bob이 실시간 수신 ✅ |

**성공 기준**: 서로 다른 서버에 연결된 클라이언트 간 실시간 메시지 교환

## 코드 동작 순서

### 1. 초기화 단계
```
ChatServerApplication (main)
  └─> WebSocketStompConfig 로드
       ├─> STOMP 엔드포인트 등록: /ws-chat
       └─> 메시지 브로커 설정: /topic, /queue, /app
```

### 2. 클라이언트 연결 흐름
```
[Client] chat.html
  ├─ 1. SockJS로 /ws-chat 연결
  ├─ 2. STOMP 프로토콜 활성화
  ├─ 3. 구독 설정
  │    ├─> /topic/chat.{roomId} 구독 (채팅방 메시지)
  │    └─> /user/queue/history 구독 (개인 히스토리)
  └─ 4. 입장 메시지 전송: /app/chat.join
       └─> [Server] ChatController.joinRoom() 실행
            ├─> ChatService.addUser() - Redis Set에 사용자 추가
            ├─> ChatService.getHistory() - Redis List에서 이전 대화 조회
            │    └─> RList<ChatResponse> (최근 100개 메시지)
            ├─> messagingTemplate.convertAndSendToUser() - 히스토리 개별 전송
            └─> messagingTemplate.convertAndSend() - 입장 알림 브로드캐스트
```

### 3. 메시지 전송 흐름
```
[Client] stompClient.send("/app/chat.send", ...)
  └─> [Server] ChatController.sendMessage()
       ├─> ChatService.saveMessage() - Redis List에 메시지 저장
       │    └─> RList.add(message) → 100개 초과 시 오래된 메시지 삭제
       └─> messagingTemplate.convertAndSend("/topic/chat.{roomId}", ...)
            └─> [All Clients] 구독자 전체에게 브로드캐스트
```

### 4. 퇴장 흐름
```
[Client] stompClient.send("/app/chat.leave", ...)
  └─> [Server] ChatController.leaveRoom()
       ├─> ChatService.removeUser() - Redis Set에서 사용자 제거
       └─> messagingTemplate.convertAndSend() - 퇴장 알림 브로드캐스트
```

## Redis 데이터 구조

### 메시지 히스토리 (RList)
```java
Key: "chat:history:{roomId}"
Type: List<ChatResponse>
용도: 채팅방별 최근 100개 메시지 저장
동작:
  - 저장: RList.add(message)
  - 조회: RList.readAll() → List<ChatResponse>
  - 제한: size > 100이면 가장 오래된 메시지 삭제 (FIFO)
```

### 사용자 목록 (RSet)
```java
Key: "chat:users:{roomId}"
Type: Set<String>
용도: 채팅방별 현재 접속 중인 사용자 관리
동작:
  - 입장: RSet.add(username)
  - 퇴장: RSet.remove(username)
  - 조회: RSet.size() → 접속자 수
```

## 기술 스택

| 계층 | 기술 | 역할 |
|------|------|------|
| **메시지 브로커** | RabbitMQ 3.13 (STOMP) | 다중 서버 간 메시지 라우팅 및 구독 관리 |
| **데이터 저장소** | Redis 7 (Redisson) | 메시지 히스토리, 사용자 목록 저장 |
| **WebSocket** | Spring STOMP | STOMP 프로토콜 기반 실시간 통신 |
| **프레임워크** | Spring Boot 3.5.10 (MVC) | 애플리케이션 서버 |
| **인프라** | Docker Compose | 로컬 개발 환경 (RabbitMQ + Redis) |

## 클래스 역할

### 설정 계층
- **WebSocketStompConfig**: STOMP 엔드포인트 및 RabbitMQ 브로커 설정
  - `/ws-chat` 엔드포인트 등록 (SockJS 지원)
  - RabbitMQ StompBrokerRelay 연동 (다중 서버 지원)
  - 메시지 목적지 prefix 설정 (`/topic`, `/queue`, `/app`)
  - CustomHandshakeHandler로 username 추출

- **RedissonCacheConfig**: Redis 캐시 매니저 설정 (동기 방식)

### 컨트롤러 계층
- **ChatController**: STOMP 메시지 라우팅 처리
  - `@MessageMapping` 어노테이션으로 경로 매핑
  - JSON 자동 직렬화/역직렬화 (Jackson)
  - `SimpMessagingTemplate`을 통한 메시지 브로드캐스트

### 서비스 계층
- **ChatService**: Redis 기반 데이터 관리
  - 메시지 히스토리 저장/조회 (`RList<ChatResponse>`)
  - 사용자 관리 (`RSet<String>`)
  - 동기 방식 RedissonClient 사용

### DTO 계층
- **ChatRequest**: 클라이언트 → 서버 요청 데이터
- **ChatResponse**: 서버 → 클라이언트 응답 데이터
- MessageType: JOIN, LEAVE, CHAT 메시지 타입 정의

### 프론트엔드
- **chat.html**: SockJS + STOMP.js 기반 웹 클라이언트
  - SockJS: WebSocket 미지원 브라우저 fallback
  - STOMP.js: STOMP 프로토콜 처리

## WebFlux + WebSocket 방식과의 차이

### 현재 구조 (Spring MVC + STOMP)

| 항목 | 특징 |
|------|------|
| **프레임워크** | Spring MVC (Servlet 기반) |
| **어노테이션** | `@EnableWebSocketMessageBroker` |
| **클라이언트** | RedissonClient (동기) |
| **메시지 처리** | `@MessageMapping` + `SimpMessagingTemplate` |
| **프로토콜** | STOMP over WebSocket |
| **장점** | 높은 수준의 추상화, 자동 JSON 변환, SockJS fallback |
| **단점** | WebFlux 미지원, 동기 I/O |

### WebFlux + WebSocket 방식

| 항목 | 특징 |
|------|------|
| **프레임워크** | Spring WebFlux (Reactive) |
| **핸들러** | `WebSocketHandler` 직접 구현 |
| **클라이언트** | RedissonReactiveClient (비동기) |
| **메시지 처리** | `webSocketSession.receive()` / `send()` |
| **프로토콜** | 순수 WebSocket (프로토콜 레벨 추상화 없음) |
| **장점** | 비동기 논블로킹 I/O, 높은 동시성, Reactive Streams |
| **단점** | 낮은 수준 제어 필요, JSON 수동 파싱 |

### 핵심 차이점

1. **메시지 라우팅**
   - MVC+STOMP: 프로토콜 레벨에서 자동 라우팅 (`/topic`, `/queue`)
   - WebFlux: 애플리케이션 레벨에서 수동 구현 필요

2. **Redis 통신**
   - MVC+STOMP: 동기 방식 (`RList.add()`, `RSet.add()`)
   - WebFlux: 비동기 방식 (`RListReactive.addAll().subscribe()`)

3. **메시지 직렬화**
   - MVC+STOMP: Spring이 자동으로 JSON ↔ DTO 변환
   - WebFlux: Jackson을 사용한 수동 파싱 필요

4. **스레드 모델**
   - MVC+STOMP: Thread-per-request (Servlet 컨테이너)
   - WebFlux: Event Loop (Netty)

5. **확장성**
   - MVC+STOMP: 외부 브로커(RabbitMQ, ActiveMQ) 연동 가능
   - WebFlux: Redis Pub/Sub을 직접 제어

## 설정 파일

### application.properties

```properties
# Redis 설정
spring.redis.host=10.11.12.13
spring.redis.port=6379

# RabbitMQ AMQP 연결
spring.rabbitmq.host=10.11.12.13
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest

# RabbitMQ STOMP Relay
spring.websocket.stomp.relay.host=10.11.12.13
spring.websocket.stomp.relay.port=61613
spring.websocket.stomp.relay.client-login=guest
spring.websocket.stomp.relay.client-passcode=guest
spring.websocket.stomp.relay.system-login=guest
spring.websocket.stomp.relay.system-passcode=guest
```

### build.gradle 핵심 의존성

```gradle
dependencies {
    // WebSocket + STOMP
    implementation 'org.springframework.boot:spring-boot-starter-websocket'

    // RabbitMQ 다중 서버 브로커
    implementation 'org.springframework.boot:spring-boot-starter-amqp'
    implementation 'io.projectreactor.netty:reactor-netty'

    // Redis 메시지 히스토리
    implementation 'org.redisson:redisson-spring-boot-starter:3.43.0'
}
```

## 트러블슈팅

### Docker 컨테이너 이슈

**증상**: `docker-compose up` 실행 후 컨테이너가 즉시 종료됨

**해결**:
```bash
# 컨테이너 로그 확인
docker-compose logs rabbitmq
docker-compose logs redis

# 컨테이너 재시작
docker-compose restart

# 완전 재생성
docker-compose down -v
docker-compose up -d
```

**증상**: RabbitMQ STOMP 플러그인 미활성화

**해결**:
```bash
# 플러그인 수동 활성화 (최초 1회)
docker exec -it chat-rabbitmq rabbitmq-plugins enable rabbitmq_stomp

# 활성화 확인
docker exec -it chat-rabbitmq rabbitmq-plugins list | grep stomp
# [E*] rabbitmq_stomp 출력 확인

# RabbitMQ 컨테이너 재시작
docker-compose restart rabbitmq
```

### RabbitMQ 연결 실패

**증상**: `Failed to connect to RabbitMQ` 또는 `Connection refused`

**해결 (Docker 사용 시)**:
```bash
# 1. 컨테이너 실행 확인
docker ps | grep rabbitmq

# 2. STOMP 플러그인 활성화
docker exec -it chat-rabbitmq rabbitmq-plugins enable rabbitmq_stomp

# 3. 포트 접근 확인
docker exec -it chat-rabbitmq rabbitmqctl status
```

**해결 (외부 서버 사용 시)**:
```bash
# 1. RabbitMQ 서버 실행 확인
rabbitmqctl status

# 2. STOMP 플러그인 활성화 확인
rabbitmq-plugins list | grep stomp
# [E*] rabbitmq_stomp 출력 확인

# 3. 포트 접근 확인
telnet 10.11.12.13 61613
```

### 서버 간 메시지 미전달

**증상**: 같은 서버 내에서는 메시지가 전달되지만, 다른 서버의 클라이언트는 수신 못함

**해결**:
1. RabbitMQ 관리 UI에서 연결 확인 (http://10.11.12.13:15672)
   - Connections 탭: 서버별 system connection 확인
   - Channels 탭: 활성 상태 확인

2. 모든 서버가 동일한 RabbitMQ 서버 설정 사용 확인
   ```properties
   spring.websocket.stomp.relay.host=10.11.12.13  # 모든 서버 동일해야 함
   ```

### Redis 연결 실패

**증상**: `Unable to connect to Redis`

**해결 (Docker 사용 시)**:
```bash
# 1. 컨테이너 실행 확인
docker ps | grep redis

# 2. Redis 연결 테스트
docker exec -it chat-redis redis-cli ping
# PONG 응답 확인

# 3. Redis 데이터 확인
docker exec -it chat-redis redis-cli
> KEYS chat:*
> LRANGE chat:history:general 0 -1
```

**해결 (외부 서버 사용 시)**:
```bash
# Redis 서버 실행 확인
redis-cli -h 10.11.12.13 ping
# PONG 응답 확인

# Redis 데이터 확인
redis-cli -h 10.11.12.13
> KEYS chat:*
> LRANGE chat:history:general 0 -1
```

## 선택 기준

### SimpleBroker vs StompBrokerRelay

- **SimpleBroker 사용 시**
  - 단일 서버 환경
  - 별도 메시지 브로커 인프라 불필요
  - 간단한 프로토타입 또는 소규모 서비스

- **StompBrokerRelay (현재 사용) 사용 시**
  - 다중 서버 환경 (수평 확장)
  - 서버 간 메시지 공유 필요
  - 고가용성(HA) 요구사항
  - RabbitMQ/ActiveMQ 인프라 운영 가능

### STOMP vs WebFlux

- **STOMP 방식 (현재 사용) 사용 시**: 빠른 개발, 표준 프로토콜, SockJS fallback 필요
- **WebFlux 방식 사용 시**: 높은 동시성, 커스텀 프로토콜, 완전한 비동기 파이프라인

---

**Note**:
- 현재 프로젝트는 **RabbitMQ StompBrokerRelay** 기반 다중 서버 아키텍처로 구성됨
- Spring MVC 전용으로, `@EnableWebSocketMessageBroker`는 WebFlux 환경에서 작동하지 않음
- SimpleBroker로 롤백 시 `WebSocketStompConfig.java`의 `enableStompBrokerRelay()` → `enableSimpleBroker()` 변경
