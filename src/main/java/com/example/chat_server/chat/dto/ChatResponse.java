package com.example.chat_server.chat.dto;

/**
 * STOMP 채팅 메시지 응답 DTO
 *
 * 서버가 클라이언트로 브로드캐스트하는 메시지 형식:
 * {
 *   "sender": "Alice",
 *   "message": "Hello world!",
 *   "roomId": "general",
 *   "timestamp": 1234567890,
 *   "type": "CHAT"
 * }
 */
public class ChatResponse {

    private String sender;
    private String message;
    private String roomId;
    private long timestamp;
    private MessageType type;

    public enum MessageType {
        JOIN,   // 채팅방 입장 알림
        LEAVE,  // 채팅방 퇴장 알림
        CHAT    // 일반 채팅 메시지
    }

    // 기본 생성자 (Jackson JSON 직렬화용)
    public ChatResponse() {
    }

    public ChatResponse(String sender, String message, String roomId, MessageType type) {
        this.sender = sender;
        this.message = message;
        this.roomId = roomId;
        this.timestamp = System.currentTimeMillis();
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

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }
}
