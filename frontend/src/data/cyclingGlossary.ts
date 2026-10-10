// ── 자전거 용어 사전 ─────────────────────────────────────────────
// 용어 사전 화면과, AI 분석 등 본문에서 마우스를 올리면 뜻이 뜨는 툴팁이 같은 데이터를 쓴다.
// term 과 aliases 가 본문에서 그대로 일치하면 하이라이트된다(영문 약어는 대소문자 구분).

export type GlossaryCategory = "훈련 지표" | "파워·심박 존" | "훈련 종류" | "라이딩" | "몸·영양" | "장비·피팅" | "이벤트";

export interface GlossaryEntry {
    /** 고유 키 겸 화면에 보이는 대표 이름 */
    term: string;
    /** 본문에서 같은 뜻으로 잡을 다른 표기 */
    aliases?: string[];
    /** 영문 풀이 (약어일 때) */
    full?: string;
    category: GlossaryCategory;
    /** 툴팁에 뜨는 한두 문장 */
    short: string;
    /** 사전 화면에서만 보이는 추가 설명 */
    detail?: string;
}

export const GLOSSARY_CATEGORIES: GlossaryCategory[] = [
    "훈련 지표", "파워·심박 존", "훈련 종류", "라이딩", "몸·영양", "장비·피팅", "이벤트",
];

export const CYCLING_GLOSSARY: GlossaryEntry[] = [
    // ── 훈련 지표 ──
    {
        term: "FTP", full: "Functional Threshold Power", aliases: ["기능적 역치 파워"], category: "훈련 지표",
        short: "약 1시간 동안 유지할 수 있는 최대 평균 파워(W). 파워존·TSS·IF 계산의 기준값.",
        detail: "보통 20분 전력 테스트 평균 파워의 95% 또는 램프 테스트로 추정한다. 체력이 바뀌면 4~8주마다 다시 재는 것이 좋다.",
    },
    {
        term: "TSS", full: "Training Stress Score", aliases: ["훈련 스트레스 점수"], category: "훈련 지표",
        short: "운동 강도와 시간을 합쳐 한 번의 라이딩이 몸에 준 부하를 점수로 나타낸 것. FTP로 1시간 타면 100.",
        detail: "TSS = (시간(초) × NP × IF) ÷ (FTP × 3600) × 100. 대략 150 미만은 다음날 회복, 150~300은 이틀 정도 피로, 300 이상은 며칠간 피로가 남는다.",
    },
    {
        term: "IF", full: "Intensity Factor", aliases: ["강도 계수"], category: "훈련 지표",
        short: "NP ÷ FTP. 그날 라이딩이 FTP 대비 얼마나 강했는지 보여주는 비율.",
        detail: "0.75 미만은 회복·지구력, 0.75~0.85 템포, 0.85~0.95 스윗스팟·역치, 1.0 이상은 1시간 이내의 레이스 강도.",
    },
    {
        term: "NP", full: "Normalized Power", aliases: ["정규화 파워", "노멀라이즈드 파워"], category: "훈련 지표",
        short: "파워 변동을 반영해 '실제로 몸이 느낀 평균 파워'로 보정한 값. 언덕·스프린트가 많을수록 평균 파워보다 높다.",
    },
    {
        term: "VI", full: "Variability Index", aliases: ["변동 지수"], category: "훈련 지표",
        short: "NP ÷ 평균 파워. 1.0에 가까울수록 일정한 페이스로 탔다는 뜻이고, 1.1 이상이면 출력 변동이 컸다는 뜻.",
    },
    {
        term: "W/kg", aliases: ["파워 대 체중비", "체중당 파워"], category: "훈련 지표",
        short: "파워(W)를 체중(kg)으로 나눈 값. 오르막 성능을 가장 잘 나타내는 지표.",
        detail: "FTP 기준으로 아마추어 평균은 2.5~3.0, 잘 훈련된 동호인은 3.5~4.0, 프로는 5.5 이상이다.",
    },
    {
        term: "CTL", full: "Chronic Training Load", aliases: ["만성 훈련 부하"], category: "훈련 지표",
        short: "최근 약 42일간 TSS의 지수 가중 평균. 쌓아온 '체력'의 크기.",
    },
    {
        term: "ATL", full: "Acute Training Load", aliases: ["급성 훈련 부하"], category: "훈련 지표",
        short: "최근 약 7일간 TSS의 지수 가중 평균. 지금 쌓여 있는 '피로'의 크기.",
    },
    {
        term: "TSB", full: "Training Stress Balance", aliases: ["훈련 스트레스 균형"], category: "훈련 지표",
        short: "CTL − ATL. 플러스면 회복돼 컨디션이 좋은 상태, 크게 마이너스면 피로가 쌓인 상태.",
        detail: "대회 전 테이퍼링으로 +5~+25 정도를 만드는 것이 일반적이다. −30 아래로 오래 머물면 과훈련 위험.",
    },
    {
        term: "kJ", aliases: ["킬로줄"], category: "훈련 지표",
        short: "페달에 실제로 전달한 일의 양. 사람 몸의 효율(약 20~25%) 덕분에 kJ 값 ≈ 소모 칼로리(kcal)로 본다.",
    },
    {
        term: "EF", full: "Efficiency Factor", aliases: ["효율 지수"], category: "훈련 지표",
        short: "NP ÷ 평균 심박. 같은 심박에서 더 큰 파워를 낼수록 높아지며, 유산소 체력이 좋아지는지 보는 지표.",
    },
    {
        term: "디커플링", aliases: ["Pa:HR", "심박 디커플링"], category: "훈련 지표",
        short: "긴 라이딩 후반에 파워는 그대로인데 심박만 올라가는 현상. 5% 미만이면 지구력이 충분하다는 신호.",
    },
    {
        term: "VO2max", aliases: ["최대 산소 섭취량", "VO2 max"], category: "훈련 지표",
        short: "몸이 1분 동안 쓸 수 있는 최대 산소량(ml/kg/min). 유산소 능력의 상한선.",
    },
    {
        term: "LTHR", full: "Lactate Threshold Heart Rate", aliases: ["역치 심박"], category: "훈련 지표",
        short: "젖산역치에서의 심박수. 심박존을 나누는 기준값.",
    },
    {
        term: "젖산역치", aliases: ["LT", "무산소 역치"], category: "훈련 지표",
        short: "젖산이 쌓이는 속도가 제거 속도를 넘어서기 시작하는 강도. FTP는 이 지점의 파워와 거의 같다.",
    },
    {
        term: "최대심박", aliases: ["최대 심박", "HRmax"], category: "훈련 지표",
        short: "낼 수 있는 가장 높은 심박수. 나이 공식(220−나이)은 오차가 커서 실제 측정값이 낫다.",
    },
    {
        term: "HRV", full: "Heart Rate Variability", aliases: ["심박 변이도"], category: "훈련 지표",
        short: "심장 박동 사이 간격의 변동 폭. 평소보다 낮으면 피로·스트레스가 쌓였다는 신호.",
    },
    {
        term: "상대강도", aliases: ["Relative Effort"], category: "훈련 지표",
        short: "심박 데이터를 기준으로 계산한 운동 부하 점수(Strava 방식). 파워미터가 없을 때 TSS 대신 볼 수 있다.",
    },
    {
        term: "파워커브", aliases: ["파워 커브", "MMP"], category: "훈련 지표",
        short: "5초·1분·5분·20분 등 각 시간 동안 낼 수 있었던 최고 평균 파워를 이은 곡선. 스프린터형/지구력형 성향이 보인다.",
    },

    // ── 파워·심박 존 ──
    {
        term: "파워존", aliases: ["파워 존"], category: "파워·심박 존",
        short: "FTP 대비 비율로 강도를 7단계(Z1~Z7)로 나눈 구간. 훈련 목적별로 어느 구간을 탈지 정한다.",
    },
    {
        term: "심박존", aliases: ["심박 존"], category: "파워·심박 존",
        short: "LTHR(또는 최대심박) 대비 비율로 강도를 5단계 안팎으로 나눈 구간. 파워미터가 없을 때 쓴다.",
    },
    {term: "Z1", category: "파워·심박 존", short: "회복 구간. FTP의 55% 미만. 대화가 아주 편한 강도."},
    {term: "Z2", category: "파워·심박 존", short: "지구력 구간. FTP의 56~75%. 장거리·기초 체력의 핵심 강도."},
    {term: "Z3", category: "파워·심박 존", short: "템포 구간. FTP의 76~90%. 대화가 짧게 끊기는 정도."},
    {term: "Z4", category: "파워·심박 존", short: "역치 구간. FTP의 91~105%. 10~60분 버틸 수 있는 강도."},
    {term: "Z5", category: "파워·심박 존", short: "VO2max 구간. FTP의 106~120%. 3~8분 인터벌에 쓴다."},
    {term: "Z6", category: "파워·심박 존", short: "무산소 구간. FTP의 121~150%. 30초~2분 고강도."},
    {term: "Z7", category: "파워·심박 존", short: "신경근 구간. FTP의 150% 이상. 몇 초 짜리 전력 스프린트."},

    // ── 훈련 종류 ──
    {
        term: "인터벌", aliases: ["인터벌 훈련"], category: "훈련 종류",
        short: "고강도 구간과 휴식 구간을 번갈아 반복하는 훈련. 이 앱은 FTP 100% 이상을 일정 시간 유지한 구간을 인터벌로 잡는다.",
    },
    {
        term: "스윗스팟", aliases: ["스윗 스팟", "Sweet Spot"], category: "훈련 종류",
        short: "FTP의 88~94% 강도. 피로 대비 FTP 향상 효과가 커서 시간이 부족한 동호인에게 인기.",
    },
    {
        term: "템포", aliases: ["템포 라이딩"], category: "훈련 종류",
        short: "Z3 강도로 길게 유지하는 훈련. 지구력과 근지구력을 함께 올린다.",
    },
    {
        term: "엔듀런스", aliases: ["지구력 훈련", "LSD"], category: "훈련 종류",
        short: "Z2 강도로 오래 타는 훈련(LSD: Long Slow Distance). 지방 대사와 유산소 기초를 만든다.",
    },
    {
        term: "회복 라이딩", aliases: ["리커버리 라이딩"], category: "훈련 종류",
        short: "Z1 강도로 30~60분 가볍게 도는 라이딩. 혈류를 늘려 피로 회복을 돕는다.",
    },
    {
        term: "FTP 테스트", aliases: ["램프 테스트", "20분 테스트"], category: "훈련 종류",
        short: "FTP를 재는 테스트. 20분 전력 평균 × 0.95 방식과, 1분마다 강도를 올리는 램프 방식이 대표적.",
    },
    {
        term: "테이퍼링", aliases: ["테이퍼"], category: "훈련 종류",
        short: "대회 1~2주 전부터 훈련량을 줄이고 강도만 짧게 유지해 피로를 빼는 과정.",
    },

    // ── 라이딩 ──
    {
        term: "케이던스", aliases: ["RPM", "rpm"], category: "라이딩",
        short: "1분 동안 페달을 돌린 횟수(rpm). 평지 85~95rpm이 일반적이며, 높을수록 근육 부담이 줄고 심폐 부담이 늘어난다.",
    },
    {
        term: "획득고도", aliases: ["누적 상승고도", "Elevation Gain"], category: "라이딩",
        short: "라이딩 중 오르막에서 올라간 높이를 모두 더한 값(m).",
    },
    {
        term: "경사도", aliases: ["그레이디언트"], category: "라이딩",
        short: "수평 100m 갈 때 올라가는 높이(%). 5% 이상부터 힘들게 느껴지고, 10% 이상은 급경사.",
    },
    {
        term: "드래프팅", aliases: ["드래프트", "피닝"], category: "라이딩",
        short: "앞 라이더 바로 뒤에 붙어 공기 저항을 줄이는 것. 같은 속도에서 파워를 20~30% 아낄 수 있다.",
    },
    {
        term: "페이싱", aliases: ["페이스 조절"], category: "라이딩",
        short: "전체 거리를 끝까지 탈 수 있도록 초반부터 출력을 나눠 쓰는 전략. 장거리일수록 VI를 낮게 유지하는 것이 핵심.",
    },
    {
        term: "업힐", aliases: ["클라이밍"], category: "라이딩",
        short: "오르막 구간 또는 오르막을 오르는 것. 성능은 W/kg가 좌우한다.",
    },

    // ── 몸·영양 ──
    {
        term: "봉크", aliases: ["봉킹", "헝거노크"], category: "몸·영양",
        short: "몸속 글리코겐이 바닥나 갑자기 힘이 빠지고 어지러운 상태. 장거리에서는 시간당 60~90g 탄수화물 보충으로 막는다.",
    },
    {
        term: "카보로딩", aliases: ["카보 로딩", "탄수화물 로딩"], category: "몸·영양",
        short: "장거리 대회 1~3일 전 탄수화물 섭취를 늘려 근육·간에 글리코겐을 최대한 채워두는 방법.",
    },
    {
        term: "글리코겐", category: "몸·영양",
        short: "근육과 간에 저장된 탄수화물. 고강도 운동의 주 연료이며 보통 90분~2시간 정도 분량이 저장된다.",
    },
    {
        term: "과훈련", aliases: ["오버트레이닝"], category: "몸·영양",
        short: "회복보다 훈련 부하가 오래 앞서 성능이 오히려 떨어지는 상태. 안정 심박 상승, HRV 저하, 수면 질 저하가 신호.",
    },

    // ── 장비·피팅 ──
    {
        term: "파워미터", aliases: ["파워 미터"], category: "장비·피팅",
        short: "페달·크랭크·허브 등에서 실제 출력(W)을 재는 센서. 없으면 앱은 속도·경사로 파워를 추정한다.",
    },
    {
        term: "기어비", category: "장비·피팅",
        short: "앞 체인링 톱니 수 ÷ 뒤 스프라켓 톱니 수. 클수록 무겁고(빠르고), 작을수록 가볍다(오르막용).",
    },
    {
        term: "체인링", category: "장비·피팅",
        short: "크랭크에 달린 앞쪽 톱니바퀴. 50/34(컴팩트), 52/36(미드), 53/39(노멀) 등으로 표기한다.",
    },
    {
        term: "스프라켓", aliases: ["카세트"], category: "장비·피팅",
        short: "뒷바퀴에 달린 톱니 묶음. 11-34T처럼 가장 작은/큰 톱니 수로 표기하며, 큰 톱니가 클수록 오르막이 쉽다.",
    },
    {
        term: "크랭크 길이", aliases: ["크랭크암 길이"], category: "장비·피팅",
        short: "BB 축에서 페달 축까지의 길이(165~175mm). 짧을수록 고케이던스·무릎 부담 감소에 유리하다.",
    },
    {
        term: "클릿", category: "장비·피팅",
        short: "신발 바닥에 달아 페달과 고정하는 부품. 각도·위치가 틀어지면 무릎 통증의 원인이 된다.",
    },
    {
        term: "안장높이", aliases: ["안장 높이"], category: "장비·피팅",
        short: "BB 중심에서 안장 윗면까지의 길이. 페달이 가장 아래일 때 무릎이 25~35° 정도 굽는 높이가 기준.",
    },
    {
        term: "스택", aliases: ["Stack"], category: "장비·피팅",
        short: "BB 중심에서 헤드튜브 윗면까지의 수직 높이. 클수록 상체가 서는 편한 자세.",
    },
    {
        term: "리치", aliases: ["Reach"], category: "장비·피팅",
        short: "BB 중심에서 헤드튜브 윗면까지의 수평 거리. 길수록 상체가 앞으로 길게 숙여진다.",
    },
    {
        term: "튜블리스", aliases: ["튜브리스"], category: "장비·피팅",
        short: "튜브 없이 타이어와 림이 밀착해 공기를 막는 방식. 실런트가 작은 펑크를 자동으로 막고 낮은 공기압으로 탈 수 있다.",
    },
    {
        term: "공기압", aliases: ["타이어 공기압", "PSI", "psi"], category: "장비·피팅",
        short: "타이어 안의 공기 압력(psi 또는 bar). 체중·타이어 폭·노면에 맞춰야 하고, 무조건 높다고 빠르지 않다.",
    },

    // ── 이벤트 ──
    {
        term: "브레베", aliases: ["Brevet"], category: "이벤트",
        short: "200·300·400·600km 등 정해진 거리를 제한 시간 안에 스스로 완주하는 비경쟁 장거리 라이딩.",
        detail: "코스 중간 체크포인트(컨트롤)에서 통과 확인을 받아야 하며, 기록 경쟁이 아니라 완주 자체가 목표다.",
    },
    {
        term: "퍼머넌트", aliases: ["Permanent"], category: "이벤트",
        short: "날짜가 정해진 브레베와 달리 등록된 코스를 원하는 날 개인적으로 달려 인증받는 장거리 라이딩.",
    },
    {
        term: "랜도너", aliases: ["랑도너", "Randonneur"], category: "이벤트",
        short: "브레베를 타는 장거리 라이더. 한 시즌에 200·300·400·600km를 모두 완주하면 '슈퍼 랜도너'.",
    },
    {
        term: "그란폰도", aliases: ["Gran Fondo"], category: "이벤트",
        short: "보통 100km 이상 코스를 많은 인원이 함께 달리는 대중 자전거 대회. 기록은 재지만 순위 경쟁보다 완주가 목적.",
    },
];

// ── 본문 매칭 ──────────────────────────────────────────────────
const escapeRegExp = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const isAscii = (s: string) => /^[\x20-\x7e]+$/.test(s);

/** 표기(term/alias) → 사전 항목 */
export const GLOSSARY_LOOKUP: Map<string, GlossaryEntry> = new Map(
    CYCLING_GLOSSARY.flatMap((e) => [e.term, ...(e.aliases ?? [])].map((w) => [w, e] as const)),
);

/**
 * 본문에서 사전 단어를 찾는 정규식. 긴 표기를 먼저 시도해 "FTP 테스트" 가 "FTP" 보다 우선한다.
 * 영문 표기는 앞뒤가 영문·숫자가 아닐 때만 잡아 "IF" 가 다른 단어 속에서 잡히지 않게 한다.
 */
export const GLOSSARY_PATTERN: RegExp = new RegExp(
    [...GLOSSARY_LOOKUP.keys()]
        .sort((a, b) => b.length - a.length)
        .map((w) => (isAscii(w) ? `(?<![A-Za-z0-9])${escapeRegExp(w)}(?![A-Za-z0-9])` : escapeRegExp(w)))
        .join("|"),
    "g",
);

/** 사전 화면에서 항목 위치(#id)로 쓰는 값 */
export const glossaryAnchor = (term: string) => `term-${encodeURIComponent(term)}`;
