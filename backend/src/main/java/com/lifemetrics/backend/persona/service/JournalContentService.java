package com.lifemetrics.backend.persona.service;

import com.lifemetrics.backend.persona.dto.ContentRequests.*;
import com.lifemetrics.backend.persona.dto.ContentTreeResponse;
import com.lifemetrics.backend.persona.entity.JournalCategory;
import com.lifemetrics.backend.persona.entity.JournalPost;
import com.lifemetrics.backend.persona.entity.JournalSubMenu;
import com.lifemetrics.backend.persona.repository.JournalCategoryRepository;
import com.lifemetrics.backend.persona.repository.JournalPostRepository;
import com.lifemetrics.backend.persona.repository.JournalSubMenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;

/**
 * 개발자·인간 페르소나 블로그 콘텐츠(대메뉴/소메뉴/글) 관리.
 * journal 데이터소스를 쓰므로 트랜잭션 매니저를 명시한다(riding 이 @Primary).
 */
@Service
@RequiredArgsConstructor
@Transactional("journalTransactionManager")
public class JournalContentService {

    private final JournalCategoryRepository categoryRepo;
    private final JournalSubMenuRepository subRepo;
    private final JournalPostRepository postRepo;

    /** 비공개글을 나타내는 visibility 값. */
    private static final String PRIVATE = "private";

    // ── 전체 트리 조회 ───────────────────────────────
    /**
     * @param includePrivate 관리자면 true. false 면 비공개글을 아예 제외한다.
     *                       본문을 비우는 게 아니라 목록에서 빼므로, 제목조차 노출되지 않는다.
     */
    @Transactional(value = "journalTransactionManager", readOnly = true)
    public ContentTreeResponse tree(boolean includePrivate) {
        List<ContentTreeResponse.Category> categories = categoryRepo.findAll().stream()
                .sorted(Comparator.comparingInt(JournalCategory::getSortOrder))
                .map(c -> new ContentTreeResponse.Category(c.getId(), c.getPersona(), c.getName(), c.getSortOrder()))
                .toList();
        List<ContentTreeResponse.SubMenu> subs = subRepo.findAll().stream()
                .sorted(Comparator.comparingInt(JournalSubMenu::getSortOrder))
                .map(s -> new ContentTreeResponse.SubMenu(s.getId(), s.getCategoryId(), s.getName(), s.getSortOrder()))
                .toList();
        List<ContentTreeResponse.Post> posts = postRepo.findAll().stream()
                .filter(p -> includePrivate || !PRIVATE.equalsIgnoreCase(p.getVisibility()))
                .map(this::toPostDto)
                .toList();
        return new ContentTreeResponse(categories, subs, posts);
    }

    // ── 대메뉴 ───────────────────────────────────────
    public ContentTreeResponse.Category addCategory(CategoryCreate req) {
        JournalCategory c = new JournalCategory();
        c.setPersona(req.persona());
        c.setName(req.name() == null ? "" : req.name().trim());
        c.setSortOrder(categoryRepo.countByPersona(req.persona()));
        JournalCategory saved = categoryRepo.save(c);
        return new ContentTreeResponse.Category(saved.getId(), saved.getPersona(), saved.getName(), saved.getSortOrder());
    }

    public void renameCategory(Long id, NameUpdate req) {
        JournalCategory c = categoryRepo.findById(id).orElseThrow(this::notFound);
        c.setName(req.name() == null ? c.getName() : req.name().trim());
        categoryRepo.save(c);
    }

    public void deleteCategory(Long id) {
        List<Long> subIds = subRepo.findByCategoryIdOrderBySortOrderAsc(id).stream()
                .map(JournalSubMenu::getId).toList();
        if (!subIds.isEmpty()) {
            postRepo.deleteBySubIdIn(subIds);
        }
        subRepo.deleteByCategoryId(id);
        categoryRepo.deleteById(id);
    }

    // ── 소메뉴 ───────────────────────────────────────
    public ContentTreeResponse.SubMenu addSubMenu(SubCreate req) {
        JournalSubMenu s = new JournalSubMenu();
        s.setCategoryId(req.categoryId());
        s.setName(req.name() == null ? "" : req.name().trim());
        s.setSortOrder(subRepo.countByCategoryId(req.categoryId()));
        JournalSubMenu saved = subRepo.save(s);
        return new ContentTreeResponse.SubMenu(saved.getId(), saved.getCategoryId(), saved.getName(), saved.getSortOrder());
    }

    public void renameSubMenu(Long id, NameUpdate req) {
        JournalSubMenu s = subRepo.findById(id).orElseThrow(this::notFound);
        s.setName(req.name() == null ? s.getName() : req.name().trim());
        subRepo.save(s);
    }

    public void deleteSubMenu(Long id) {
        postRepo.deleteBySubId(id);
        subRepo.deleteById(id);
    }

    // ── 글 ───────────────────────────────────────────
    public ContentTreeResponse.Post addPost(PostCreate req) {
        JournalPost p = new JournalPost();
        p.setSubId(req.subId());
        p.setTitle(req.title() == null ? "" : req.title().trim());
        p.setContent(req.body());
        p.setVisibility(normalizeVisibility(req.visibility()));
        // 마이그레이션 시 원본 작성일 보존(없으면 @PrePersist 가 현재 시각 사용).
        LocalDateTime created = parseDateTime(req.createdAt());
        if (created != null) {
            p.setCreatedAt(created);
            p.setUpdatedAt(created);
        }
        return toPostDto(postRepo.save(p));
    }

    public ContentTreeResponse.Post updatePost(Long id, PostUpdate req) {
        JournalPost p = postRepo.findById(id).orElseThrow(this::notFound);
        if (req.subId() != null) p.setSubId(req.subId());
        if (req.title() != null) p.setTitle(req.title().trim());
        if (req.body() != null) p.setContent(req.body());
        if (req.visibility() != null) p.setVisibility(normalizeVisibility(req.visibility()));
        return toPostDto(postRepo.save(p));
    }

    public void deletePost(Long id) {
        postRepo.deleteById(id);
    }

    // ── 헬퍼 ─────────────────────────────────────────
    private ContentTreeResponse.Post toPostDto(JournalPost p) {
        return new ContentTreeResponse.Post(
                p.getId(),
                p.getSubId(),
                p.getTitle(),
                p.getContent(),
                p.getVisibility(),
                p.getCreatedAt() == null ? null : p.getCreatedAt().toString(),
                p.getUpdatedAt() == null ? null : p.getUpdatedAt().toString()
        );
    }

    private String normalizeVisibility(String v) {
        return "private".equals(v) ? "private" : "public";
    }

    /** ISO 문자열을 관대하게 LocalDateTime 으로 파싱. "...Z"(UTC)·밀리초·날짜만 형태 모두 허용. */
    private LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            // "2026-06-27T10:00:00.000Z" 등 오프셋 포함 → 해당 벽시계 시각 유지
            return OffsetDateTime.parse(s).toLocalDateTime();
        } catch (DateTimeParseException ignore) {
            // 다음 포맷 시도
        }
        try {
            // "2026-06-27T10:00:00" / "...000"
            return LocalDateTime.parse(s);
        } catch (DateTimeParseException ignore) {
            // 다음 포맷 시도
        }
        try {
            // "2026-06-27"
            return LocalDate.parse(s.substring(0, 10)).atStartOfDay();
        } catch (RuntimeException ignore) {
            return null;
        }
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 항목");
    }
}
