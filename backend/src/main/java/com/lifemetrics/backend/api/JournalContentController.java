package com.lifemetrics.backend.api;

import com.lifemetrics.backend.persona.dto.ContentRequests.*;
import com.lifemetrics.backend.persona.dto.ContentTreeResponse;
import com.lifemetrics.backend.persona.service.JournalContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 개발자·인간 페르소나 블로그 콘텐츠(대메뉴/소메뉴/글) CRUD.
 * 프론트 contentStore 가 이 엔드포인트를 사용한다.
 */
@RestController
@RequestMapping("/api/content")
@RequiredArgsConstructor
public class JournalContentController {

    private final JournalContentService service;

    @GetMapping
    public ContentTreeResponse tree() {
        return service.tree();
    }

    // ── 대메뉴 ──
    @PostMapping("/categories")
    public ContentTreeResponse.Category addCategory(@RequestBody CategoryCreate req) {
        return service.addCategory(req);
    }

    @PatchMapping("/categories/{id}")
    public void renameCategory(@PathVariable Long id, @RequestBody NameUpdate req) {
        service.renameCategory(id, req);
    }

    @DeleteMapping("/categories/{id}")
    public void deleteCategory(@PathVariable Long id) {
        service.deleteCategory(id);
    }

    // ── 소메뉴 ──
    @PostMapping("/subs")
    public ContentTreeResponse.SubMenu addSubMenu(@RequestBody SubCreate req) {
        return service.addSubMenu(req);
    }

    @PatchMapping("/subs/{id}")
    public void renameSubMenu(@PathVariable Long id, @RequestBody NameUpdate req) {
        service.renameSubMenu(id, req);
    }

    @DeleteMapping("/subs/{id}")
    public void deleteSubMenu(@PathVariable Long id) {
        service.deleteSubMenu(id);
    }

    // ── 글 ──
    @PostMapping("/posts")
    public ContentTreeResponse.Post addPost(@RequestBody PostCreate req) {
        return service.addPost(req);
    }

    @PatchMapping("/posts/{id}")
    public ContentTreeResponse.Post updatePost(@PathVariable Long id, @RequestBody PostUpdate req) {
        return service.updatePost(id, req);
    }

    @DeleteMapping("/posts/{id}")
    public void deletePost(@PathVariable Long id) {
        service.deletePost(id);
    }
}
