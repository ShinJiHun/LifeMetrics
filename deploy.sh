#!/bin/bash
# LifeMetrics 배포 스크립트 (개발 서버 → 운영 승격 방식)
#
#   ./deploy.sh dev [1|2|3|-n]   빌드 후 개발 서버에 배포 (http://SERVER:8081)
#                                  1: Backend만   2: Frontend만   3: 전체(기본)   -n: 빌드 없이 재기동
#   ./deploy.sh prod [--yes]     개발 서버에 올라가 있는 JAR+static 을 운영(ride.tho881.me)으로 승격
#   ./deploy.sh status           두 서버의 컨테이너/응답 상태 확인
#
# 운영은 직접 빌드하지 않는다. dev 에서 확인한 바로 그 파일이 올라가므로 "테스트한 것 = 배포되는 것".
# 프론트는 이미지에 넣지 않고 /app/static 볼륨으로 마운트한다(deploy/Dockerfile 참고).
set -euo pipefail

SERVER="34.172.162.148"
USER="jihoon"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/riding_key_nopass}"
PROD_DIR="/mnt/200gb/apps"
DEV_DIR="/mnt/200gb/apps-dev"
DEV_PORT=8081

REPO_PATH="$(cd "$(dirname "$0")" && pwd)"
SSH=(ssh -i "$SSH_KEY" "$USER@$SERVER")
SSH_E="ssh -i $SSH_KEY"

usage() { sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit 1; }

remote() { "${SSH[@]}" "bash -s -- $1 $SERVER" < "$REPO_PATH/deploy/remote.sh"; }

deploy_dev() {
    local build_backend=true build_frontend=true
    case "${1:-3}" in
        1)          build_frontend=false; echo "⚡ Backend만" ;;
        2)          build_backend=false;  echo "⚡ Frontend만" ;;
        3)          echo "⚡ 전체" ;;
        -n|--no-build) build_backend=false; build_frontend=false; echo "⚡ 빌드 스킵(재기동만)" ;;
        *)          usage ;;
    esac

    "${SSH[@]}" "mkdir -p $DEV_DIR/static"

    if $build_backend; then
        echo "[1/4] Backend 빌드..."
        (cd "$REPO_PATH/backend" && ./gradlew clean bootJar --no-daemon)
        echo "[2/4] JAR 전송..."
        scp -i "$SSH_KEY" "$REPO_PATH/backend/build/libs/lifemetrics.jar" "$USER@$SERVER:$DEV_DIR/lifemetrics.jar"
    else
        echo "[1-2/4] Backend 스킵"
    fi

    if $build_frontend; then
        echo "[3/4] Frontend 빌드 및 전송..."
        (cd "$REPO_PATH/frontend" && rm -rf dist && npm run build)
        rsync -az --delete --progress -e "$SSH_E" "$REPO_PATH/frontend/dist/" "$USER@$SERVER:$DEV_DIR/static/"
    else
        echo "[3/4] Frontend 스킵"
    fi

    scp -q -i "$SSH_KEY" "$REPO_PATH/deploy/Dockerfile" "$USER@$SERVER:$DEV_DIR/Dockerfile"

    echo "[4/4] 개발 서버 재기동..."
    remote dev
    echo "🔗 http://$SERVER:$DEV_PORT"
}

promote_prod() {
    echo "⚠️  운영(https://ride.tho881.me)에 배포합니다. 개발 서버의 현재 산출물이 그대로 올라갑니다:"
    "${SSH[@]}" "ls -l --time-style=long-iso $DEV_DIR/lifemetrics.jar $DEV_DIR/static/index.html" | awk '{print "   " $6, $7, $5"B", $8}'

    if [ "${1:-}" != "--yes" ]; then
        read -r -p "계속하려면 'prod' 를 입력: " answer
        [ "$answer" = "prod" ] || { echo "취소"; exit 1; }
    fi

    "${SSH[@]}" "set -e
        [ -f $DEV_DIR/lifemetrics.jar ] && [ -f $DEV_DIR/static/index.html ] || { echo '❌ dev 산출물이 없음 — 먼저 ./deploy.sh dev'; exit 1; }
        mkdir -p $PROD_DIR/static
        # 롤백용 백업(JAR + static 직전 버전)
        [ -f $PROD_DIR/lifemetrics.jar ] && cp -p $PROD_DIR/lifemetrics.jar $PROD_DIR/lifemetrics.jar.prev
        rsync -a --delete $PROD_DIR/static/ $PROD_DIR/static.prev/
        # 승격
        cp $DEV_DIR/lifemetrics.jar $PROD_DIR/lifemetrics.jar
        cp $DEV_DIR/Dockerfile $PROD_DIR/Dockerfile
        rsync -a --delete $DEV_DIR/static/ $PROD_DIR/static/
        echo '✓ 산출물 승격 완료'"

    remote prod
    echo "🔗 https://ride.tho881.me"
}

status() {
    "${SSH[@]}" "docker ps --filter name=lifemetrics --format '{{.Names}}\t{{.Status}}\t{{.Ports}}'"
    for target in "http://$SERVER:$DEV_PORT/" "https://ride.tho881.me/"; do
        printf '%s -> ' "$target"
        curl -s -o /dev/null --max-time 10 -w '%{http_code}\n' "$target" || echo "연결 실패"
    done
}

case "${1:-}" in
    dev)    deploy_dev "${2:-3}" ;;
    prod)   promote_prod "${2:-}" ;;
    status) status ;;
    *)      usage ;;
esac
