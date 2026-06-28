package com.lifemetrics.backend.notify;

import lombok.Data;

/**
 * 수동 알림 전송 요청 바디.
 */
@Data
public class NotifyRequest {
    /** 전송할 메시지. 비어 있으면 기본 테스트 문구로 대체된다. */
    private String text;
}