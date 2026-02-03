package com.example.chat_server.chat.controller;

import com.example.chat_server.chat.dto.ChatRequest;
import com.example.chat_server.chat.dto.ChatResponse;
import com.example.chat_server.chat.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * STOMP 채팅 컨트롤러
 *
 * 메시지 매핑:
 * - /app/chat.send   : 채팅 메시지 전송
 * - /app/chat.join   : 채팅방 입장
 * - /app/chat.leave  : 채팅방 퇴장
 *
 * 브로드캐스트 목적지:
 * - /topic/chat.{roomId} : 채팅방별 메시지 브로드캐스트
 * - /user/queue/history  : 입장한 사용자에게만 히스토리 전송
 */
@Controller
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * 채팅 메시지 전송
     *
     * 클라이언트 요청:
     * stompClient.send("/app/chat.send", {}, JSON.stringify({
     *   sender: "Alice",
     *   message: "Hello!",
     *   roomId: "general",
     *   type: "CHAT"
     * }));
     *
     * 응답: /topic/chat.general 구독자 전체에게 브로드캐스트
     */
    @MessageMapping("chat.send")
    public void sendMessage(ChatRequest request) {
        ChatResponse response = new ChatResponse();
        response.setSender(request.getSender());
        response.setMessage(request.getMessage());
        response.setRoomId(request.getRoomId());
        response.setTimestamp(System.currentTimeMillis());
        response.setType(ChatResponse.MessageType.CHAT);

        // Redis에 메시지 저장
        chatService.saveMessage(request.getRoomId(), response);

        // 채팅방 구독자들에게 브로드캐스트
        messagingTemplate.convertAndSend(
            "/topic/chat." + request.getRoomId(),
            response
        );
    }

    /**
     * 채팅방 입장
     *
     * 클라이언트 요청:
     * stompClient.send("/app/chat.join", {}, JSON.stringify({
     *   sender: "Alice",
     *   roomId: "general",
     *   type: "JOIN"
     * }));
     *
     * 응답:
     * 1. 입장한 사용자에게 히스토리 전송 (/user/queue/history)
     * 2. 채팅방 전체에 입장 알림 브로드캐스트 (/topic/chat.general)
     */
    @MessageMapping("chat.join")
    public void joinRoom(ChatRequest request) {
        // 사용자 입장 처리
        chatService.addUser(request.getRoomId(), request.getSender());

        // 1. 히스토리 전송 (입장한 사용자에게만)
        List<ChatResponse> history = chatService.getHistory(request.getRoomId());
        for (ChatResponse msg : history) {
            messagingTemplate.convertAndSendToUser(
                request.getSender(),
                "/queue/history",
                msg
            );
        }

        // 2. 입장 알림 메시지 생성
        ChatResponse response = new ChatResponse();
        response.setSender("System");
        response.setMessage(request.getSender() + " joined the chat");
        response.setRoomId(request.getRoomId());
        response.setTimestamp(System.currentTimeMillis());
        response.setType(ChatResponse.MessageType.JOIN);

        // 3. 채팅방 전체에 브로드캐스트
        messagingTemplate.convertAndSend(
            "/topic/chat." + request.getRoomId(),
            response
        );
    }

    /**
     * 채팅방 퇴장
     *
     * 클라이언트 요청:
     * stompClient.send("/app/chat.leave", {}, JSON.stringify({
     *   sender: "Alice",
     *   roomId: "general",
     *   type: "LEAVE"
     * }));
     *
     * 응답: /topic/chat.general 구독자 전체에게 퇴장 알림 브로드캐스트
     */
    @MessageMapping("chat.leave")
    public void leaveRoom(ChatRequest request) {
        // 사용자 퇴장 처리
        chatService.removeUser(request.getRoomId(), request.getSender());

        // 퇴장 알림 메시지 생성
        ChatResponse response = new ChatResponse();
        response.setSender("System");
        response.setMessage(request.getSender() + " left the chat");
        response.setRoomId(request.getRoomId());
        response.setTimestamp(System.currentTimeMillis());
        response.setType(ChatResponse.MessageType.LEAVE);

        // 채팅방 전체에 브로드캐스트
        messagingTemplate.convertAndSend(
            "/topic/chat." + request.getRoomId(),
            response
        );
    }
}
