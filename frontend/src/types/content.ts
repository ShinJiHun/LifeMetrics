// 개발자·인간 페르소나 블로그 콘텐츠 모델 (localStorage 기반)
// 대메뉴(Category) → 소메뉴(SubMenu) → 글(Post) 2단계 구조

export type Persona = "developer" | "human";
export type Visibility = "public" | "private";

// 대메뉴 — 예: 자바의 정석 / 회사 일기
export interface Category {
    id: string;
    persona: Persona;
    name: string;
    order: number;
}

// 소메뉴 — 예: 1단원 / 26년
export interface SubMenu {
    id: string;
    categoryId: string;
    name: string;
    order: number;
}

// 글 — 예: 2.1 변수 / 1주차
export interface Post {
    id: string;
    subId: string;
    title: string;
    body: string;
    visibility: Visibility;
    createdAt: string;
    updatedAt: string;
}

export interface ContentDB {
    categories: Category[];
    subMenus: SubMenu[];
    posts: Post[];
}
