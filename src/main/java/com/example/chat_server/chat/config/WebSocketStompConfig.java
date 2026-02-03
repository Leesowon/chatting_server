package com.example.chat_server.chat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * WebSocket 채팅 연결 설정
 *
 * [주요 역할]
 * 1. 클라이언트가 /ws-chat 주소로 WebSocket 연결
 *    - ?username=Alice로 연결하면 "Alice"라는 이름으로 사용자 식별
 *    - WebSocket 안되면 다른 방법(Long Polling)으로 자동 전환
 *
 * 2. 메시지 주소 규칙 설정
 *    - /app/chat.send → 서버가 받아서 처리
 *    - /topic/chat.general → 채팅방의 모든 사람에게 전송
 *    - /user/Alice/queue/history → Alice 한 명에게만 전송
 *
 * 3. RabbitMQ를 통한 다중 서버 메시지 브로커 연동
 *    - 서버 A의 클라이언트와 서버 B의 클라이언트가 실시간 채팅 가능
 *    - RabbitMQ가 모든 서버 간 메시지 라우팅 및 구독 관리 담당
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketStompConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${spring.websocket.stomp.relay.host}")
    private String relayHost;

    @Value("${spring.websocket.stomp.relay.port}")
    private int relayPort;

    @Value("${spring.websocket.stomp.relay.client-login}")
    private String clientLogin;

    @Value("${spring.websocket.stomp.relay.client-passcode}")
    private String clientPasscode;

    @Value("${spring.websocket.stomp.relay.system-login}")
    private String systemLogin;

    @Value("${spring.websocket.stomp.relay.system-passcode}")
    private String systemPasscode;

    /**
     * WebSocket 연결 주소 설정
     * 1. 클라이언트가 /ws-chat?username=Alice로 WebSocket 연결 요청
     * 2. CustomHandshakeHandler가 username=Alice를 파싱해서 Principal에 저장
     * 3. WebSocket 연결 수립 (여기까지가 registerStompEndpoints의 역할)
     * 4. 이후 클라이언트가 /app/chat.send로 메시지 보내면 → @MessageMapping("chat.send") 컨트롤러로 라우팅 (이건 configureMessageBroker + @MessageMapping이 처리)
     *
     * 정리: registerStompEndpoints는 "문 열어주기"만 하고, 실제 메시지를 어떤 컨트롤러로 보낼지는 configureMessageBroker의 /app prefix 설정과 @MessageMapping 어노테이션이 결정합니다.
     *
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(new CustomHandshakeHandler()) // 연결할 때 username을 받아서 사용자 식별용으로 저장
                .withSockJS(); // WebSocket 안되면 자동으로 다른 방법 사용
    }

    /**
     * 메시지 브로커 설정 - RabbitMQ 기반 다중 서버 지원
     *
     * [단일 서버 vs 다중 서버]
     * - enableSimpleBroker: 서버 메모리에만 구독 정보 저장 → 같은 서버 내 클라이언트끼리만 통신 가능
     * - enableStompBrokerRelay: RabbitMQ를 통해 모든 서버가 메시지 공유 → 서버 A 클라이언트 ↔ 서버 B 클라이언트 통신 가능
     *
     * [메시지 주소 규칙]
     * - /app/chat.send: 클라이언트가 서버로 메시지 보낼 때 (→ @MessageMapping으로 처리)
     * - /topic/chat.general: 채팅방의 모든 사람에게 메시지 보낼 때
     * - /user/Alice/queue/history: Alice 한 명에게만 메시지 보낼 때
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {

        // 1. RabbitMQ 외부 브로커 연동 (다중 서버 지원)
        //    - 기존 enableSimpleBroker는 서버 메모리에만 구독 정보 저장 (단일 서버 제한)
        //    - enableStompBrokerRelay는 RabbitMQ를 통해 여러 서버 간 메시지 공유
        //    - 서버 A 클라이언트 ↔ RabbitMQ ↔ 서버 B 클라이언트 실시간 통신 가능
        registry.enableStompBrokerRelay("/topic", "/queue")
                .setRelayHost(relayHost)              // RabbitMQ 서버 주소
                .setRelayPort(relayPort)              // RabbitMQ STOMP 포트 (기본: 61613)
                .setClientLogin(clientLogin)          // 클라이언트 연결용 인증 정보
                .setClientPasscode(clientPasscode)
                .setSystemLogin(systemLogin)          // 시스템 연결용 인증 정보 (heartbeat, 관리 작업)
                .setSystemPasscode(systemPasscode)
                .setSystemHeartbeatSendInterval(20000)    // 20초마다 heartbeat 전송
                .setSystemHeartbeatReceiveInterval(20000); // 20초 내 heartbeat 수신 기대

        // 2. 애플리케이션 목적지 prefix: 클라이언트가 메시지를 보낼 목적지
        //    변경사항 없음 - /app/chat.send 등 기존 엔드포인트 그대로 작동
        registry.setApplicationDestinationPrefixes("/app");

        // 3. 사용자 목적지 prefix: 특정 사용자에게 메시지를 보낼 때 사용
        //    변경사항 없음 - RabbitMQ가 자동으로 user-specific routing 처리
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * 사용자 이름 추출기
     * - 연결 주소에서 ?username=Alice 부분을 꺼내서 저장
     * - 나중에 "Alice에게만 메시지 보내기" 할 때 이 이름 사용
     */
    private static class CustomHandshakeHandler extends DefaultHandshakeHandler {
        @Override
        protected Principal determineUser(ServerHttpRequest request,
                                         WebSocketHandler wsHandler,
                                         Map<String, Object> attributes) {
            // 쿼리 파라미터에서 username 추출
            if (request instanceof ServletServerHttpRequest) {
                ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
                String username = servletRequest.getServletRequest().getParameter("username");
                if (username != null && !username.isEmpty()) {
                    return new StompPrincipal(username);
                }
            }
            // username이 없으면 기본 principal 사용
            return super.determineUser(request, wsHandler, attributes);
        }
    }

    /**
     * 사용자 이름 저장 객체
     * - Alice, Bob 같은 이름을 저장하는 간단한 클래스
     */
    private static class StompPrincipal implements Principal {
        private final String name;

        public StompPrincipal(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
