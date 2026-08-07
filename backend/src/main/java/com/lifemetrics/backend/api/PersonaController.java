package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.PersonaChatRequest;
import com.lifemetrics.backend.dto.PersonaChatResponse;
import com.lifemetrics.backend.dto.PersonaRefreshResponse;
import com.lifemetrics.backend.persona.entity.PersonaProfile;
import com.lifemetrics.backend.persona.repository.BlogPostRepository;
import com.lifemetrics.backend.service.BlogCrawlerService;
import com.lifemetrics.backend.service.PersonaChatService;
import com.lifemetrics.backend.service.PersonaDocService;
import com.lifemetrics.backend.service.PersonaProfileService;
import com.lifemetrics.backend.service.SttService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/persona")
@RequiredArgsConstructor
public class PersonaController {

    private final BlogCrawlerService blogCrawlerService;
    private final PersonaProfileService personaProfileService;
    private final PersonaChatService personaChatService;
    private final BlogPostRepository blogPostRepository;
    private final PersonaDocService personaDocService;
    private final SttService sttService;

    /** 블로그 재크롤링 + 이력서 재로딩 + 페르소나 프로필 재생성 (수동 트리거). */
    @PostMapping("/refresh")
    public PersonaRefreshResponse refresh() {
        int crawled = blogCrawlerService.crawlAll();
        boolean docsLoaded = personaDocService.reload();
        boolean profileGenerated = personaProfileService.regenerate();
        String msg = "글 " + crawled + "개 수집"
                + (docsLoaded ? ", 본인 문서(이력서/포트폴리오) 로드됨" : ", 본인 문서 없음/미설정")
                + (profileGenerated ? ", 페르소나 프로필 재생성 완료" : ", 프로필 생성 실패(글 없음 또는 API 키 미설정)");
        return new PersonaRefreshResponse(crawled, profileGenerated, msg);
    }

    /** 블로그 페르소나와 멀티턴 대화. */
    @PostMapping("/chat")
    public PersonaChatResponse chat(@RequestBody PersonaChatRequest request) {
        String reply = personaChatService.chat(request.getMessages());
        return new PersonaChatResponse(reply);
    }

    /** 녹음된 음성을 로컬 Whisper 서버로 보내 텍스트로 변환한다. */
    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> transcribe(@RequestParam("audio") MultipartFile audio) {
        byte[] bytes;
        try {
            bytes = audio.getBytes();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "오디오 파일을 읽을 수 없습니다."));
        }

        String text = sttService.transcribe(bytes, audio.getContentType());
        if (text == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "음성 인식 서버에 연결할 수 없습니다. (로컬 Whisper 서버 미기동)"));
        }
        return ResponseEntity.ok(Map.of("text", text));
    }

    /** 현재 적재 상태 확인. */
    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> result = new HashMap<>();
        result.put("postCount", blogPostRepository.count());
        Optional<PersonaProfile> profile = personaProfileService.getLatest();
        result.put("profileGenerated", profile.isPresent());
        result.put("profileGeneratedAt", profile.map(PersonaProfile::getGeneratedAt).orElse(null));
        result.put("profilePostCount", profile.map(PersonaProfile::getPostCount).orElse(null));
        result.put("docsLoaded", personaDocService.isAnyLoaded());
        result.put("docs", personaDocService.getStatusList());
        return result;
    }
}
