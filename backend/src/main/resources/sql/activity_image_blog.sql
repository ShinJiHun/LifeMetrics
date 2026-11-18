-- 활동 사진: 블로그(NAS persona/blog) 사진을 라이딩 지도에 연결 (2026-11-18)
-- 기존 activity_image(비어 있던 테이블)에 위치·시각·블로그 출처 컬럼을 더한다.
-- image_path / thumb_path 는 블로그 보관 폴더(blog.archive.path) 기준 상대 경로: "athlete/029_.../images/02.jpg"
-- location_source: EXIF_TIME(촬영 시각 → 그 시각의 라이딩 좌표), ORDER_ESTIMATE(앞뒤 사진 시각 사이로 추정), NONE(위치 없음)
-- 채우는 쪽: data_pipeline/persona/blog_photo_link.py (다시 실행하면 같은 글의 BLOG 행을 지우고 새로 넣음)
ALTER TABLE activity_image
    ADD COLUMN IF NOT EXISTS source          VARCHAR(20)  NULL COMMENT 'BLOG 등 출처',
    ADD COLUMN IF NOT EXISTS sort_order      INT          NULL COMMENT '글 안 사진 순서',
    ADD COLUMN IF NOT EXISTS thumb_path      VARCHAR(500) NULL,
    ADD COLUMN IF NOT EXISTS taken_at        DATETIME     NULL COMMENT '촬영 시각(KST, EXIF 또는 추정)',
    ADD COLUMN IF NOT EXISTS lat             DOUBLE       NULL,
    ADD COLUMN IF NOT EXISTS lon             DOUBLE       NULL,
    ADD COLUMN IF NOT EXISTS distance_m      DOUBLE       NULL COMMENT '그 시각까지 달린 거리',
    ADD COLUMN IF NOT EXISTS location_source VARCHAR(20)  NULL,
    ADD COLUMN IF NOT EXISTS caption         VARCHAR(500) NULL COMMENT '사진 근처 블로그 문장',
    ADD COLUMN IF NOT EXISTS width           INT          NULL,
    ADD COLUMN IF NOT EXISTS height          INT          NULL,
    ADD COLUMN IF NOT EXISTS blog_post_no    INT          NULL,
    ADD COLUMN IF NOT EXISTS blog_post_title VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS blog_post_url   VARCHAR(255) NULL;

CREATE INDEX IF NOT EXISTS idx_activity_image_activity_order ON activity_image (activity_core_id, sort_order);
