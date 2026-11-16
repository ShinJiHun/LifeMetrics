package com.lifemetrics.backend.persona.worklog;

import java.util.List;

/**
 * 일일보고 과제를 큰 주제로 묶는 키워드 규칙. 보고서의 업무 분류 문구는 날마다 달라서(예: "모바일 회사게시판 / 온보딩")
 * 과제 하나하나를 보고 배정한다: 과제명 → 내용 → 그날 업무 분류 순으로 보고, 위에서부터 처음 맞는 주제.
 * 순서가 중요하다 — 더 구체적인 주제(홈 포틀릿, 그룹게시판)를 일반 주제(회사게시판)보다 앞에 둔다.
 */
final class WorkLogTopics {

    record Rule(String topic, List<String> keywords) {}

    static final String OTHER = "기타";

    static final List<Rule> RULES = List.of(
            new Rule("온보딩", List.of("온보딩")),
            new Rule("홈 화면", List.of("홈 >", "홈>", "홈화면", "홈 화면", "홈 단위", "포틀릿", "하단 바로가기", "HOM-")),
            new Rule("사회공헌", List.of("사회공헌")),
            new Rule("복지안내", List.of("복지안내")),
            new Rule("그룹게시판", List.of("그룹게시판", "그룹공지")),
            new Rule("회사게시판", List.of("게시판", "글쓰기", "좋아요", "댓글", "답글", "종료일", "조회자", "작성 버튼", "LNB")),
            new Rule("HSE·SAP 연동", List.of("HSE", "건강검진", "건강상담", "SAP", "RFC")),
            new Rule("로그인·인증", List.of("로그인", "로그아웃", "비밀번호", "인증", "계정 잠금", "잠금", "만료", "재발급",
                    "계정 전환", "M2X", "SmsUtil", "알림톡")),
            new Rule("개발 환경·도구", List.of("개발 환경", "로컬", "Maven", "스키마", "소스 복구", "백업", "도구", "IDE")),
            new Rule("일정·문서 관리", List.of("WBS", "PL 보고", "PL 일정", "현황", "기획서", "명세서", "결과서", "확인요청"))
    );

    private WorkLogTopics() {}

    static String classify(String task, String content, String dayCategory) {
        for (String text : new String[]{task, content, dayCategory}) {
            if (text == null || text.isBlank()) continue;
            for (Rule r : RULES) {
                for (String k : r.keywords()) {
                    if (text.contains(k)) return r.topic();
                }
            }
        }
        return OTHER;
    }

    /** 화면 정렬용: 규칙 순서, 기타는 맨 뒤. */
    static int order(String topic) {
        for (int i = 0; i < RULES.size(); i++) if (RULES.get(i).topic().equals(topic)) return i;
        return RULES.size();
    }
}
