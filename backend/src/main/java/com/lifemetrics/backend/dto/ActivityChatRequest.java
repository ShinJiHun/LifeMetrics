package com.lifemetrics.backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class ActivityChatRequest {

    private List<Message> messages;

    @Data
    public static class Message {
        private String role;
        private String content;
    }
}