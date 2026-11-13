#!/bin/bash
# 서버에서 실행되는 스크립트 (deploy.sh 가 ssh 로 stdin 전달: ssh host 'bash -s -- dev IP' < remote.sh)
# 인자: $1 = dev | prod,  $2 = 서버 IP
#
# 하는 일: 해당 환경 디렉터리의 Dockerfile/JAR/static 으로 이미지를 빌드하고 컨테이너를 교체한다.
# dev 는 별도 DB(*_dev 스키마, 계정 lm_dev — prod 스키마 권한 없음)를 쓴다. NAS 는 공유.
#   dev DB 를 prod 최신 데이터로 다시 채우려면: bash deploy/refresh-dev-db.sh
# 그 외 차이: 포트, 스케줄러/슬랙 off, 메모리 제한.
# 헬스체크에 실패하면 직전 이미지(:prev)로 자동 롤백한다.
set -euo pipefail

TARGET="${1:?usage: dev|prod}"
SERVER_IP="${2:?server ip}"
ENV_FILE=/mnt/200gb/project/data_pipeline/.env
DEV_DB_ENV=/mnt/200gb/apps-dev/.env.db   # dev 전용 DB_USER/DB_PASSWORD/SPRING_DATASOURCE_*_URL (600)

case "$TARGET" in
    dev)  DIR=/mnt/200gb/apps-dev; NAME=lifemetrics-dev; IMAGE=lifemetrics-dev; PORT=8081 ;;
    prod) DIR=/mnt/200gb/apps;     NAME=lifemetrics;     IMAGE=lifemetrics;     PORT=8080 ;;
    *)    echo "unknown target: $TARGET"; exit 1 ;;
esac

run_container() {   # $1 = 이미지 태그
    # DB_URL 은 파이썬 파이프라인용(비밀번호가 URL 에 평문으로 들어 있음). 백엔드는 안 쓰므로 컨테이너에 넣지 않는다.
    # (docker 가 /dev/fd 를 못 열어서 프로세스 치환 대신 600 임시 파일 사용)
    local env_tmp
    env_tmp=$(mktemp)
    chmod 600 "$env_tmp"
    if [ "$TARGET" = dev ]; then
        # dev 에는 prod DB 계정을 아예 넣지 않고 dev 전용 계정·URL 로 바꿔 넣는다.
        [ -f "$DEV_DB_ENV" ] || { echo "❌ $DEV_DB_ENV 없음 (dev DB 접속 정보)"; rm -f "$env_tmp"; return 1; }
        { grep -v '^DB_' "$ENV_FILE"; grep -v '^#' "$DEV_DB_ENV"; } > "$env_tmp"
    else
        grep -v '^DB_URL=' "$ENV_FILE" > "$env_tmp"
    fi
    # dev 는 DB 가 분리돼 있지만 NAS 는 공유한다(dev 에서 올린 파일이 prod 폴더에도 생김).
    # dev 는 "중복 실행되면 안 되는 부수효과"도 끈다: 자동 동기화 스케줄러, 슬랙 알림.
    local args=(
        -d --name "$NAME" -p "$PORT:8080"
        --add-host=host.docker.internal:host-gateway
        --env-file "$env_tmp"
        -e SPRING_PROFILES_ACTIVE=prod
        -v "$DIR/static:/app/static:ro"
        -v /mnt/200gb/NAS/inbody/raw:/mnt/200gb/NAS/inbody/raw
        -v /mnt/200gb/NAS/inbody/processed:/mnt/200gb/NAS/inbody/processed:ro   # 기록지에서 체중조절 값 읽기(InbodyReExtractService)
        -v /mnt/200gb/NAS/career-media:/mnt/200gb/NAS/career-media
        -v /mnt/200gb/NAS/handler-settlement:/mnt/200gb/NAS/handler-settlement   # 핸들러 월별 정산서 PDF
        -v /data/home/tho881/project/NAS/brevet:/data/home/tho881/project/NAS/brevet
        -v /mnt/200gb/NAS/data/permanent:/mnt/200gb/NAS/data/permanent
        -v /mnt/200gb/NAS/data/lotto:/mnt/200gb/NAS/data/lotto
        --restart unless-stopped
    )

    if [ "$TARGET" = dev ]; then
        args+=(
            -e APP_SCHEDULING_ENABLED=false          # 로또/연금복권 자동 동기화 끔(운영과 중복 실행 방지)
            -e SLACK_WEBHOOK_URL=                    # 슬랙 알림 끔
            -e "APP_BASE_URL=http://$SERVER_IP:$PORT"
            -e JAVA_OPTS=-Xmx512m
            --memory 900m
        )
    fi

    local rc=0
    docker run "${args[@]}" "$1" >/dev/null || rc=$?
    rm -f "$env_tmp"
    return $rc
}

wait_healthy() {    # 최대 120초 동안 / 가 200 을 돌려줄 때까지 대기
    local code
    for _ in $(seq 1 24); do
        sleep 5
        code=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$PORT/" || true)
        [ "$code" = 200 ] && return 0
    done
    return 1
}

cd "$DIR"
[ -f Dockerfile ]        || { echo "❌ $DIR/Dockerfile 없음"; exit 1; }
[ -f lifemetrics.jar ]   || { echo "❌ $DIR/lifemetrics.jar 없음"; exit 1; }
[ -f static/index.html ] || { echo "❌ $DIR/static/index.html 없음 (프론트 빌드가 올라가지 않음)"; exit 1; }

echo "🔨 이미지 빌드: $IMAGE:new"
docker build -q -t "$IMAGE:new" .

echo "🚀 컨테이너 교체: $NAME (:$PORT)"
docker rm -f "$NAME" >/dev/null 2>&1 || true
run_container "$IMAGE:new"

if wait_healthy; then
    # 검증이 끝난 뒤에만 태그를 옮긴다 → :latest 는 항상 "마지막으로 정상 기동한 이미지", :prev 는 그 직전 버전
    docker tag "$IMAGE:latest" "$IMAGE:prev" 2>/dev/null || true
    docker tag "$IMAGE:new" "$IMAGE:latest"
    echo "✅ $NAME 정상 (http://localhost:$PORT/ → 200)"
    docker ps --filter "name=^${NAME}$" --format '   {{.Names}}  {{.Status}}  {{.Ports}}'
    exit 0
fi

echo "❌ 헬스체크 실패 — 최근 로그:"
docker logs --tail 30 "$NAME" 2>&1 | cut -c1-200 || true
if docker image inspect "$IMAGE:latest" >/dev/null 2>&1; then
    echo "↩️  마지막 정상 이미지($IMAGE:latest)로 롤백"
    docker rm -f "$NAME" >/dev/null 2>&1 || true
    run_container "$IMAGE:latest"
    wait_healthy && echo "↩️  롤백 완료" || echo "⚠️  롤백 후에도 비정상 — 수동 확인 필요"
else
    echo "⚠️  롤백할 이미지가 없음(첫 배포). $NAME 은 비정상 상태로 남겨 둡니다 — docker logs $NAME 확인"
fi
exit 1
