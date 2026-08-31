const API_BASE = import.meta.env.VITE_API_BASE || "";

export interface IntroSection {
    id: number;
    subtitle: string;
    lines: string[];
    sortOrder: number;
}

export interface CareerTaskMedia {
    id: number;
    taskId: number;
    url: string;
    mediaKind: "IMAGE" | "VIDEO";
    sortOrder: number;
}

export interface CareerProjectTask {
    id: number;
    projectId: number;
    description: string;
    sortOrder: number;
    media: CareerTaskMedia[];
}

export interface CareerProject {
    id: number;
    companyId: number;
    title: string;
    periodLabel: string | null;
    overview: string;
    sortOrder: number;
    tasks: CareerProjectTask[];
}

export interface CareerCompany {
    id: number;
    path: string;
    domain: string | null;
    companyName: string;
    shortName: string | null;
    periodLabel: string;
    startDate: string | null; // "yyyy-MM-dd"
    endDate: string | null;   // "yyyy-MM-dd", null = 재직중
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
        roleTagline: string;
        focusTags: string[];
        contactBlurb: string;
        sideProject: string;
        availability: string; // 구직/이직 준비 중일 때 표시할 문구
        openToWork: boolean;  // 구직/이직 준비 여부
    };
    contact: {
        phone: string;
        github: string;
        blog: string;
    };
    stats: {
        totalCareerMonths: number;
        totalCareerLabel: string; // "8년 4개월"
        companyCount: number;
        projectCount: number;
        employed: boolean;
        currentCompany: string; // 재직중이면 회사명, 아니면 ""
        currentSince: string; // "yyyy-MM", 재직중 아니면 ""
    };
    career: CareerCompany[];
    education: Education[];
}

interface IntroUpdateInput {
    elevatorPitch: string;
    highlights: string[];
    headline: string;
    subheadline: string;
    roleTagline: string;
    focusTags: string[];
    contactBlurb: string;
    sideProject: string;
    availability: string;
    openToWork: boolean;
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
    shortName: string | null;
    periodLabel: string;
    startDate: string | null;
    endDate: string | null;
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
    overview: string;
    sortOrder: number;
}

interface CareerProjectTaskInput {
    projectId: number;
    description: string;
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

// ── 경력(업무) ─────────────────────────────────────
export function addCareerProjectTask(input: CareerProjectTaskInput): Promise<CareerProjectTask> {
    return request<CareerProjectTask>("/api/profile/career/tasks", "POST", input);
}

export function updateCareerProjectTask(id: number, input: CareerProjectTaskInput): Promise<void> {
    return request<void>(`/api/profile/career/tasks/${id}`, "PUT", input);
}

export function deleteCareerProjectTask(id: number): Promise<void> {
    return request<void>(`/api/profile/career/tasks/${id}`, "DELETE");
}

// ── 경력(업무 미디어) ─────────────────────────────────
export async function uploadCareerTaskMedia(taskId: number, file: File): Promise<CareerTaskMedia> {
    const formData = new FormData();
    formData.append("file", file);
    const res = await fetch(`${API_BASE}/api/profile/career/tasks/${taskId}/media`, {
        method: "POST",
        body: formData,
    });
    if (!res.ok) throw new Error(`업로드 실패 (${res.status})`);
    return res.json();
}

export function deleteCareerTaskMedia(mediaId: number): Promise<void> {
    return request<void>(`/api/profile/career/tasks/media/${mediaId}`, "DELETE");
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
