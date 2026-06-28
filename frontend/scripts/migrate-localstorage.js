/**
 * localStorage → 백엔드(/api/content) 콘텐츠 마이그레이션 (1회용)
 *
 * 사용법:
 *  1) 백엔드를 재기동하고 journal_db 에 테이블이 만들어진 상태여야 합니다.
 *  2) 실행 중인 프론트(예: http://localhost:5173) 를 브라우저에서 엽니다.
 *     ※ 반드시 앱과 "같은 출처"에서 실행해야 /api 프록시가 백엔드로 연결됩니다.
 *  3) DevTools(F12) → Console 탭에 이 파일 내용을 통째로 붙여넣고 Enter.
 *  4) 안내에 따라 진행. 끝나면 새로고침해서 메뉴/글이 보이는지 확인.
 *
 * 동작:
 *  - 대메뉴 → 소메뉴 → 글 순서로 생성하며 옛 id 를 새 서버 id 로 매핑합니다.
 *  - 순서(order)와 글 작성일(createdAt)이 보존됩니다. (백엔드가 전달된 작성일을 그대로 저장)
 *  - 두 번 실행하면 중복 생성됩니다. 한 번만 실행하세요.
 */
(async function migrateLocalContent() {
    const KEY = "lifemetrics.content.v1";

    const raw = localStorage.getItem(KEY);
    if (!raw) {
        console.warn(`[migrate] localStorage 키 "${KEY}" 가 없습니다. 마이그레이션할 데이터가 없습니다.`);
        return;
    }

    let dbData;
    try {
        dbData = JSON.parse(raw);
    } catch (e) {
        console.error("[migrate] localStorage 데이터 파싱 실패:", e);
        return;
    }

    const categories = [...(dbData.categories || [])].sort((a, b) => a.order - b.order);
    const subMenus = [...(dbData.subMenus || [])].sort((a, b) => a.order - b.order);
    const posts = [...(dbData.posts || [])].sort((a, b) =>
        (a.createdAt || "").localeCompare(b.createdAt || ""),
    );

    console.log(
        `[migrate] 대상: 대메뉴 ${categories.length} / 소메뉴 ${subMenus.length} / 글 ${posts.length}`,
    );
    if (!confirm(
        `백엔드로 이전합니다.\n대메뉴 ${categories.length} · 소메뉴 ${subMenus.length} · 글 ${posts.length}\n\n` +
        `※ 두 번 실행하면 중복됩니다. 진행할까요?`,
    )) {
        console.log("[migrate] 취소됨.");
        return;
    }

    async function post(url, body) {
        const r = await fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body),
        });
        if (!r.ok) throw new Error(`${url} → HTTP ${r.status}`);
        return r.json();
    }

    // 사전 점검: 백엔드 연결 확인
    try {
        const ping = await fetch("/api/content");
        if (!ping.ok) throw new Error(`HTTP ${ping.status}`);
    } catch (e) {
        console.error(
            "[migrate] /api/content 에 연결할 수 없습니다. 백엔드 재기동·테이블 생성·프록시(같은 출처)를 확인하세요.",
            e,
        );
        return;
    }

    const catMap = {}; // oldCategoryId -> newCategoryId
    const subMap = {}; // oldSubId -> newSubId

    try {
        for (const c of categories) {
            const created = await post("/api/content/categories", {
                persona: c.persona,
                name: c.name,
            });
            catMap[c.id] = created.id;
            console.log(`[migrate] 대메뉴 "${c.name}" (${c.persona}) → id ${created.id}`);
        }

        for (const s of subMenus) {
            const newCat = catMap[s.categoryId];
            if (!newCat) {
                console.warn(`[migrate] 소메뉴 "${s.name}" 의 상위 대메뉴를 못 찾아 건너뜀`);
                continue;
            }
            const created = await post("/api/content/subs", { categoryId: newCat, name: s.name });
            subMap[s.id] = created.id;
            console.log(`[migrate]   소메뉴 "${s.name}" → id ${created.id}`);
        }

        let made = 0;
        for (const p of posts) {
            const newSub = subMap[p.subId];
            if (!newSub) {
                console.warn(`[migrate] 글 "${p.title}" 의 상위 소메뉴를 못 찾아 건너뜀`);
                continue;
            }
            await post("/api/content/posts", {
                subId: newSub,
                title: p.title,
                body: p.body,
                visibility: p.visibility === "private" ? "private" : "public",
                createdAt: p.createdAt || null, // 원본 작성일 보존
            });
            made++;
            console.log(`[migrate]     글 "${p.title}" (${p.visibility}) 이전 완료`);
        }

        console.log(
            `%c[migrate] 완료! 대메뉴 ${Object.keys(catMap).length} · 소메뉴 ${Object.keys(subMap).length} · 글 ${made} 이전됨.`,
            "color:#16a34a;font-weight:bold",
        );
    } catch (e) {
        console.error("[migrate] 중단됨:", e);
        console.warn(
            "[migrate] 일부만 이전되었을 수 있습니다. 서버에서 잘못 들어간 항목을 정리한 뒤 다시 실행하세요.",
        );
        return;
    }

    // 백업본을 콘솔에 남기고, 원하면 옛 localStorage 비우기
    console.log("[migrate] 옛 데이터 백업(필요하면 복사해 두세요):");
    console.log(raw);
    if (confirm("이전이 끝났습니다. 옛 localStorage 데이터를 지울까요? (백업은 위 콘솔에 출력됨)")) {
        localStorage.removeItem(KEY);
        console.log("[migrate] localStorage 정리 완료. 페이지를 새로고침하세요.");
    } else {
        console.log("[migrate] localStorage 는 그대로 둡니다. (다시 실행하면 중복되니 주의)");
    }
})();
