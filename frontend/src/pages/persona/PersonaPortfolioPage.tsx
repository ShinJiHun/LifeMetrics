import { useEffect, useMemo, useState } from "react";
import type { CSSProperties } from "react";
import { Link, useParams } from "react-router-dom";
import { PERSONA_META } from "@/lib/persona";
import { fetchProfile } from "@/api/profile";
import type { BasicProfile } from "@/api/profile";
import AdminOnly from "@/components/common/AdminOnly";

const accent = PERSONA_META.developer.accent; // #6366f1

// ── 데이터 (DB 연동: 경력/학력/소개/연락처는 /api/profile, 아래 하드코딩은 정적 UI뿐) ──

const VALID_SECTIONS = ["overview", "career", "education", "career-detail", "projects", "contact"] as const;
type Section = (typeof VALID_SECTIONS)[number];

function normalizeSection(raw: string | undefined): Section {
    return (VALID_SECTIONS as readonly string[]).includes(raw ?? "") ? (raw as Section) : "overview";
}

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
    const { section: sectionParam } = useParams<{ section?: string }>();
    const section = normalizeSection(sectionParam);

    const [selectedDomain, setSelectedDomain] = useState<string | null>(null);
    const [openTs, setOpenTs] = useState(0);
    const [profile, setProfile] = useState<BasicProfile | null>(null);

    useEffect(() => {
        fetchProfile().then(setProfile).catch(() => {});
    }, []);

    // 경력 도메인(예: 음성인식, 보안(SIEM)) — 등장 순서를 그대로 좌측 메뉴 순서로 사용
    const domains = useMemo(() => {
        if (!profile) return [];
        const seen = new Set<string>();
        const list: string[] = [];
        profile.career.forEach((c) => {
            const d = c.domain?.trim() || "기타";
            if (!seen.has(d)) {
                seen.add(d);
                list.push(d);
            }
        });
        return list;
    }, [profile]);

    const companiesInDomain = useMemo(() => {
        if (!profile || !selectedDomain) return [];
        return profile.career.filter((c) => (c.domain?.trim() || "기타") === selectedDomain);
    }, [profile, selectedDomain]);

    // 최초 로딩 시 첫 도메인을 기본 선택
    useEffect(() => {
        if (!profile || domains.length === 0) return;
        if (selectedDomain && domains.includes(selectedDomain)) return;
        setSelectedDomain(domains[0]);
    }, [profile, domains, selectedDomain]);

    const selectDomain = (d: string) => {
        setSelectedDomain(d);
    };

    if (!profile) {
        return <div style={S.page}>불러오는 중...</div>;
    }

    return (
        <div style={S.page}>
            <style>{CSS}</style>

            {/* 좌측 글로벌 메뉴(SideBar)가 섹션 이동을 담당 — 여기는 선택된 섹션만 렌더링 */}
            <div style={S.topBar}>
                <div className="pp-eyebrow">{"// developer persona"}</div>
                <AdminOnly>
                    <Link to="/persona/developer/manage" className="pp-btn-ghost" style={{ fontSize: 11.5, padding: "6px 12px" }}>
                        ✏️ 프로필 관리
                    </Link>
                </AdminOnly>
            </div>

            {section === "overview" && (
                <>
                    {/* Hero */}
                    <section style={S.hero}>
                        <div style={S.badgeRow}>
                            <span className={`pp-badge ${profile.stats.employed ? "on" : "muted"}`}>
                                {profile.stats.employed
                                    ? `재직 중${profile.stats.currentCompany ? ` · ${profile.stats.currentCompany}` : ""}`
                                    : "현재 재직 중 아님"}
                            </span>
                            <span className={`pp-badge ${profile.intro.openToWork ? "seek" : "muted"}`}>
                                {profile.intro.openToWork ? profile.intro.availability || "이직 준비 중" : "이직 계획 없음"}
                            </span>
                        </div>
                        <h1 style={S.headline}>{renderHeadline(profile.intro.headline)}</h1>
                        <p style={S.subhead}>{profile.intro.subheadline}</p>
                        <div style={S.statRow}>
                            <Stat
                                num={String(Math.floor(profile.stats.totalCareerMonths / 12))}
                                unit={profile.stats.totalCareerMonths % 12 > 0 ? `년 ${profile.stats.totalCareerMonths % 12}개월` : "년"}
                                label="총 경력"
                            />
                            <Stat num={String(profile.stats.companyCount)} label="소속 기업" />
                            <Stat num={String(profile.stats.projectCount)} label="참여 프로젝트" />
                            <Stat num={profile.intro.focusTags[0] ?? ""} label="전문분야" />
                        </div>
                    </section>

                    {/* 01 소개 */}
                    <section className="pp-section">
                        <SecHead num="01 · Overview" title="소개" />
                        <div style={S.overviewGrid}>
                            <div>
                                {profile.intro.sections.flatMap((sec) =>
                                    sec.lines.map((line, i) => (
                                        <p key={`${sec.id}-${i}`} style={S.p}>
                                            {line}
                                        </p>
                                    ))
                                )}
                                <div style={{ marginTop: 22, display: "flex", flexDirection: "column", gap: 8 }}>
                                    {profile.intro.highlights.map((h) => (
                                        <HChip key={h} text={h} />
                                    ))}
                                </div>
                            </div>
                            <div className="pp-code-card">
                                <div style={{ color: "#6A7290" }}>{"// whoami"}</div>
                                <div>
                                    <span style={{ color: "#7C87F7" }}>const</span> <span style={{ color: "#F2B84B" }}>developer</span> = {"{"}
                                </div>
                                <div>&nbsp;&nbsp;name: <span style={{ color: "#9ADE9A" }}>"신지훈"</span>,</div>
                                <div>&nbsp;&nbsp;role: <span style={{ color: "#9ADE9A" }}>"{profile.intro.roleTagline}"</span>,</div>
                                <div>
                                    &nbsp;&nbsp;focus: [
                                    {profile.intro.focusTags.map((tag, i) => (
                                        <span key={tag}>
                                            {i > 0 && ", "}
                                            <span style={{ color: "#9ADE9A" }}>"{tag}"</span>
                                        </span>
                                    ))}
                                    ],
                                </div>
                                <div>&nbsp;&nbsp;employed: <span style={{ color: "#F2B84B" }}>{String(profile.stats.employed)}</span>,</div>
                                {profile.stats.employed && (
                                    <>
                                        <div>&nbsp;&nbsp;company: <span style={{ color: "#9ADE9A" }}>"{profile.stats.currentCompany}"</span>,</div>
                                        {profile.stats.currentSince && (
                                            <div>&nbsp;&nbsp;since: <span style={{ color: "#9ADE9A" }}>"{profile.stats.currentSince}"</span>,</div>
                                        )}
                                    </>
                                )}
                                <div>&nbsp;&nbsp;openToWork: <span style={{ color: "#F2B84B" }}>{String(profile.intro.openToWork)}</span>,</div>
                                <div>&nbsp;&nbsp;sideProject: <span style={{ color: "#9ADE9A" }}>"{profile.intro.sideProject}"</span>,</div>
                                <div>{"}"};</div>
                            </div>
                        </div>
                    </section>
                </>
            )}

            {/* 02 경력 (git log) */}
            {section === "career" && (
                <section className="pp-section">
                    <SecHead
                        num="02 · Career"
                        title="경력"
                        desc={`총 ${profile.stats.totalCareerLabel}, ${profile.stats.companyCount}개 기업 — git log 형태로 정리했습니다.`}
                    />
                    <div className="pp-git-log">
                        {profile.career.map((c) => {
                            const body =
                                c.projects.length > 0
                                    ? c.projects[0].title + (c.projects.length > 1 ? ` 외 ${c.projects.length - 1}건` : "")
                                    : c.role;
                            return (
                                <div key={c.id} className={`pp-commit ${c.isCurrent ? "current" : ""}`}>
                                    <div className="pp-commit-node" />
                                    <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4, flexWrap: "wrap" }}>
                                        {c.commitHash && <span className="pp-commit-hash">{c.commitHash}</span>}
                                        {c.commitTag && <span className={`pp-commit-tag ${c.isCurrent ? "" : "muted"}`}>{c.commitTag}</span>}
                                        <span className="pp-commit-hash">{c.periodLabel}</span>
                                    </div>
                                    <div style={{ fontWeight: 700, fontSize: 16.5, marginBottom: 3 }}>{c.companyName}</div>
                                    <div style={{ fontSize: 13, color: "#6B7280", marginBottom: 6 }}>{c.role}</div>
                                    <div style={{ fontSize: 13, color: "#9CA1B5" }}>{body}</div>
                                </div>
                            );
                        })}
                    </div>
                </section>
            )}

            {/* 03 학력 */}
            {section === "education" && (
                <section className="pp-section">
                    <SecHead num="03 · Education" title="학력" />
                    <div style={S.eduGrid}>
                        {profile.education.map((e) => (
                            <EduCard key={e.id} period={e.periodLabel} school={e.school} major={e.major} />
                        ))}
                    </div>
                </section>
            )}

            {/* 04 경력기술서 */}
            {section === "career-detail" && (
                <section className="pp-section">
                    <SecHead num="04 · Career Detail" title="경력 기술서" desc="좌측에서 도메인을 고르면 그 도메인을 거쳐온 회사가 나열됩니다. 회사를 클릭하면 프로젝트별 개요·기간·업무와 화면을 상세 페이지에서 확인할 수 있습니다." />
                    <div className="pp-cd-layout">
                        <nav className="pp-cd-nav" aria-label="경력 도메인">
                            {domains.map((d) => {
                                const count = profile.career.filter((c) => (c.domain?.trim() || "기타") === d).length;
                                const active = selectedDomain === d;
                                return (
                                    <button
                                        key={d}
                                        type="button"
                                        className={`pp-cd-nav-item ${active ? "active" : ""}`}
                                        onClick={() => selectDomain(d)}
                                        aria-current={active}
                                    >
                                        <span className="pp-cd-nav-dot" />
                                        <span className="pp-cd-nav-label">{d}</span>
                                        <span className="pp-cd-nav-count">{count}</span>
                                    </button>
                                );
                            })}
                        </nav>
                        <div className="pp-cd-content">
                            {companiesInDomain.map((cd) => (
                                <Link key={cd.path} to={`/persona/developer/career-detail/${cd.id}/1`} className="pp-cd-card-btn">
                                    <div style={{ fontFamily: "var(--pp-mono)", fontSize: 11, color: "#9CA1B5", marginBottom: 6 }}>{cd.path}</div>
                                    <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 16 }}>
                                        <div style={{ display: "flex", alignItems: "baseline", gap: 14, flexWrap: "wrap" }}>
                                            <span style={{ fontWeight: 700, fontSize: 16 }}>{cd.companyName}</span>
                                            <span className="pp-commit-hash">{cd.periodLabel}</span>
                                            <span style={{ fontSize: 12, color: "#6B7280" }}>{cd.role}</span>
                                        </div>
                                        <span className="pp-cd-arrow">→</span>
                                    </div>
                                    <div style={{ fontSize: 12, color: "#9CA1B5", marginTop: 8 }}>
                                        프로젝트 {cd.projects.length}건 보기
                                    </div>
                                </Link>
                            ))}
                        </div>
                    </div>
                </section>
            )}

            {/* 05 프로젝트 */}
            {section === "projects" && (
                <section className="pp-section">
                    <SecHead num="05 · Personal Projects" title="개인 프로젝트" desc="업무 밖에서 직접 기획하고 끝까지 만든 프로젝트입니다." />

                    <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 36, flexWrap: "wrap", gap: 14 }}>
                        <div>
                            <div style={{ fontWeight: 800, fontSize: 22 }}>LifeMetrics</div>
                            <div style={{ fontSize: 13, color: "#6B7280", marginTop: 6 }}>
                                라이드·체성분·코스 데이터를 하나의 파이프라인으로 묶는 개인 사이클링 데이터 플랫폼 — 지금 보고 있는 이 페이지도 이 프로젝트의 일부입니다.
                            </div>
                        </div>
                        <a className="pp-btn-ghost" href={profile.contact.github || "https://github.com/ShinJiHun"} target="_blank" rel="noopener noreferrer">
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
            )}

            {/* 06 연락처 */}
            {section === "contact" && (
                <section className="pp-section" style={{ padding: "12px 0 40px" }}>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", flexWrap: "wrap", gap: 20 }}>
                        <div>
                            <div style={{ fontWeight: 800, fontSize: 20, marginBottom: 8 }}>연락 주세요</div>
                            <p style={{ color: "#6B7280", fontSize: 13.5, maxWidth: 360 }}>{profile.intro.contactBlurb}</p>
                        </div>
                        <div style={{ display: "flex", gap: 20, flexWrap: "wrap" }}>
                            {profile.contact.phone && (
                                <a className="pp-foot-link" href={`tel:${profile.contact.phone}`}>{profile.contact.phone}</a>
                            )}
                            {profile.contact.github && (
                                <a className="pp-foot-link" href={profile.contact.github} target="_blank" rel="noopener noreferrer">GitHub ↗</a>
                            )}
                            {profile.contact.blog && (
                                <a className="pp-foot-link" href={profile.contact.blog} target="_blank" rel="noopener noreferrer">블로그 ↗</a>
                            )}
                        </div>
                    </div>
                    <div style={{ marginTop: 40, fontFamily: "var(--pp-mono)", fontSize: 11, color: "#9CA1B5" }}>
                        신지훈 · {profile.intro.roleTagline} · {profile.intro.focusTags.join(" / ")}
                    </div>
                </section>
            )}
        </div>
    );
}

// ── 서브 컴포넌트 ─────────────────────────────────────────

// 헤드라인의 줄바꿈(\n)을 <br/>로, {{강조문구}}를 accent 색 <span>으로 렌더링
function renderHeadline(text: string) {
    return text.split("\n").map((line, i) => (
        <span key={i}>
            {i > 0 && <br />}
            {line.split(/(\{\{[^}]*\}\})/g).map((part, j) => {
                const m = part.match(/^\{\{([^}]*)\}\}$/);
                return m ? (
                    <span key={j} style={{ color: accent }}>{m[1]}</span>
                ) : (
                    <span key={j}>{part}</span>
                );
            })}
        </span>
    ));
}

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
    topBar: { display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12, padding: "32px 0 0" },
    hero: { padding: "20px 0 60px" },
    badgeRow: { display: "flex", flexWrap: "wrap", gap: 8, marginBottom: 18 },
    headline: { fontWeight: 800, fontSize: "clamp(28px, 4vw, 42px)", lineHeight: 1.3, letterSpacing: "-0.01em", maxWidth: 720, marginBottom: 18 },
    subhead: { fontSize: 16, color: "#6B7280", maxWidth: 580, marginBottom: 34, lineHeight: 1.65 },
    statRow: { display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 1, background: "#E3E6F0", border: "1px solid #E3E6F0", borderRadius: 8, overflow: "hidden", maxWidth: 700 },
    overviewGrid: { display: "grid", gridTemplateColumns: "1.15fr 0.85fr", gap: 40, alignItems: "start" },
    p: { color: "#6B7280", fontSize: 15, marginBottom: 14, lineHeight: 1.7 },
    eduGrid: { display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 },
    featGrid: { display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 16, marginBottom: 46 },
};

// hover/accordion처럼 인라인 style로 표현하기 번거로운 부분만 CSS로 (PersonaGate.tsx 패턴과 동일)
const CSS = `
  .pp-eyebrow{ font-family: var(--pp-mono); font-size:13px; color:${accent}; }

  .pp-badge{ display:inline-flex; align-items:center; gap:7px; padding:6px 13px; border-radius:999px; font-size:12.5px; font-weight:600; }
  .pp-badge::before{ content:''; width:8px; height:8px; border-radius:50%; flex-shrink:0; }
  .pp-badge.seek{ background:#ECFDF3; border:1px solid #A6F4C5; color:#067647; }
  .pp-badge.seek::before{ background:#22C55E; box-shadow:0 0 0 3px rgba(34,197,94,0.18); animation: pp-status-pulse 2s ease-in-out infinite; }
  .pp-badge.on{ background:#EEF0FE; border:1px solid #DEE1FA; color:${accent}; }
  .pp-badge.on::before{ background:${accent}; }
  .pp-badge.muted{ background:#F0F1F8; border:1px solid #E3E6F0; color:#9CA1B5; }
  .pp-badge.muted::before{ background:#C7CBDE; }
  @keyframes pp-status-pulse{ 0%,100%{ opacity:1 } 50%{ opacity:.4 } }

  .pp-section{ padding: 12px 0 60px; }

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

  .pp-cd-layout{ display:flex; align-items:flex-start; gap:28px; }
  .pp-cd-nav{ flex:0 0 208px; position:sticky; top:12px; display:flex; flex-direction:column; gap:3px; }
  .pp-cd-nav-item{ display:flex; align-items:center; gap:9px; padding:10px 12px; border-radius:7px; border:1px solid transparent; background:none; cursor:pointer; text-align:left; font-family:inherit; font-size:13.5px; color:#6B7280; transition: background .15s, color .15s, border-color .15s; }
  .pp-cd-nav-item:hover{ background:#F5F6FB; color:#1B2236; }
  .pp-cd-nav-item.active{ background:#EEF0FE; border-color:#DEE1FA; color:${accent}; font-weight:700; }
  .pp-cd-nav-dot{ width:6px; height:6px; border-radius:50%; background:#C7CBDE; flex-shrink:0; }
  .pp-cd-nav-item.active .pp-cd-nav-dot{ background:${accent}; }
  .pp-cd-nav-label{ flex:1; }
  .pp-cd-nav-count{ font-family: var(--pp-mono); font-size:10.5px; color:#9CA1B5; }
  .pp-cd-nav-item.active .pp-cd-nav-count{ color:${accent}; }
  .pp-cd-content{ flex:1; min-width:0; display:flex; flex-direction:column; gap:14px; }

  .pp-cd-card-btn{ display:block; background:#fff; border:1px solid #E3E6F0; border-radius:8px; padding:16px 22px; text-decoration:none; color:inherit; transition: border-color .15s, transform .15s, box-shadow .15s; }
  .pp-cd-card-btn:hover{ border-color:${accent}; transform: translateY(-2px); box-shadow: 0 8px 20px rgba(99,102,241,0.08); }
  .pp-cd-arrow{ font-family: var(--pp-mono); color:#9CA1B5; font-size:17px; transition: transform .15s, color .15s; flex-shrink:0; }
  .pp-cd-card-btn:hover .pp-cd-arrow{ transform: translateX(3px); color:${accent}; }

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
    .pp-cd-layout{ flex-direction: column; }
    .pp-cd-nav{ position: static; flex-direction: row; overflow-x: auto; width: 100%; gap: 6px; padding-bottom: 4px; }
    .pp-cd-nav-item{ flex-shrink: 0; }
  }
`;
