package com.example.chat_server.chat.dto;

/**
 * STOMP 채팅 메시지 요청 DTO
 *
 * 클라이언트가 서버로 전송하는 메시지 형식:
 * {
 *   "sender": "Alice",
 *   "message": "Hello world!",
 *   "roomId": "general",
 *   "type": "CHAT"
 * }
 */
public class ChatRequest {

    private String sender;      // 메시지 보낸 사람
    private String message;     // 메시지 내용
    private String roomId;      // 채팅방 ID
    private MessageType type;   // 메시지 타입

    public enum MessageType {
        JOIN,   // 채팅방 입장
        LEAVE,  // 채팅방 퇴장
        CHAT    // 일반 채팅 메시지
    }

    // 기본 생성자 (Jackson JSON 역직렬화용)
    public ChatRequest() {
    }

    public ChatRequest(String sender, String message, String roomId, MessageType type) {
        this.sender = sender;
        this.message = message;
        this.roomId = roomId;
        this.type = type;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }
}
