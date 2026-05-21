package com.lifemetrics.backend.service;

import jakarta.annotation.PostConstruct;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * 이력서 PDF 텍스트를 추출·캐시해 페르소나 컨텍스트로 제공한다.
 * 경로는 프로파일별 application-{profile}.yml 의 persona.resume.path 로 주입한다.
 * 파일 부재/파싱 실패 시 조용히 비어 있는 상태로 동작(블로그 단독 모드).
 */
@Service
public class ResumeService {

    @Value("${persona.resume.path:}")
    private String resumePath;

    private volatile String cachedText = "";
    private volatile String resolvedAbsolutePath = "";

    @PostConstruct
    void initialLoad() {
        reload();
    }

    /** 캐시된 이력서 텍스트(없으면 빈 문자열). */
    public String getResumeText() {
        return cachedText;
    }

    public boolean isLoaded() {
        return cachedText != null && !cachedText.isBlank();
    }

    public String getResolvedPath() {
        return resolvedAbsolutePath;
    }

    /** PDF 를 다시 읽어 캐시 갱신. 성공 시 true. */
    public synchronized boolean reload() {
        if (resumePath == null || resumePath.isBlank()) {
            System.out.println("ℹ️ [ResumeService] persona.resume.path 미설정 - 이력서 컨텍스트 비활성");
            cachedText = "";
            return false;
        }
        File file = new File(resumePath);
        resolvedAbsolutePath = file.getAbsolutePath();
        if (!file.exists() || !file.isFile()) {
            System.out.println("⚠️ [ResumeService] 이력서 PDF 없음: " + resolvedAbsolutePath
                    + " (파일을 두면 다음 /api/persona/refresh 시 반영됨)");
            cachedText = "";
            return false;
        }
        try (PDDocument doc = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc).trim();
            cachedText = text;
            System.out.println("✅ [ResumeService] 이력서 로드 완료: " + resolvedAbsolutePath
                    + " (" + text.length() + "자, " + doc.getNumberOfPages() + "p)");
            return true;
        } catch (Exception e) {
            System.out.println("❌ [ResumeService] PDF 파싱 실패: " + resolvedAbsolutePath + " -> " + e.getMessage());
            cachedText = "";
            return false;
        }
    }
}
