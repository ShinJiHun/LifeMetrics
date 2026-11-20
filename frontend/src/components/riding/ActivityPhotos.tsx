// 활동 사진 — 블로그 라이딩 후기 사진을 라이딩 지도에 연결 (data_pipeline/persona/blog_photo_link.py 가 채움)
// · 지도: 위치가 있는 사진을 썸네일 마커로 (추정 위치는 점선 테두리)
// · 지도 아래: 사진 목록(글 순서) — 누르면 크게 보기, 위치가 있으면 지도도 그 지점으로 이동
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import mapboxgl from "mapbox-gl";

export interface ActivityPhoto {
    id: number;
    sortOrder: number | null;
    thumbUrl: string;
    webUrl: string;
    originalUrl: string;
    takenAt: string | null;
    lat: number | null;
    lon: number | null;
    distanceKm: number | null;
    locationSource: "EXIF_TIME" | "ORDER_ESTIMATE" | "NONE" | null;
    caption: string | null;
    width: number | null;
    height: number | null;
    blogPostNo: number | null;
    blogPostTitle: string | null;
    blogPostUrl: string | null;
}

export const hasLocation = (p: ActivityPhoto) => p.lat != null && p.lon != null;

export function useActivityPhotos(activityId?: string | number) {
    const [photos, setPhotos] = useState<ActivityPhoto[]>([]);
    useEffect(() => {
        if (!activityId) return;
        let alive = true;
        fetch(`/api/activity/${activityId}/photos`)
            .then(r => (r.ok ? r.json() : []))
            .then(d => alive && setPhotos(Array.isArray(d) ? d : []))
            .catch(() => alive && setPhotos([]));
        return () => {
            alive = false;
        };
    }, [activityId]);
    return photos;
}

const timeLabel = (iso: string | null) => (iso ? iso.slice(11, 16) : "");

/** 지도에 사진 마커를 단다. 돌려주는 함수로 제거. */
export function addPhotoMarkers(
    map: mapboxgl.Map,
    photos: ActivityPhoto[],
    onClick: (p: ActivityPhoto) => void,
): { remove: () => void; highlight: (id: number | null) => void } {
    const els = new Map<number, HTMLDivElement>();
    const markers = photos.filter(hasLocation).map(p => {
        const el = document.createElement("div");
        el.className = "ap-marker" + (p.locationSource === "ORDER_ESTIMATE" ? " ap-marker--estimate" : "");
        el.style.backgroundImage = `url("${p.thumbUrl}")`;
        el.title = [timeLabel(p.takenAt), p.distanceKm != null ? `${p.distanceKm}km` : "", p.caption ?? ""]
            .filter(Boolean).join(" · ");
        el.addEventListener("click", e => {
            e.stopPropagation();
            onClick(p);
        });
        els.set(p.id, el);
        return new mapboxgl.Marker({element: el}).setLngLat([p.lon!, p.lat!]).addTo(map);
    });
    return {
        remove: () => markers.forEach(m => m.remove()),
        highlight: id => els.forEach((el, pid) => el.classList.toggle("ap-marker--active", pid === id)),
    };
}

/** 지도 아래 사진 목록 + 크게 보기 */
export function ActivityPhotoGallery({photos, selectedId, onSelect}: {
    photos: ActivityPhoto[];
    selectedId: number | null;
    onSelect: (p: ActivityPhoto | null) => void;
}) {
    const located = photos.filter(hasLocation).length;
    const posts = useMemo(() => {
        const seen = new Map<number, ActivityPhoto>();
        photos.forEach(p => p.blogPostNo != null && !seen.has(p.blogPostNo) && seen.set(p.blogPostNo, p));
        return [...seen.values()];
    }, [photos]);
    const stripRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (selectedId == null) return;
        stripRef.current?.querySelector(`[data-photo="${selectedId}"]`)
            ?.scrollIntoView({behavior: "smooth", inline: "center", block: "nearest"});
    }, [selectedId]);

    if (!photos.length) return null;
    const selected = photos.find(p => p.id === selectedId) ?? null;

    return (
        <section className="ap-card">
            <style>{CSS}</style>
            <div className="ap-head">
                <h3>📷 블로그 사진 <span className="ap-count">{photos.length}장</span></h3>
                <div className="ap-meta">
                    지도에 표시 {located}장
                    {photos.length - located > 0 && <> · 위치 정보 없음 {photos.length - located}장</>}
                </div>
                {posts.map(p => p.blogPostUrl && (
                    <a key={p.blogPostNo} className="ap-post" href={p.blogPostUrl} target="_blank" rel="noreferrer">
                        📝 {p.blogPostTitle} ↗
                    </a>
                ))}
            </div>
            <div className="ap-strip lm-scroll-hidden" ref={stripRef}>
                {photos.map(p => (
                    <button key={p.id} type="button" data-photo={p.id}
                            className={"ap-thumb" + (p.id === selectedId ? " ap-thumb--active" : "")}
                            onClick={() => onSelect(p)}
                            title={p.caption ?? ""}>
                        <img src={p.thumbUrl} alt={p.caption ?? ""} loading="lazy"/>
                        <span className={"ap-badge" + (hasLocation(p) ? "" : " ap-badge--none")}>
                            {hasLocation(p) ? (p.distanceKm != null ? `${p.distanceKm}km` : "📍") : "위치 없음"}
                        </span>
                    </button>
                ))}
            </div>
            {selected && <PhotoViewer photos={photos} photo={selected} onSelect={onSelect}/>}
        </section>
    );
}

function PhotoViewer({photos, photo, onSelect}: {
    photos: ActivityPhoto[];
    photo: ActivityPhoto;
    onSelect: (p: ActivityPhoto | null) => void;
}) {
    const idx = photos.findIndex(p => p.id === photo.id);
    const go = useCallback((d: number) => {
        const next = photos[idx + d];
        if (next) onSelect(next);
    }, [photos, idx, onSelect]);

    useEffect(() => {
        const onKey = (e: KeyboardEvent) => {
            if (e.key === "Escape") onSelect(null);
            if (e.key === "ArrowLeft") go(-1);
            if (e.key === "ArrowRight") go(1);
        };
        window.addEventListener("keydown", onKey);
        return () => window.removeEventListener("keydown", onKey);
    }, [go, onSelect]);

    const where = photo.locationSource === "EXIF_TIME" ? "촬영 시각 기준 위치"
        : photo.locationSource === "ORDER_ESTIMATE" ? "앞뒤 사진으로 추정한 위치"
            : "위치 정보 없음";

    return (
        <div className="ap-viewer" onClick={() => onSelect(null)} role="dialog" aria-modal="true">
            <div className="ap-viewer-body" onClick={e => e.stopPropagation()}>
                <img src={photo.webUrl} alt={photo.caption ?? ""}/>
                <div className="ap-viewer-info">
                    <div className="ap-viewer-caption">{photo.caption || " "}</div>
                    <div className="ap-viewer-meta">
                        {photo.takenAt && <span>🕒 {photo.takenAt.slice(0, 16).replace("T", " ")}</span>}
                        {photo.distanceKm != null && <span>🚴 {photo.distanceKm}km 지점</span>}
                        <span className={photo.locationSource === "ORDER_ESTIMATE" ? "ap-est" : ""}>📍 {where}</span>
                        <span>{idx + 1} / {photos.length}</span>
                        <a href={photo.originalUrl} target="_blank" rel="noreferrer">원본 ↗</a>
                        {photo.blogPostUrl && <a href={photo.blogPostUrl} target="_blank" rel="noreferrer">블로그 글 ↗</a>}
                    </div>
                </div>
                <button type="button" className="ap-nav ap-prev" disabled={idx <= 0} onClick={() => go(-1)} aria-label="이전">‹</button>
                <button type="button" className="ap-nav ap-next" disabled={idx >= photos.length - 1} onClick={() => go(1)} aria-label="다음">›</button>
                <button type="button" className="ap-close" onClick={() => onSelect(null)} aria-label="닫기">×</button>
            </div>
        </div>
    );
}

const CSS = `
.ap-marker{width:34px;height:34px;border-radius:50%;border:2px solid #f8fafc;background:#0f172a center/cover no-repeat;
  box-shadow:0 2px 6px rgba(0,0,0,.5);cursor:pointer;transition:transform .15s,border-color .15s}
.ap-marker:hover{transform:scale(1.35);z-index:2}
.ap-marker--estimate{border-style:dashed;border-color:#94a3b8;opacity:.85}
.ap-marker--active{transform:scale(1.6);border-color:#f59e0b;z-index:3}
.ap-card{margin-bottom:16px;background:#1e293b;border:1px solid #334155;border-radius:12px;padding:12px 14px}
.ap-head{display:flex;flex-wrap:wrap;align-items:baseline;gap:6px 12px;margin-bottom:10px}
.ap-head h3{margin:0;font-size:14px;font-weight:600;color:#f1f5f9}
.ap-count{color:#94a3b8;font-weight:400;margin-left:4px}
.ap-meta{font-size:12px;color:#94a3b8}
.ap-post{font-size:12px;color:#93c5fd;text-decoration:none}
.ap-post:hover{text-decoration:underline}
.ap-strip{display:flex;gap:8px;overflow-x:auto;padding-bottom:4px;scroll-snap-type:x proximity}
.ap-thumb{position:relative;flex:0 0 auto;width:96px;height:96px;padding:0;border:2px solid transparent;border-radius:8px;
  overflow:hidden;background:#0f172a;cursor:pointer;scroll-snap-align:center}
.ap-thumb img{width:100%;height:100%;object-fit:cover;display:block}
.ap-thumb--active{border-color:#f59e0b}
.ap-badge{position:absolute;left:4px;bottom:4px;font-size:10px;padding:1px 5px;border-radius:4px;background:rgba(15,23,42,.8);color:#e2e8f0}
.ap-badge--none{color:#94a3b8}
.ap-viewer{position:fixed;inset:0;z-index:1000;background:rgba(2,6,23,.88);display:flex;align-items:center;justify-content:center;padding:16px}
.ap-viewer-body{position:relative;max-width:min(1100px,100%);max-height:100%;display:flex;flex-direction:column;background:#0f172a;
  border:1px solid #334155;border-radius:12px;overflow:hidden}
.ap-viewer-body>img{max-width:100%;max-height:calc(100vh - 160px);object-fit:contain;background:#020617}
.ap-viewer-info{padding:10px 14px}
.ap-viewer-caption{color:#f1f5f9;font-size:14px;margin-bottom:6px}
.ap-viewer-meta{display:flex;flex-wrap:wrap;gap:4px 14px;font-size:12px;color:#94a3b8}
.ap-viewer-meta a{color:#93c5fd;text-decoration:none}
.ap-est{color:#fbbf24}
.ap-nav,.ap-close{position:absolute;border:0;background:rgba(15,23,42,.7);color:#f8fafc;cursor:pointer;border-radius:50%;
  width:40px;height:40px;font-size:24px;line-height:40px;padding:0}
.ap-nav{top:calc(50% - 60px)}
.ap-nav:disabled{opacity:.25;cursor:default}
.ap-prev{left:10px}.ap-next{right:10px}
.ap-close{top:10px;right:10px;font-size:22px}
@media (max-width:640px){.ap-thumb{width:76px;height:76px}.ap-nav{width:34px;height:34px;line-height:34px}}
`;
