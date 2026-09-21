#!/bin/bash
# 서버에서 실행되는 스크립트 (deploy.sh 가 ssh 로 stdin 전달: ssh host 'bash -s -- dev IP' < remote.sh)
# 인자: $1 = dev | prod,  $2 = 서버 IP
#
# 하는 일: 해당 환경 디렉터리의 Dockerfile/JAR/static 으로 이미지를 빌드하고 컨테이너를 교체한다.
# 헬스체크에 실패하면 직전 이미지(:prev)로 자동 롤백한다.
set -euo pipefail

TARGET="${1:?usage: dev|prod}"
SERVER_IP="${2:?server ip}"
ENV_FILE=/mnt/200gb/project/data_pipeline/.env
DB_HOST=172.17.0.1

case "$TARGET" in
    dev)  DIR=/mnt/200gb/apps-dev; NAME=lifemetrics-dev; IMAGE=lifemetrics-dev; PORT=8081 ;;
    prod) DIR=/mnt/200gb/apps;     NAME=lifemetrics;     IMAGE=lifemetrics;     PORT=8080 ;;
    *)    echo "unknown target: $TARGET"; exit 1 ;;
esac

run_container() {   # $1 = 이미지 태그
    local args=(
        -d --name "$NAME" -p "$PORT:8080"
        --add-host=host.docker.internal:host-gateway
        --env-file "$ENV_FILE"
        -e SPRING_PROFILES_ACTIVE=prod
        -v "$DIR/static:/app/static:ro"
        --restart unless-stopped
    )

    if [ "$TARGET" = dev ]; then
        # 개발 서버: 운영 DB/NAS/알림을 건드리지 않도록 전부 분리하거나 읽기 전용으로 둔다.
        mkdir -p "$DIR/nas/lotto"
        args+=(
            -e "SPRING_DATASOURCE_RIDING_URL=jdbc:mariadb://$DB_HOST:3306/riding_db_dev"
            -e "SPRING_DATASOURCE_JOURNAL_URL=jdbc:mariadb://$DB_HOST:3306/journal_db_dev"
            -e "SPRING_DATASOURCE_LOTTO_URL=jdbc:mariadb://$DB_HOST:3306/lotto_db_dev"
            -e APP_SCHEDULING_ENABLED=false          # 로또/연금복권 자동 동기화 끔
            -e SLACK_WEBHOOK_URL=                    # 슬랙 알림 끔
            -e "APP_BASE_URL=http://$SERVER_IP:$PORT"
            -e JAVA_OPTS=-Xmx512m
            --memory 900m
            -v "$DIR/nas/lotto:/mnt/200gb/NAS/data/lotto"                                  # 쓰기: dev 전용 폴더
            -v /mnt/200gb/NAS/inbody/raw:/mnt/200gb/NAS/inbody/raw:ro                       # 운영 NAS 는 읽기 전용
            -v /mnt/200gb/NAS/career-media:/mnt/200gb/NAS/career-media:ro
            -v /data/home/tho881/project/NAS/brevet:/data/home/tho881/project/NAS/brevet:ro
            -v /mnt/200gb/NAS/data/permanent:/mnt/200gb/NAS/data/permanent:ro
        )
    else
        args+=(
            -v /mnt/200gb/NAS/inbody/raw:/mnt/200gb/NAS/inbody/raw
            -v /mnt/200gb/NAS/career-media:/mnt/200gb/NAS/career-media
            -v /data/home/tho881/project/NAS/brevet:/data/home/tho881/project/NAS/brevet
            -v /mnt/200gb/NAS/data/permanent:/mnt/200gb/NAS/data/permanent
            -v /mnt/200gb/NAS/data/lotto:/mnt/200gb/NAS/data/lotto
        )
    fi

    docker run "${args[@]}" "$1" >/dev/null
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
