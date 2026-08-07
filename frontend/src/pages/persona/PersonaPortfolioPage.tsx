import { useEffect, useRef, useState } from "react";
import type { CSSProperties } from "react";
import { PERSONA_META } from "@/lib/persona";

const accent = PERSONA_META.developer.accent; // #6366f1

// ── 데이터 ─────────────────────────────────────────

const TABS = [
    { id: "overview", label: "소개.tsx", color: "#6366F1" },
    { id: "career", label: "경력.tsx", color: "#10B981" },
    { id: "education", label: "학력.tsx", color: "#F2B84B" },
    { id: "career-detail", label: "경력기술서.tsx", color: "#EF4444" },
    { id: "projects", label: "프로젝트.tsx", color: "#38BDF8" },
    { id: "contact", label: "연락처.tsx", color: "#A78BFA" },
];

interface Commit {
    hash: string;
    period: string;
    company: string;
    tag?: string;
    role: string;
    body: string;
    current?: boolean;
}

const COMMITS: Commit[] = [
    {
        hash: "a4f0e2c",
        period: "2024.04 ~ 재직중",
        company: "㈜티엔에스소프트 (TNS Soft) 합류",
        tag: "HEAD → main",
        role: "개발팀 · 과장 · 웹개발 / STT 인프라",
        body: "스마트녹취백업 시스템 운영, gRPC·MRCP 기반 음성인식 연동, KT STT 평가 DB 구축 PM",
        current: true,
    },
    {
        hash: "7c19b83",
        period: "2023.04 ~ 2024.04 · 1년 1개월",
        company: "㈜싸이버원",
        role: "SIEM 개발팀 · 과장/팀원 · 웹개발",
        body: "SIEM 솔루션 프론트엔드 및 백엔드 개발, 유지보수",
    },
    {
        hash: "2f8ad61",
        period: "2020.11 ~ 2023.03 · 2년 5개월",
        company: "알에스솔루션즈 → KMParking 합병",
        role: "개발팀/TFT팀 · 대리 · Java API 서버 개발",
        body: "카카오T·아마노 무인정산기 연동 서버, 대구 스마트시티 미들웨어, 주차장 장애대응",
    },
    {
        hash: "e91d345",
        period: "2020.07 ~ 2020.10 · 4개월",
        company: "㈜소만사",
        role: "솔루션 개발팀 · 대리 · 프론트엔드 개발",
        body: "솔루션 프론트엔드 개발",
    },
    {
        hash: "1a0c9f2",
        period: "2018.06 ~ 2020.06 · 2년 1개월",
        company: "미디어젠 입사 — 첫 회사",
        tag: "root commit",
        role: "APP개발팀 / SDS개발팀 · 사원 · 챗봇 프론트엔드 / 음성인식 어댑터",
        body: "노랑풍선 챗봇 프론트엔드, 폭스바겐 AVN Speech Adaptor(C++) 개발",
    },
];

interface CareerProject {
    title: string;
    period?: string;
    paragraphs: string[];
}
interface CareerDetail {
    path: string;
    company: string;
    period: string;
    role: string;
    projects: CareerProject[];
    stack: string[];
}

const CAREER_DETAILS: CareerDetail[] = [
    {
        path: "~/career/tns-soft",
        company: "㈜티엔에스소프트 (TNS Soft)",
        period: "2024.04 ~ 재직중",
        role: "개발팀 · 과장",
        projects: [
            {
                title: "스마트녹취백업 시스템 운영 및 인프라 유지보수",
                period: "2024.04 ~ 현재",
                paragraphs: [
                    "상용 스마트녹취백업 시스템의 STT 연동 모듈 및 백엔드 서비스를 상시 운영합니다.",
                    "OpenShift·Podman 기반 컨테이너 인프라를 배포·모니터링하고, 일일 점검 프로세스로 장애를 예방합니다.",
                ],
            },
            {
                title: "MRCP 기반 SCU 음성인식 LLM 연동 아키텍처 설계",
                paragraphs: [
                    "KT SCU 음성인식 엔진에 LLM 응답을 연동하는 파이프라인을 설계했습니다. SIP Client → Asterisk Dialplan → UniMRCP → SCU Plugin → KT SCU 서버로 이어지는 전체 흐름을 구성했습니다.",
                    "SCU Plugin은 scu_recog_engine.c(MRCP 프로토콜) → ScuAdapterWrapper.cpp(C/C++ 브릿지) → ScuAdapter.cpp(비즈니스 로직) 3계층으로 역할을 분리했고, libcurl로 LLM 서버에 HTTP POST 요청 후 콜백으로 결과를 engine.c까지 되돌려 NLSML 응답을 생성하도록 구현했습니다.",
                ],
            },
            {
                title: "Ai-Subtitle — 실시간 AI 자막 생성 엔진 (gRPC STT 통합)",
                paragraphs: [
                    "동일한 sttservice.proto 계약을 공유하는 C++ 레퍼런스 클라이언트와 Java 프로덕션 구현을 함께 개발했습니다.",
                    "Java 쪽은 Spring Boot 백엔드 + Vue3/Electron 프론트로, 세션ID별 ConcurrentMap으로 스트림을 다중화해 여러 사용자의 실시간 자막을 동시에 처리하도록 설계했습니다. 실시간(gRPC 스트리밍) · 화자분리(REST 폴링) · C++ CLI 검증 경로까지 3가지 데이터 흐름을 각각 구현했습니다.",
                ],
            },
            {
                title: "gRPC 기반 음성 통신 안드로이드 STT 샘플 앱 개발",
                paragraphs: ["향후 KT 프로젝트의 gRPC 적용 가능성에 대비해, 안드로이드-STT 엔진 간 저지연 스트리밍 통신을 선제적으로 검증한 프로토타입을 개발했습니다."],
            },
            {
                title: "MRCP 기반 사내 STT 연동 및 Asterisk 콜센터 연동 샘플 개발",
                paragraphs: ["콜센터 솔루션 확장을 대비해 MRCP 서버와 Asterisk 텔레포니 서버를 구축하고, 사내 STT 엔진과 연동해 테스트용 안드로이드 앱까지 만들었습니다."],
            },
            {
                title: "KT 음성인식 STT 모델 평가용 음성 DB 구축 및 PM 수행",
                paragraphs: [
                    "노인·유아 음성 데이터 수집 프로젝트를 총괄했습니다. 노인회·유아 관련 기관을 직접 발굴해 협약을 주도하고, 녹음 프로세스 기획부터 일정·품질 관리, 현장 이슈 대응까지 담당했습니다.",
                    "고령층·아동 특유의 음성 패턴을 반영한 데이터셋 구축으로 STT 모델의 인식률 평가·고도화에 기여했습니다.",
                ],
            },
        ],
        stack: ["OpenShift", "Podman", "Linux", "gRPC", "MRCP", "Asterisk", "UniMRCP", "C/C++", "Java · Spring Boot", "Vue 3", "Electron", "Android", "STT", "Project Management"],
    },
    {
        path: "~/career/cyberone",
        company: "㈜싸이버원",
        period: "2023.04 ~ 2024.04 · 1년 1개월",
        role: "SIEM 개발팀 · 과장/팀원",
        projects: [
            {
                title: "SIEM 솔루션 프론트엔드 · 백엔드 개발 및 유지보수",
                paragraphs: ["보안 이벤트를 수집·분석하는 SIEM(Security Information and Event Management) 솔루션의 화면과 서버 로직을 개발하고 운영 중 발생하는 이슈를 유지보수했습니다."],
            },
        ],
        stack: ["Frontend", "Backend", "SIEM"],
    },
    {
        path: "~/career/rs-solutions",
        company: "알에스솔루션즈 → KMParking 합병",
        period: "2020.11 ~ 2023.03 · 2년 5개월",
        role: "개발팀/TFT팀 · 대리",
        projects: [
            {
                title: "카카오T 안드로이드 무인정산기 API 서버 개발",
                period: "2022.07 ~ 2023.01",
                paragraphs: ["카카오T 주차장에서 사용하는 안드로이드 무인정산기와 연동되는 API 서버를 개발했습니다. 기존 클라우드 서버 방식을 각 현장 로컬 서버 구조로 대체하기 위한 로컬 전용 API를 직접 설계했습니다."],
            },
            {
                title: "아마노 코리아 무인정산기 개발",
                period: "2021.02 ~ 2021.07",
                paragraphs: [
                    "주차회사 아마노 코리아 의뢰로 WebView 기반 모바일 웹 무인정산기 안드로이드 앱을 개발했습니다.",
                    "VAN사 연동 카드 결제 시스템, PBX 서버 기반 VOIP 연동, 영수증 출력기·할인권 리더기용 RS232C 시리얼통신 API를 구현했습니다.",
                ],
            },
            {
                title: "대구 스마트시티 주차 공유 플랫폼 미들웨어 개발",
                period: "2021.10 ~ 2022.03",
                paragraphs: ["주차장마다 관제 솔루션 업체가 제각각인 문제를 해결하기 위해, 각 현장 API와 대구시 스마트시티 공유 플랫폼 API를 연동하는 미들웨어 서버를 개발했습니다. WebSocket과 RESTful API를 함께 사용했습니다."],
            },
            {
                title: "이기종 통합 주차관제 솔루션 · 장애인 주차면 관리 시스템",
                paragraphs: ["모든 종류의 주차장비와 연동 가능한 통합 주차관제 솔루션의 백엔드·프론트엔드를 개발하고, 현장별 커스터마이징을 진행했습니다. 아마노 코리아의 장애인 주차면 관리 시스템에서는 소형 카메라 센서의 이미지 데이터를 LPR 서버로 전달하는 통신 모듈을 개발했습니다."],
            },
            {
                title: "주차 무인정산기 연동 서버 개발 및 주차장 이슈 장애대응",
                paragraphs: ["운영 중인 주차장 현장에서 발생하는 정산기·API 연동 장애를 상시 대응했습니다."],
            },
        ],
        stack: ["Java", "Spring Framework/Boot", "JQuery", "MariaDB", "RESTful API", "TCP/IP Socket", "WebSocket", "PBX", "RS232C", "Android"],
    },
    {
        path: "~/career/somansa",
        company: "㈜소만사",
        period: "2020.07 ~ 2020.10 · 4개월",
        role: "솔루션 개발팀 · 대리",
        projects: [{ title: "솔루션 프론트엔드 개발", paragraphs: ["사내 솔루션 제품의 프론트엔드 화면을 개발했습니다."] }],
        stack: ["Frontend"],
    },
    {
        path: "~/career/mediazen",
        company: "미디어젠 · 첫 회사",
        period: "2018.06 ~ 2020.06 · 2년 1개월",
        role: "APP개발팀/SDS개발팀 · 사원",
        projects: [
            {
                title: "노랑풍선 여행사 챗봇 시스템 프론트엔드 개발",
                period: "2018.07 ~ 2019.02",
                paragraphs: ["기존 상담 업무 일부를 대행하는 AI 기반 챗봇의 프론트엔드를 개발했습니다. 음성인식 솔루션 API를 연동해 STT(Speech To Text) 서비스를 제공했습니다."],
            },
            {
                title: "폭스바겐(VW) AVN Speech Adaptor 개발",
                period: "2019.04 ~ 2020.03",
                paragraphs: ["차량용 AVN 시스템에 적용되는 Speech Adaptor를 C++/Linux 환경에서 개발했습니다. 중국 음성인식 엔진사의 인식 결과를 IPC 통신으로 전달받아, 차량용 AVN에 연동된 모든 파트에 REST API로 전달하는 역할을 했습니다."],
            },
        ],
        stack: ["Java", "C++", "Spring Framework", "JQuery", "JavaScript", "HTML/CSS", "RESTful API", "IPC 통신", "Jenkins", "SVN"],
    },
];

interface Troubleshoot {
    id: string;
    title: string;
    remove: string[];
    add: string[];
}

const TROUBLESHOOTS: Troubleshoot[] = [
    {
        id: "#42",
        title: "활동 42건의 자전거가 뒤바뀌어 있었다",
        remove: [
            "activity_core의 bike_id 42건이 실제로 탄 자전거와 다르게 기록돼 있었습니다.",
            "원인: 인제스천 단계에서 FIT 타임스탬프의 월/일이 뒤바뀐 채 파싱되고 있었습니다.",
        ],
        add: [
            "백업 테이블 생성 후 UPDATE로 42건을 정정하고, 저장 필드인 bike.total_distance를 수동 재계산했습니다.",
            '"자동 계산될 것 같은 필드"는 스키마를 직접 확인하기 전까지 가정하지 않기로 했습니다.',
        ],
    },
    {
        id: "#null",
        title: "null 허용 필드에서 생성자 표현식이 깨졌다",
        remove: [
            "nullable DOUBLE 컬럼을 JPA 엔티티에 primitive int로 매핑했더니 생성자 표현식에서 예외가 났습니다.",
            "DB 스키마를 재조회해 해당 컬럼이 NULL을 허용한다는 걸 확인했습니다.",
        ],
        add: ["필드 타입을 Double로 바꿔 null을 그대로 표현했습니다.", "엔티티 작성 전 nullable 여부를 먼저 확인하는 걸 원칙으로 삼았습니다."],
    },
    {
        id: "#weather",
        title: "날씨 API가 뒤섞인 테스트 데이터를 주고 있었다",
        remove: [
            "Windy API 무료 티어 연동 후 실제 관측값과 맞지 않는 값이 계속 나왔습니다.",
            "같은 좌표·시각으로 반복 요청해보니 셔플된 테스트 데이터를 반환한다는 걸 확인했습니다.",
        ],
        add: ["API 키 없이 실제 GFS/ECMWF/기상청 데이터를 제공하는 Open-Meteo로 전환했습니다.", "무료 티어 응답을 눈으로 검증하는 단계를 체크리스트에 추가했습니다."],
    },
];

// ── 컴포넌트 ─────────────────────────────────────────

export default function PersonaPortfolioPage() {
    const rootRef = useRef<HTMLDivElement>(null);
    const sectionRefs = useRef<Record<string, HTMLElement | null>>({});
    const [activeTab, setActiveTab] = useState("overview");
    const [scrollPct, setScrollPct] = useState(0);
    const [openCompany, setOpenCompany] = useState(0);
    const [openTs, setOpenTs] = useState(0);

    // Layout.tsx의 <main overflowY:auto> 가 실제 스크롤 컨테이너이므로
    // window가 아니라 그 요소를 기준으로 스크롤 진행률/활성 탭을 계산합니다.
    useEffect(() => {
        const scrollEl = rootRef.current?.closest("main");
        if (!scrollEl) return;

        const onScroll = () => {
            const max = scrollEl.scrollHeight - scrollEl.clientHeight;
            setScrollPct(max > 0 ? Math.min(Math.max(scrollEl.scrollTop / max, 0), 1) : 0);
        };
        scrollEl.addEventListener("scroll", onScroll, { passive: true });
        onScroll();
        return () => scrollEl.removeEventListener("scroll", onScroll);
    }, []);

    useEffect(() => {
        const scrollEl = rootRef.current?.closest("main");
        if (!scrollEl) return;

        const io = new IntersectionObserver(
            (entries) => {
                entries.forEach((entry) => {
                    if (entry.isIntersecting) setActiveTab(entry.target.id);
                });
            },
            { root: scrollEl, rootMargin: "-40% 0px -55% 0px", threshold: 0 }
        );
        Object.values(sectionRefs.current).forEach((el) => el && io.observe(el));
        return () => io.disconnect();
    }, []);

    const registerSection = (id: string) => (el: HTMLElement | null) => {
        sectionRefs.current[id] = el;
    };

    return (
        <div ref={rootRef} style={S.page}>
            <style>{CSS}</style>

            {/* 섹션 이동용 스티키 탭바 (전역 크롬 아님 — SideBar가 그 역할을 담당) */}
            <div className="pp-tabstrip-wrap">
                <div className="pp-tabstrip">
                    {TABS.map((t) => (
                        <a key={t.id} href={`#${t.id}`} className={`pp-tab ${activeTab === t.id ? "active" : ""}`}>
                            <span className="pp-sq" style={{ background: t.color }} />
                            {t.label}
                        </a>
                    ))}
                </div>
                <div className="pp-scroll-indicator">
                    <div className="pp-scroll-fill" style={{ width: `${scrollPct * 100}%` }} />
                </div>
            </div>

            {/* Hero */}
            <section style={S.hero}>
                <div className="pp-eyebrow">{"// developer persona"}</div>
                <h1 style={S.headline}>
                    음성 데이터가 서버와 엔진 사이를
                    <br />
                    <span style={{ color: accent }}>끊기지 않고</span> 흐르게 만듭니다.
                </h1>
                <p style={S.subhead}>
                    웹 프론트엔드로 시작해 주차 시스템 백엔드, SIEM, 그리고 지금은 STT·gRPC·MRCP 기반 음성인식 인프라까지 — 도메인을 넓혀가며 8년 3개월째 만들고 운영하고 있습니다.
                </p>
                <div style={S.statRow}>
                    <Stat num="8" unit="년 3개월" label="총 경력" />
                    <Stat num="5" label="소속 기업" />
                    <Stat num="12" unit="+" label="참여 프로젝트" />
                    <Stat num="STT" label="gRPC · MRCP 전문분야" />
                </div>
            </section>

            {/* 01 소개 */}
            <section id="overview" ref={registerSection("overview")} className="pp-section">
                <SecHead num="01 · Overview" title="소개" />
                <div style={S.overviewGrid}>
                    <div>
                        <p style={S.p}>
                            2018년 프론트엔드 개발로 시작해서, 주차 시스템 백엔드 API 서버 개발, SIEM 솔루션 풀스택 개발을 거쳐 현재는{" "}
                            <strong>TNS Soft에서 음성인식(STT) 도메인의 백엔드와 인프라를 담당</strong>하고 있습니다. gRPC 양방향 스트리밍, MRCP 표준 프로토콜, Asterisk 텔레포니 연동처럼
                            프로토콜 레이어부터 직접 설계하는 일에 강점이 있습니다.
                        </p>
                        <p style={S.p}>
                            맡은 시스템은 끝까지 책임지는 편입니다. 상용 스마트녹취백업 시스템을 OpenShift·Podman 기반으로 상시 운영하며 장애를 미리 막는 일, KT 음성인식 모델 평가용 음성
                            DB를 노인·유아 대상 기관과 직접 협의해 구축하고 총괄한 일 모두 "만들고 끝"이 아니라 "돌아가게 만드는" 데 방점을 찍은 결과입니다.
                        </p>
                        <p style={S.p}>업무 외 시간에는 라이딩과 운동을 즐기고, 그 데이터를 직접 설계한 개인 플랫폼(LifeMetrics)으로 관리합니다. 지금 보고 있는 이 페이지도 그 일부입니다.</p>
                        <div style={{ marginTop: 22, display: "flex", flexDirection: "column", gap: 8 }}>
                            <HChip text="카카오T 주차장 연동 API 서버를 클라우드 방식에서 현장 로컬 서버 구조로 단독 전환" />
                            <HChip text="KT STT 모델 평가용 음성 DB 구축 프로젝트 PM 총괄 — 기관 협약부터 녹음 프로세스까지" />
                            <HChip text="gRPC 계약(sttservice.proto) 기준으로 C++ 레퍼런스 클라이언트와 Java 프로덕션 구현을 함께 설계" />
                        </div>
                    </div>
                    <div className="pp-code-card">
                        <div style={{ color: "#6A7290" }}>{"// whoami"}</div>
                        <div>
                            <span style={{ color: "#7C87F7" }}>const</span> <span style={{ color: "#F2B84B" }}>developer</span> = {"{"}
                        </div>
                        <div>&nbsp;&nbsp;name: <span style={{ color: "#9ADE9A" }}>"신지훈"</span>,</div>
                        <div>&nbsp;&nbsp;role: <span style={{ color: "#9ADE9A" }}>"Backend Developer"</span>,</div>
                        <div>
                            &nbsp;&nbsp;focus: [<span style={{ color: "#9ADE9A" }}>"STT"</span>, <span style={{ color: "#9ADE9A" }}>"gRPC"</span>, <span style={{ color: "#9ADE9A" }}>"MRCP"</span>],
                        </div>
                        <div>&nbsp;&nbsp;current: <span style={{ color: "#9ADE9A" }}>"TNS Soft"</span>,</div>
                        <div>&nbsp;&nbsp;since: <span style={{ color: "#9ADE9A" }}>"2024-04"</span>,</div>
                        <div>&nbsp;&nbsp;sideProject: <span style={{ color: "#9ADE9A" }}>"LifeMetrics"</span>,</div>
                        <div>{"}"};</div>
                    </div>
                </div>
            </section>

            {/* 02 경력 (git log) */}
            <section id="career" ref={registerSection("career")} className="pp-section">
                <SecHead num="02 · Career" title="경력" desc="총 8년 3개월, 5개 기업 — git log 형태로 정리했습니다." />
                <div className="pp-git-log">
                    {COMMITS.map((c) => (
                        <div key={c.hash} className={`pp-commit ${c.current ? "current" : ""}`}>
                            <div className="pp-commit-node" />
                            <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4, flexWrap: "wrap" }}>
                                <span className="pp-commit-hash">{c.hash}</span>
                                {c.tag && <span className={`pp-commit-tag ${c.current ? "" : "muted"}`}>{c.tag}</span>}
                                <span className="pp-commit-hash">{c.period}</span>
                            </div>
                            <div style={{ fontWeight: 700, fontSize: 16.5, marginBottom: 3 }}>{c.company}</div>
                            <div style={{ fontSize: 13, color: "#6B7280", marginBottom: 6 }}>{c.role}</div>
                            <div style={{ fontSize: 13, color: "#9CA1B5" }}>{c.body}</div>
                        </div>
                    ))}
                </div>
            </section>

            {/* 03 학력 */}
            <section id="education" ref={registerSection("education")} className="pp-section">
                <SecHead num="03 · Education" title="학력" />
                <div style={S.eduGrid}>
                    <EduCard period="2010 ~ 2016" school="한림대학교 · 학사" major="융합소프트웨어학과" />
                    <EduCard period="2016 ~ 2018" school="한림대학교 · 석사" major="융합소프트웨어학과 — 청취 음량 수집 애플리케이션 연구" />
                </div>
            </section>

            {/* 04 경력기술서 */}
            <section id="career-detail" ref={registerSection("career-detail")} className="pp-section">
                <SecHead num="04 · Career Detail" title="경력 기술서" desc="회사를 클릭하면 프로젝트별 요약·역할·성과와 사용 기술 스택이 펼쳐집니다." />
                <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
                    {CAREER_DETAILS.map((cd, i) => {
                        const open = openCompany === i;
                        return (
                            <div key={cd.path} className={`pp-cd-card ${open ? "open" : ""}`}>
                                <div style={{ fontFamily: "var(--pp-mono)", fontSize: 11, color: "#9CA1B5", padding: "8px 22px 0" }}>{cd.path}</div>
                                <button
                                    type="button"
                                    className="pp-cd-head"
                                    onClick={() => setOpenCompany(open ? -1 : i)}
                                    aria-expanded={open}
                                >
                                    <div style={{ display: "flex", alignItems: "baseline", gap: 14, flexWrap: "wrap" }}>
                                        <span style={{ fontWeight: 700, fontSize: 16 }}>{cd.company}</span>
                                        <span className="pp-commit-hash">{cd.period}</span>
                                        <span style={{ fontSize: 12, color: "#6B7280" }}>{cd.role}</span>
                                    </div>
                                    <span className="pp-cd-toggle">+</span>
                                </button>
                                <div className="pp-cd-body">
                                    <div className="pp-cd-body-inner">
                                        {cd.projects.map((p) => (
                                            <div key={p.title} className="pp-proj-item">
                                                <div style={{ fontWeight: 700, fontSize: 13.5, marginBottom: 6, display: "flex", gap: 10, flexWrap: "wrap", alignItems: "baseline" }}>
                                                    {p.title}
                                                    {p.period && <span className="pp-commit-hash" style={{ fontSize: 10.5 }}>{p.period}</span>}
                                                </div>
                                                {p.paragraphs.map((para, idx) => (
                                                    <p key={idx} style={{ fontSize: 13, color: "#6B7280", marginBottom: 4 }}>
                                                        {para}
                                                    </p>
                                                ))}
                                            </div>
                                        ))}
                                        <div style={{ display: "flex", flexWrap: "wrap", gap: 6, marginTop: 16 }}>
                                            {cd.stack.map((s) => (
                                                <span key={s} className="pp-tag">{s}</span>
                                            ))}
                                        </div>
                                    </div>
                                </div>
                            </div>
                        );
                    })}
                </div>
            </section>

            {/* 05 프로젝트 */}
            <section id="projects" ref={registerSection("projects")} className="pp-section">
                <SecHead num="05 · Personal Projects" title="개인 프로젝트" desc="업무 밖에서 직접 기획하고 끝까지 만든 프로젝트입니다." />

                <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 36, flexWrap: "wrap", gap: 14 }}>
                    <div>
                        <div style={{ fontWeight: 800, fontSize: 22 }}>LifeMetrics</div>
                        <div style={{ fontSize: 13, color: "#6B7280", marginTop: 6 }}>
                            라이드·체성분·코스 데이터를 하나의 파이프라인으로 묶는 개인 사이클링 데이터 플랫폼 — 지금 보고 있는 이 페이지도 이 프로젝트의 일부입니다.
                        </div>
                    </div>
                    <a className="pp-btn-ghost" href="https://github.com/ShinJiHun" target="_blank" rel="noopener noreferrer">
                        GitHub에서 보기 →
                    </a>
                </div>

                <div style={S.featGrid}>
                    <FeatCard icon="📐" title="코스-라이드 비교" desc="같은 코스를 여러 번 탄 기록을 이동시간·평균속도·상승고도·평균 파워/심박/케이던스로 비교합니다. JPQL 프로젝션으로 DTO를 직접 구성했습니다." tags={["CourseRideComparisonDto", "JPQL"]} />
                    <FeatCard icon="🚲" title="자전거 장비 관리" desc="모델명 검색(웹 검색), 스펙 이미지 업로드(비전), 제품 URL(web_fetch) 세 가지 방식으로 자전거를 등록합니다." tags={["Claude Vision", "web_fetch"]} />
                    <FeatCard icon="🧭" title="퍼소나 게이트웨이" desc="방문자가 개발/라이더/휴먼 중 원하는 페르소나를 골라 각기 다른 DB 기반 데이터로 채팅할 수 있는 라우팅 구조입니다. 지금 이 페이지가 그 결과물입니다." tags={["PersonaGate", "Gemini · Vertex AI"]} />
                </div>

                <div className="pp-arch-diagram">
                    <div style={{ fontFamily: "var(--pp-mono)", fontSize: 11, color: "#9CA1B5", marginBottom: 18 }}>Architecture · 데이터가 흐르는 경로</div>
                    <div className="pp-arch-row">
                        <ArchCol label="수집" boxes={["Garmin FIT", "Samsung Health", "InBody 사진"]} />
                        <div className="pp-arch-arrow">→</div>
                        <ArchCol label="파이프라인" boxes={[{ text: "Python / uvicorn :8001", hl: true }, "strava_segment_sync", "Claude OCR"]} />
                        <div className="pp-arch-arrow">→</div>
                        <ArchCol label="저장" boxes={[{ text: "MariaDB 11.6 riding_db", hl: true }, "journal_db 10.4"]} />
                        <div className="pp-arch-arrow">→</div>
                        <ArchCol label="서빙" boxes={[{ text: "Spring Boot JPA/Hibernate", hl: true }, "React 19 + Vite"]} />
                    </div>
                </div>

                <div style={{ display: "flex", flexDirection: "column", gap: 14, marginBottom: 54 }}>
                    {TROUBLESHOOTS.map((ts, i) => {
                        const open = openTs === i;
                        return (
                            <div key={ts.id} className={`pp-ts-card ${open ? "open" : ""}`}>
                                <button type="button" className="pp-ts-head" onClick={() => setOpenTs(open ? -1 : i)} aria-expanded={open}>
                                    <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                                        <span className="pp-commit-hash">{ts.id}</span>
                                        <span style={{ fontWeight: 700, fontSize: 14 }}>{ts.title}</span>
                                    </div>
                                    <span className="pp-cd-toggle">+</span>
                                </button>
                                <div className="pp-ts-body">
                                    <div className="pp-ts-body-inner">
                                        {ts.remove.map((line, idx) => (
                                            <div key={idx} className="pp-diff-line pp-diff-remove">
                                                <span className="pp-pfx">-</span>
                                                {line}
                                            </div>
                                        ))}
                                        {ts.add.map((line, idx) => (
                                            <div key={idx} className="pp-diff-line pp-diff-add">
                                                <span className="pp-pfx">+</span>
                                                {line}
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            </div>
                        );
                    })}
                </div>

                <div className="pp-dep-block">
                    <div style={{ color: "#6A7290" }}>{"// backend — package.json"}</div>
                    <DepLine k='"spring-boot + jpa"' c="관계형 조인이 많은 도메인, JPQL 프로젝션으로 DTO 직접 구성" />
                    <DepLine k='"mariadb@11.6 / 10.4"' c="riding_db · journal_db를 분리해 스키마 변경 영향 범위를 좁힘" />
                    <DepLine k='"python + uvicorn"' c="FIT 파싱은 파이썬 생태계가 압도적으로 풍부" />
                    <div style={{ color: "#6A7290", marginTop: 14 }}>{"// ai-integration"}</div>
                    <DepLine k='"claude-api"' c="InBody OCR, 자전거 스펙 추출 등 비정형 입력 처리" />
                    <DepLine k='"gemini + vertex-ai(adc)"' c="개인 문서 RAG — AI Studio 키 대신 ADC로 학습 데이터 사용 방지" />
                    <div style={{ color: "#6A7290", marginTop: 14 }}>{"// infra"}</div>
                    <DepLine k='"gcp-vm + docker + nginx"' c="단일 VM 컨테이너 운영, deploy.sh로 배포 표준화" />
                    <DepLine k='"open-meteo"' c="Windy 무료 티어가 셔플 데이터 반환 확인 후 교체" />
                </div>

                <div className="pp-mini-proj-card" style={{ marginTop: 34 }}>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", flexWrap: "wrap", gap: 8, marginBottom: 10 }}>
                        <div style={{ fontWeight: 700, fontSize: 15 }}>청취 음량 수집 애플리케이션</div>
                        <div className="pp-commit-hash">대학원 논문 주제 · 2016 ~ 2018</div>
                    </div>
                    <p style={{ fontSize: 13, color: "#6B7280", marginBottom: 12 }}>
                        안드로이드 스마트폰에 이어폰을 연결해 음악을 청취하는 동안의 청취 음압(dBA)을 측정하는 앱을 개발했습니다. 시간별·일별·주별·월별 평균 청취 음량을 집계해 청력
                        손상 위험을 스스로 확인할 수 있게 했습니다.
                    </p>
                    <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                        {["Java", "Tomcat", "MySQL", "JQuery"].map((s) => (
                            <span key={s} className="pp-tag">{s}</span>
                        ))}
                    </div>
                </div>
            </section>

            {/* 06 연락처 */}
            <section id="contact" ref={registerSection("contact")} style={{ padding: "60px 0 40px", borderTop: "1px solid #ECEEF6" }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", flexWrap: "wrap", gap: 20 }}>
                    <div>
                        <div style={{ fontWeight: 800, fontSize: 20, marginBottom: 8 }}>연락 주세요</div>
                        <p style={{ color: "#6B7280", fontSize: 13.5, maxWidth: 360 }}>음성인식 백엔드, 데이터 파이프라인, 인프라 운영에 관심 있는 팀이라면 언제든 편하게 연락 주세요.</p>
                    </div>
                    <div style={{ display: "flex", gap: 20, flexWrap: "wrap" }}>
                        <a className="pp-foot-link" href="tel:010-XXXX-XXXX">010-XXXX-XXXX</a>
                        <a className="pp-foot-link" href="https://github.com/ShinJiHun" target="_blank" rel="noopener noreferrer">GitHub ↗</a>
                        <a className="pp-foot-link" href="https://tho881.tistory.com/" target="_blank" rel="noopener noreferrer">블로그 ↗</a>
                    </div>
                </div>
                <div style={{ marginTop: 40, fontFamily: "var(--pp-mono)", fontSize: 11, color: "#9CA1B5" }}>신지훈 · Backend Developer · STT / gRPC / MRCP</div>
            </section>
        </div>
    );
}

// ── 서브 컴포넌트 ─────────────────────────────────────────

function Stat({ num, unit, label }: { num: string; unit?: string; label: string }) {
    return (
        <div className="pp-stat">
            <div style={{ fontFamily: "var(--pp-mono)", fontSize: 20, fontWeight: 700 }}>
                {num} {unit && <span style={{ fontSize: 12, color: "#9CA1B5", fontWeight: 400 }}>{unit}</span>}
            </div>
            <div style={{ fontSize: 11, color: "#9CA1B5", marginTop: 5 }}>{label}</div>
        </div>
    );
}

function SecHead({ num, title, desc }: { num: string; title: string; desc?: string }) {
    return (
        <div style={{ marginBottom: 46, maxWidth: 620 }}>
            <div style={{ fontFamily: "var(--pp-mono)", fontSize: 12, color: accent, marginBottom: 12 }}>{num}</div>
            <h2 style={{ fontWeight: 800, fontSize: 28, marginBottom: 10, letterSpacing: "-0.01em" }}>{title}</h2>
            {desc && <p style={{ color: "#6B7280", fontSize: 14.5 }}>{desc}</p>}
        </div>
    );
}

function HChip({ text }: { text: string }) {
    return (
        <div style={{ border: "1px solid #E3E6F0", background: "#fff", borderRadius: 6, padding: "11px 14px", fontSize: 13, color: "#6B7280", display: "flex", gap: 9 }}>
            <span style={{ color: "#10B981", fontFamily: "var(--pp-mono)", fontWeight: 700, flexShrink: 0 }}>+</span>
            {text}
        </div>
    );
}

function EduCard({ period, school, major }: { period: string; school: string; major: string }) {
    return (
        <div style={{ background: "#fff", border: "1px solid #E3E6F0", borderRadius: 8, padding: 22 }}>
            <div style={{ fontFamily: "var(--pp-mono)", fontSize: 11.5, color: accent, marginBottom: 8 }}>{period}</div>
            <div style={{ fontWeight: 700, fontSize: 16, marginBottom: 4 }}>{school}</div>
            <div style={{ fontSize: 13, color: "#6B7280" }}>{major}</div>
        </div>
    );
}

function FeatCard({ icon, title, desc, tags }: { icon: string; title: string; desc: string; tags: string[] }) {
    return (
        <div className="pp-feat-card">
            <div className="pp-feat-icon">{icon}</div>
            <h4 style={{ fontWeight: 700, fontSize: 15, marginBottom: 7 }}>{title}</h4>
            <p style={{ color: "#6B7280", fontSize: 12.5, marginBottom: 12 }}>{desc}</p>
            <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                {tags.map((t) => (
                    <span key={t} className="pp-tag">{t}</span>
                ))}
            </div>
        </div>
    );
}

type ArchBox = string | { text: string; hl?: boolean };
function ArchCol({ label, boxes }: { label: string; boxes: ArchBox[] }) {
    return (
        <div style={{ display: "flex", flexDirection: "column", gap: 8, flex: 1 }}>
            <div style={{ fontFamily: "var(--pp-mono)", fontSize: 10, color: "#9CA1B5", textAlign: "center", marginBottom: 2 }}>{label}</div>
            {boxes.map((b, i) => {
                const isObj = typeof b !== "string";
                const text = isObj ? (b as { text: string }).text : b;
                const hl = isObj && (b as { hl?: boolean }).hl;
                return (
                    <div key={i} className={`pp-arch-box ${hl ? "hl" : ""}`}>
                        {text}
                    </div>
                );
            })}
        </div>
    );
}

function DepLine({ k, c }: { k: string; c: string }) {
    return (
        <div>
            <span style={{ color: "#9ADE9A" }}>{k}</span>: <span style={{ color: "#6A7290" }}>{`// ${c}`}</span>
        </div>
    );
}

// ── 스타일 ─────────────────────────────────────────

const S: Record<string, CSSProperties> = {
    page: { maxWidth: 1000, margin: "0 auto", fontFamily: "var(--pp-body, inherit)" },
    hero: { padding: "48px 0 70px" },
    headline: { fontWeight: 800, fontSize: "clamp(28px, 4vw, 42px)", lineHeight: 1.3, letterSpacing: "-0.01em", maxWidth: 720, marginBottom: 18 },
    subhead: { fontSize: 16, color: "#6B7280", maxWidth: 580, marginBottom: 34, lineHeight: 1.65 },
    statRow: { display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 1, background: "#E3E6F0", border: "1px solid #E3E6F0", borderRadius: 8, overflow: "hidden", maxWidth: 700 },
    overviewGrid: { display: "grid", gridTemplateColumns: "1.15fr 0.85fr", gap: 40, alignItems: "start" },
    p: { color: "#6B7280", fontSize: 15, marginBottom: 14, lineHeight: 1.7 },
    eduGrid: { display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 },
    featGrid: { display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 16, marginBottom: 46 },
};

// hover/accordion/sticky처럼 인라인 style로 표현하기 번거로운 부분만 CSS로 (PersonaGate.tsx 패턴과 동일)
const CSS = `
  .pp-tabstrip-wrap{ position: sticky; top: 0; z-index: 20; background: #fff; margin: 0 -24px 0; padding: 0 24px; border-bottom: 1px solid #ECEEF6; }
  .pp-tabstrip{ display:flex; overflow-x:auto; }
  .pp-tab{ font-family: var(--pp-mono); font-size: 12.5px; color:#9CA1B5; padding: 11px 16px; display:flex; align-items:center; gap:7px; white-space:nowrap; text-decoration:none; position:relative; }
  .pp-tab:hover{ color:#1B2236; }
  .pp-tab.active{ color:#1B2236; font-weight:600; }
  .pp-tab.active::after{ content:''; position:absolute; bottom:-1px; left:16px; right:16px; height:2px; background:${accent}; }
  .pp-sq{ width:8px; height:8px; border-radius:2px; }
  .pp-scroll-indicator{ height:2px; background:#ECEEF6; }
  .pp-scroll-fill{ height:100%; background:${accent}; transition: width .1s linear; }

  .pp-section{ padding: 70px 0; border-top: 1px solid #ECEEF6; }
  .pp-eyebrow{ font-family: var(--pp-mono); font-size:13px; color:${accent}; margin-bottom:20px; }

  .pp-stat{ background:#fff; padding:16px 18px; }

  .pp-code-card{ background:#1E2130; border-radius:8px; padding:20px 22px; font-family: var(--pp-mono); font-size:12.5px; color:#B4B9D6; overflow-x:auto; box-shadow: 0 8px 24px rgba(27,34,54,0.10); line-height: 1.9; }

  .pp-git-log{ position:relative; padding-left:30px; }
  .pp-git-log::before{ content:''; position:absolute; left:5px; top:6px; bottom:6px; width:2px; background:#E3E6F0; }
  .pp-commit{ position:relative; padding-bottom:38px; }
  .pp-commit:last-child{ padding-bottom:0; }
  .pp-commit-node{ position:absolute; left:-30px; top:4px; width:12px; height:12px; border-radius:50%; background:#fff; border:2.5px solid #9CA1B5; }
  .pp-commit.current .pp-commit-node{ background:${accent}; border-color:${accent}; box-shadow: 0 0 0 4px #EEF0FE; }
  .pp-commit-hash{ font-family: var(--pp-mono); font-size:11.5px; color:#9CA1B5; }
  .pp-commit-tag{ font-family: var(--pp-mono); font-size:10px; background:#EEF0FE; color:${accent}; padding:2px 8px; border-radius:10px; font-weight:600; }
  .pp-commit-tag.muted{ background:#F0F1F8; color:#9CA1B5; }

  .pp-cd-card{ background:#fff; border:1px solid #E3E6F0; border-radius:8px; overflow:hidden; }
  .pp-cd-head{ width:100%; padding:12px 22px 18px; display:flex; align-items:center; justify-content:space-between; cursor:pointer; gap:16px; background:none; border:none; font-family:inherit; text-align:left; }
  .pp-cd-toggle{ font-family: var(--pp-mono); color:#9CA1B5; font-size:17px; transition: transform .2s; flex-shrink:0; }
  .pp-cd-card.open .pp-cd-toggle{ transform: rotate(45deg); color:${accent}; }
  .pp-cd-body{ max-height:0; overflow:hidden; transition: max-height .35s ease; }
  .pp-cd-card.open .pp-cd-body{ max-height: 3400px; }
  .pp-cd-body-inner{ padding:6px 22px 22px; border-top:1px solid #ECEEF6; }
  .pp-proj-item{ padding-top:18px; border-bottom:1px solid #ECEEF6; padding-bottom:18px; }
  .pp-proj-item:last-of-type{ border-bottom:none; }

  .pp-tag{ font-family: var(--pp-mono); font-size:10.5px; color:#6B7280; border:1px solid #E3E6F0; padding:3px 9px; border-radius:12px; background:#F0F1F8; }

  .pp-feat-card{ background:#fff; border:1px solid #E3E6F0; border-radius:8px; padding:22px; transition: border-color .2s, transform .2s; }
  .pp-feat-card:hover{ border-color:${accent}; transform: translateY(-3px); box-shadow: 0 8px 20px rgba(99,102,241,0.08); }
  .pp-feat-icon{ width:32px; height:32px; border-radius:7px; background:#EEF0FE; display:flex; align-items:center; justify-content:center; margin-bottom:14px; }

  .pp-arch-diagram{ background:#fff; border:1px solid #E3E6F0; border-radius:8px; padding:30px 24px; overflow-x:auto; margin-bottom:54px; }
  .pp-arch-row{ display:flex; align-items:stretch; justify-content:space-between; gap:12px; min-width:600px; }
  .pp-arch-box{ border:1px solid #E3E6F0; background:#F0F1F8; border-radius:6px; padding:10px 12px; font-family: var(--pp-mono); font-size:11px; color:#6B7280; text-align:center; }
  .pp-arch-box.hl{ border-color:${accent}; background:#EEF0FE; color:#4F46E5; font-weight:600; }
  .pp-arch-arrow{ display:flex; align-items:center; justify-content:center; color:#9CA1B5; font-size:15px; }

  .pp-ts-card{ background:#fff; border:1px solid #E3E6F0; border-radius:8px; overflow:hidden; }
  .pp-ts-head{ width:100%; padding:16px 20px; display:flex; align-items:center; justify-content:space-between; cursor:pointer; gap:16px; background:none; border:none; font-family:inherit; text-align:left; }
  .pp-ts-body{ max-height:0; overflow:hidden; transition: max-height .3s ease; }
  .pp-ts-card.open .pp-ts-body{ max-height:500px; }
  .pp-ts-body-inner{ padding:4px 0 14px; border-top:1px solid #ECEEF6; }
  .pp-diff-line{ font-family: var(--pp-mono); font-size:12.5px; padding:9px 20px; display:flex; gap:10px; }
  .pp-pfx{ flex-shrink:0; font-weight:700; }
  .pp-diff-remove{ background: rgba(239,68,68,0.07); color:#7A2020; border-left:3px solid #EF4444; }
  .pp-diff-remove .pp-pfx{ color:#EF4444; }
  .pp-diff-add{ background: rgba(16,185,129,0.08); color:#12563E; border-left:3px solid #10B981; }
  .pp-diff-add .pp-pfx{ color:#10B981; }

  .pp-dep-block{ background:#1E2130; border-radius:8px; padding:24px 26px; overflow-x:auto; font-family: var(--pp-mono); font-size:12.5px; line-height:2; color:#B4B9D6; }

  .pp-mini-proj-card{ background:#fff; border:1px solid #E3E6F0; border-radius:8px; padding:22px; }

  .pp-btn-ghost{ font-family: var(--pp-mono); font-size:13px; padding:11px 20px; border-radius:7px; border:1px solid #E3E6F0; color:#1B2236; background:#fff; text-decoration:none; transition: all .15s; }
  .pp-btn-ghost:hover{ border-color:#9CA1B5; transform: translateY(-1px); }

  .pp-foot-link{ font-family: var(--pp-mono); font-size:12.5px; color:#6B7280; text-decoration:none; border-bottom:1px solid transparent; padding-bottom:2px; transition: color .15s, border-color .15s; }
  .pp-foot-link:hover{ color:${accent}; border-color:${accent}; }

  :root{ --pp-mono: 'JetBrains Mono', monospace; }

  @media (max-width: 860px){
    .pp-featGrid{ grid-template-columns: 1fr; }
  }
`;