package com.example.chat_server.chat.service;

import com.example.chat_server.chat.dto.ChatResponse;
import org.redisson.api.RList;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis를 사용한 채팅 서비스
 *
 * 주요 기능:
 * - 메시지 히스토리 저장 (채팅방별)
 * - 사용자 관리 (입장/퇴장)
 * - 접속자 수 조회
 */
@Service
public class ChatService {

    @Autowired
    private RedissonClient redissonClient; // MVC용 동기 클라이언트

    /**
     * 메시지를 Redis에 저장
     * 최근 100개 메시지만 유지
     */
    public void saveMessage(String roomId, ChatResponse message) {
        String key = "chat:history:" + roomId;
        RList<ChatResponse> history = redissonClient.getList(key);
        history.add(message);

        // 최근 100개만 유지
        if (history.size() > 100) {
            history.remove(0);
        }
    }

    /**
     * 채팅방의 메시지 히스토리 조회
     */
    public List<ChatResponse> getHistory(String roomId) {
        String key = "chat:history:" + roomId;
        RList<ChatResponse> history = redissonClient.getList(key);
        return new ArrayList<>(history);
    }

    /**
     * 사용자 입장 처리
     */
    public void addUser(String roomId, String username) {
        String key = "chat:users:" + roomId;
        RSet<String> users = redissonClient.getSet(key);
        users.add(username);
    }

    /**
     * 사용자 퇴장 처리
     */
    public void removeUser(String roomId, String username) {
        String key = "chat:users:" + roomId;
        RSet<String> users = redissonClient.getSet(key);
        users.remove(username);
    }

    /**
     * 현재 접속자 수 조회
     */
    public int getUserCount(String roomId) {
        String key = "chat:users:" + roomId;
        RSet<String> users = redissonClient.getSet(key);
        return users.size();
    }
}
