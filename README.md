# WebSocket Chat Server (STOMP)

Spring Boot 3.5.10 기반 STOMP 프로토콜을 사용한 실시간 채팅 서버

## 실행 방법

```bash
# 빌드 및 실행
./gradlew bootRun

# 채팅 UI 접속
http://localhost:8080/chat.html
```

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

## 클래스 역할

### 설정 계층
- **WebSocketStompConfig**: STOMP 엔드포인트 및 메시지 브로커 설정
  - `/ws-chat` 엔드포인트 등록 (SockJS 지원)
  - 메시지 목적지 prefix 설정 (`/topic`, `/queue`, `/app`)

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

## 선택 기준

- **STOMP 방식 사용 시**: 빠른 개발, 표준 프로토콜, SockJS fallback 필요
- **WebFlux 방식 사용 시**: 높은 동시성, 커스텀 프로토콜, 완전한 비동기 파이프라인

---

**Note**: 현재 프로젝트는 Spring MVC 전용으로, `@EnableWebSocketMessageBroker`는 WebFlux 환경에서 작동하지 않습니다.
