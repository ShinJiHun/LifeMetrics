import api from "@/lib/axios";

/** 블로그 글 본문 이미지 업로드 (관리자). 서버 BlogImageController — 돌려받은 url 을 <img src> 로 쓴다. */
export async function uploadBlogImage(file: File): Promise<string> {
    const form = new FormData();
    form.append("file", file);
    const res = await api.post<{ url: string }>("/content/images", form, {
        headers: { "Content-Type": "multipart/form-data" },
    });
    return res.data.url;
}
