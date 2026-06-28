package com.lifemetrics.backend.persona.dto;

import java.util.List;

/** 프론트 ContentDB 와 동일한 형태(필드명 order/body)로 내려준다. */
public record ContentTreeResponse(
        List<Category> categories,
        List<SubMenu> subMenus,
        List<Post> posts
) {
    public record Category(Long id, String persona, String name, int order) {}

    public record SubMenu(Long id, Long categoryId, String name, int order) {}

    public record Post(
            Long id,
            Long subId,
            String title,
            String body,
            String visibility,
            String createdAt,
            String updatedAt
    ) {}
}
