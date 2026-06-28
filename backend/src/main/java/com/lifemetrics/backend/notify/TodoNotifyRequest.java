package com.lifemetrics.backend.notify;

import lombok.Data;

import java.util.List;

/**
 * 할 일 알림 요청 바디.
 */
@Data
public class TodoNotifyRequest {
    /** 설정 진입 비밀번호. */
    private String password;
    /** 사용자가 입력한 할 일 항목들. */
    private List<String> items;

    /** 공백 제거 + 빈 항목 필터링된 할 일 목록. */
    public List<String> cleanItems() {
        if (items == null) return List.of();
        return items.stream()
                .map(s -> s == null ? "" : s.trim())
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
