const API_BASE = import.meta.env.VITE_API_BASE || "";

export interface IntroSection {
    id: number;
    subtitle: string;
    lines: string[];
    sortOrder: number;
}

export interface CareerProject {
    id: number;
    companyId: number;
    title: string;
    periodLabel: string | null;
    paragraphs: string[];
    sortOrder: number;
}

export interface CareerCompany {
    id: number;
    path: string;
    domain: string | null;
    companyName: string;
    periodLabel: string;
    role: string;
    isCurrent: boolean;
    commitHash: string | null;
    commitTag: string | null;
    stack: string[];
    displayOrder: number;
    projects: CareerProject[];
}

export interface Education {
    id: number;
    periodLabel: string;
    school: string;
    major: string;
    displayOrder: number;
}

export interface BasicProfile {
    intro: {
        elevatorPitch: string;
        highlights: string[];
        headline: string;
        subheadline: string;
        sections: IntroSection[];
    };
    contact: {
        phone: string;
        github: string;
        blog: string;
    };
    career: CareerCompany[];
    education: Education[];
}

interface IntroUpdateInput {
    elevatorPitch: string;
    highlights: string[];
    headline: string;
    subheadline: string;
}

interface IntroSectionInput {
    subtitle: string;
    lines: string[];
    sortOrder: number;
}

interface ContactUpdateInput {
    phone: string;
    github: string;
    blog: string;
}

interface CareerCompanyInput {
    path: string;
    domain: string | null;
    companyName: string;
    periodLabel: string;
    role: string;
    isCurrent: boolean;
    commitHash: string | null;
    commitTag: string | null;
    stack: string[];
    sortOrder: number;
}

interface CareerProjectInput {
    companyId: number;
    title: string;
    periodLabel: string | null;
    paragraphs: string[];
    sortOrder: number;
}

interface EducationInput {
    periodLabel: string;
    school: string;
    major: string;
    sortOrder: number;
}

async function request<T>(path: string, method: string, body?: unknown): Promise<T> {
    const res = await fetch(`${API_BASE}${path}`, {
        method,
        headers: body !== undefined ? { "Content-Type": "application/json" } : undefined,
        body: body !== undefined ? JSON.stringify(body) : undefined,
    });
    if (!res.ok) throw new Error(`요청 실패 (${res.status}): ${method} ${path}`);
    const text = await res.text();
    return text ? (JSON.parse(text) as T) : (undefined as T);
}

export function fetchProfile(): Promise<BasicProfile> {
    return request<BasicProfile>("/api/profile", "GET");
}

// ── 소개 ─────────────────────────────────────────
export function updateIntro(input: IntroUpdateInput): Promise<void> {
    return request<void>("/api/profile/intro", "PUT", input);
}

export function addIntroSection(input: IntroSectionInput): Promise<IntroSection> {
    return request<IntroSection>("/api/profile/intro/sections", "POST", input);
}

export function updateIntroSection(id: number, input: IntroSectionInput): Promise<void> {
    return request<void>(`/api/profile/intro/sections/${id}`, "PUT", input);
}

export function deleteIntroSection(id: number): Promise<void> {
    return request<void>(`/api/profile/intro/sections/${id}`, "DELETE");
}

// ── 연락처 ─────────────────────────────────────────
export function updateContact(input: ContactUpdateInput): Promise<void> {
    return request<void>("/api/profile/contact", "PUT", input);
}

// ── 경력(회사) ─────────────────────────────────────
export function addCareerCompany(input: CareerCompanyInput): Promise<CareerCompany> {
    return request<CareerCompany>("/api/profile/career", "POST", input);
}

export function updateCareerCompany(id: number, input: CareerCompanyInput): Promise<void> {
    return request<void>(`/api/profile/career/${id}`, "PUT", input);
}

export function deleteCareerCompany(id: number): Promise<void> {
    return request<void>(`/api/profile/career/${id}`, "DELETE");
}

// ── 경력(프로젝트) ─────────────────────────────────
export function addCareerProject(input: CareerProjectInput): Promise<CareerProject> {
    return request<CareerProject>("/api/profile/career/projects", "POST", input);
}

export function updateCareerProject(id: number, input: CareerProjectInput): Promise<void> {
    return request<void>(`/api/profile/career/projects/${id}`, "PUT", input);
}

export function deleteCareerProject(id: number): Promise<void> {
    return request<void>(`/api/profile/career/projects/${id}`, "DELETE");
}

// ── 학력 ─────────────────────────────────────────
export function addEducation(input: EducationInput): Promise<Education> {
    return request<Education>("/api/profile/education", "POST", input);
}

export function updateEducation(id: number, input: EducationInput): Promise<void> {
    return request<void>(`/api/profile/education/${id}`, "PUT", input);
}

export function deleteEducation(id: number): Promise<void> {
    return request<void>(`/api/profile/education/${id}`, "DELETE");
}
