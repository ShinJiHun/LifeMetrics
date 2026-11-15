package com.lifemetrics.backend.dto;

import java.util.List;

/** 애슐리트 챗 요청. sessionId 가 있으면 그 대화에 이어서 저장하고, 없으면 새 대화를 만든다. */
public record AthleteChatRequest(Long sessionId, List<PersonaChatRequest.Message> messages) {}
