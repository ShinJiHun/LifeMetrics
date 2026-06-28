package com.lifemetrics.backend.persona.dto;

/** 콘텐츠 CRUD 요청 바디 모음. */
public final class ContentRequests {

    private ContentRequests() {}

    public record CategoryCreate(String persona, String name) {}

    public record NameUpdate(String name) {}

    public record SubCreate(Long categoryId, String name) {}

    public record PostCreate(Long subId, String title, String body, String visibility, String createdAt) {}

    public record PostUpdate(Long subId, String title, String body, String visibility) {}
}
