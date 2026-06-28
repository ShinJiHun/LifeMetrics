// 백엔드(/api/content) 연동 콘텐츠 스토어
// - GET 으로 전체 트리를 받아 메모리 캐시에 보관, useSyncExternalStore 로 React 구독
// - 모든 뮤테이션은 서버 호출 후 refresh() 로 캐시를 재적재(쓰기 후 재조회)
// - 서버 id(Long) 는 프론트 전체에서 문자열로 정규화해 사용
import { useSyncExternalStore } from "react";
import api from "@/lib/axios";
import type { Category, ContentDB, Persona, Post, SubMenu, Visibility } from "@/types/content";

const EMPTY: ContentDB = { categories: [], subMenus: [], posts: [] };

// ── 내부 캐시 ────────────────────────────────────────
let db: ContentDB = EMPTY;
const listeners = new Set<() => void>();

function emit(): void {
    listeners.forEach((l) => l());
}

function setDb(next: ContentDB): void {
    db = next;
    emit();
}

function subscribe(listener: () => void): () => void {
    listeners.add(listener);
    ensureLoaded();
    return () => listeners.delete(listener);
}

function getSnapshot(): ContentDB {
    return db;
}

// 서버 응답의 숫자 id 를 문자열로 정규화
function normalize(raw: ContentDB): ContentDB {
    return {
        categories: (raw.categories ?? []).map((c) => ({ ...c, id: String(c.id) })),
        subMenus: (raw.subMenus ?? []).map((s) => ({
            ...s,
            id: String(s.id),
            categoryId: String(s.categoryId),
        })),
        posts: (raw.posts ?? []).map((p) => ({
            ...p,
            id: String(p.id),
            subId: String(p.subId),
            title: p.title ?? "",
            body: p.body ?? "",
            visibility: (p.visibility as Visibility) ?? "public",
            createdAt: p.createdAt ?? "",
            updatedAt: p.updatedAt ?? "",
        })),
    };
}

export async function refresh(): Promise<void> {
    const res = await api.get<ContentDB>("/content");
    setDb(normalize(res.data));
}

let started = false;
function ensureLoaded(): void {
    if (started) return;
    started = true;
    refresh().catch((e) => {
        started = false; // 실패 시 다음 구독에서 재시도
        console.error("[content] 초기 로드 실패", e);
    });
}

// ── 훅 ───────────────────────────────────────────────
export function useContent(): ContentDB {
    return useSyncExternalStore(subscribe, getSnapshot, getSnapshot);
}

// ── 셀렉터 (순수 함수) ───────────────────────────────
export function categoriesOf(data: ContentDB, persona: Persona): Category[] {
    return data.categories.filter((c) => c.persona === persona).sort(byOrder);
}

export function subMenusOf(data: ContentDB, categoryId: string): SubMenu[] {
    return data.subMenus.filter((s) => s.categoryId === categoryId).sort(byOrder);
}

export function postsOf(data: ContentDB, subId: string): Post[] {
    return data.posts
        .filter((p) => p.subId === subId)
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
}

export function findSubMenu(data: ContentDB, subId: string): SubMenu | undefined {
    return data.subMenus.find((s) => s.id === subId);
}

export function findCategory(data: ContentDB, categoryId: string): Category | undefined {
    return data.categories.find((c) => c.id === categoryId);
}

export function findPost(data: ContentDB, postId: string): Post | undefined {
    return data.posts.find((p) => p.id === postId);
}

function byOrder(a: { order: number }, b: { order: number }): number {
    return a.order - b.order;
}

// ── 대메뉴(Category) ─────────────────────────────────
export async function addCategory(persona: Persona, name: string): Promise<void> {
    await api.post("/content/categories", { persona, name });
    await refresh();
}

export async function renameCategory(categoryId: string, name: string): Promise<void> {
    await api.patch(`/content/categories/${categoryId}`, { name });
    await refresh();
}

export async function deleteCategory(categoryId: string): Promise<void> {
    await api.delete(`/content/categories/${categoryId}`);
    await refresh();
}

// ── 소메뉴(SubMenu) ──────────────────────────────────
export async function addSubMenu(categoryId: string, name: string): Promise<void> {
    await api.post("/content/subs", { categoryId: Number(categoryId), name });
    await refresh();
}

export async function renameSubMenu(subId: string, name: string): Promise<void> {
    await api.patch(`/content/subs/${subId}`, { name });
    await refresh();
}

export async function deleteSubMenu(subId: string): Promise<void> {
    await api.delete(`/content/subs/${subId}`);
    await refresh();
}

// ── 글(Post) ─────────────────────────────────────────
export async function addPost(input: {
    subId: string;
    title: string;
    body: string;
    visibility: Visibility;
}): Promise<Post> {
    const res = await api.post<Post>("/content/posts", {
        subId: Number(input.subId),
        title: input.title,
        body: input.body,
        visibility: input.visibility,
    });
    await refresh();
    return { ...res.data, id: String(res.data.id), subId: String(res.data.subId) };
}

export async function updatePost(
    postId: string,
    patch: { subId?: string; title?: string; body?: string; visibility?: Visibility },
): Promise<void> {
    await api.patch(`/content/posts/${postId}`, {
        subId: patch.subId !== undefined ? Number(patch.subId) : undefined,
        title: patch.title,
        body: patch.body,
        visibility: patch.visibility,
    });
    await refresh();
}

export async function deletePost(postId: string): Promise<void> {
    await api.delete(`/content/posts/${postId}`);
    await refresh();
}
