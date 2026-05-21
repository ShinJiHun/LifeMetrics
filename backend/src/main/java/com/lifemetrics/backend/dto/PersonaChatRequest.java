package com.lifemetrics.backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class PersonaChatRequest {

    private List<Message> messages;

    @Data
    public static class Message {
        private String role;
        private String content;
    }
}