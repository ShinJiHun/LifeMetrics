package com.lifemetrics.backend.service;

import jakarta.annotation.PostConstruct;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 본인이 직접 작성한 PDF 문서(이력서·포트폴리오 등)를 텍스트로 추출·캐시해 페르소나 컨텍스트로 제공한다.
 * persona.docs.location(글롭 패턴)에 매칭되는 모든 PDF 를 자동 스캔하므로, PDF 추가/삭제 시
 * 코드·설정 변경 없이 해당 폴더에 파일만 두면 다음 /api/persona/refresh(또는 재기동) 때 반영된다.
 * 라벨은 파일명(확장자 제외)에서 자동 도출한다.
 * Spring 리소스 규칙을 따르므로 local 은 {@code classpath*:templates/*.pdf}(클래스패스 = src/main/resources),
 * prod 는 {@code file:/abs/dir/*.pdf}(파일시스템)처럼 지정한다.
 * 파일 부재/파싱 실패 시 해당 문서만 조용히 비활성(블로그 단독 모드로 동작).
 */
@Service
public class PersonaDocService {

    private final ResourcePatternResolver resolver;

    @Value("${persona.docs.location:classpath*:templates/*.pdf}")
    private String location;

    /** 로드된 문서들. 파일명 정렬로 순서 고정. */
    private volatile List<Doc> docs = List.of();

    public PersonaDocService(ResourceLoader resourceLoader) {
        this.resolver = (resourceLoader instanceof ResourcePatternResolver rpr)
                ? rpr
                : new PathMatchingResourcePatternResolver(resourceLoader);
    }

    @PostConstruct
    void initialLoad() {
        reload();
    }

    /** location 패턴의 모든 PDF 를 다시 스캔·로드해 캐시 갱신. 하나라도 로드되면 true. */
    public synchronized boolean reload() {
        Resource[] found;
        try {
            found = resolver.getResources(location);
        } catch (Exception e) {
            System.out.println("⚠️ [PersonaDoc] 스캔 실패(" + location + "): " + e.getMessage());
            this.docs = List.of();
            return false;
        }

        List<Resource> sorted = new ArrayList<>(List.of(found));
        sorted.sort(Comparator.comparing(r -> r.getFilename() == null ? "" : r.getFilename()));

        List<Doc> loaded = new ArrayList<>();
        for (Resource resource : sorted) {
            loaded.add(loadOne(resource));
        }
        this.docs = loaded;

        if (loaded.isEmpty()) {
            System.out.println("ℹ️ [PersonaDoc] " + location + " 매칭 PDF 없음 - 본인 문서 컨텍스트 비활성");
        }
        return isAnyLoaded();
    }

    private Doc loadOne(Resource resource) {
        String label = toLabel(resource.getFilename());
        String resolved = describe(resource);
        try (InputStream is = resource.getInputStream();
             PDDocument doc = Loader.loadPDF(is.readAllBytes())) {
            String text = new PDFTextStripper().getText(doc).trim();
            System.out.println("✅ [PersonaDoc] " + label + " 로드 완료: " + resolved
                    + " (" + text.length() + "자, " + doc.getNumberOfPages() + "p)");
            return new Doc(label, text, resolved);
        } catch (Exception e) {
            System.out.println("❌ [PersonaDoc] " + label + " PDF 파싱 실패: " + resolved + " -> " + e.getMessage());
            return new Doc(label, "", resolved);
        }
    }

    /** 파일명에서 확장자를 떼어 라벨로 사용. 예: resume.pdf → resume. */
    private static String toLabel(String filename) {
        if (filename == null || filename.isBlank()) return "문서";
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    public boolean isAnyLoaded() {
        return docs.stream().anyMatch(Doc::loaded);
    }

    /**
     * 프롬프트 주입용. 로드된 문서들을 "## {라벨} (본인이 직접 작성)\n{본문}" 블록으로 이어 붙인다.
     * 로드된 문서가 없으면 빈 문자열.
     */
    public String getCombinedText() {
        StringBuilder sb = new StringBuilder();
        for (Doc d : docs) {
            if (d.loaded()) {
                sb.append("## ").append(d.label()).append(" (본인이 직접 작성)\n")
                  .append(d.text()).append("\n\n");
            }
        }
        return sb.toString();
    }

    /** 상태 응답/로그용 문서별 적재 현황. */
    public List<Map<String, Object>> getStatusList() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Doc d : docs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", d.label());
            m.put("loaded", d.loaded());
            m.put("chars", d.text().length());
            m.put("path", d.path());
            list.add(m);
        }
        return list;
    }

    /** 로깅/상태 응답용 사람이 읽기 좋은 경로 표현. */
    private static String describe(Resource resource) {
        try {
            return resource.getURI().toString();
        } catch (Exception e) {
            return resource.getDescription();
        }
    }

    private record Doc(String label, String text, String path) {
        boolean loaded() {
            return text != null && !text.isBlank();
        }
    }
}