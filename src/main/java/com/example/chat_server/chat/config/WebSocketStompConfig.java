package com.example.chat_server.chat.config;

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
 * 3. 서버 메모리에 "누가 어떤 주소를 구독 중인지" 저장 (단일 서버만 지원)
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketStompConfig implements WebSocketMessageBrokerConfigurer {

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
     * 메시지 주소 규칙 설정
     * - /app/chat.send: 클라이언트가 서버로 메시지 보낼 때 (→ @MessageMapping으로 처리)
     * - /topic/chat.general: 채팅방의 모든 사람에게 메시지 보낼 때
     * - /user/Alice/queue/history: Alice 한 명에게만 메시지 보낼 때
     *
     * enableSimpleBroker: 서버 메모리에 "누가 어떤 주소 구독 중인지" 저장 (단일 서버만 가능)
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {

        // 1. 메시지 브로커 활성화: 클라이언트가 구독할 목적지 prefix
        registry.enableSimpleBroker("/topic", "/queue");

        // 2. 애플리케이션 목적지 prefix: 클라이언트가 메시지를 보낼 목적지
        registry.setApplicationDestinationPrefixes("/app");

        // 3. 사용자 목적지 prefix: 특정 사용자에게 메시지를 보낼 때 사용
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
