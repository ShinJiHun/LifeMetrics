#!/bin/bash

# 설정
SERVER="34.172.162.148"
USER="jihoon"
REMOTE_PATH="/mnt/200gb/apps"
SSH_KEY="/Users/jihoon/.ssh/riding_key_nopass"
REPO_PATH="/Users/jihoon/Desktop/depolyment/project/LifeMetrics"

# 옵션 파싱
BUILD_BACKEND=true
BUILD_FRONTEND=true

case "$1" in
    --no-build|-n)
        BUILD_BACKEND=false
        BUILD_FRONTEND=false
        echo "⚡ 빌드 스킵 모드"
        ;;
    1)
        BUILD_BACKEND=true
        BUILD_FRONTEND=false
        echo "⚡ Backend만 빌드 (서버에서)"
        ;;
    2)
        BUILD_BACKEND=false
        BUILD_FRONTEND=true
        echo "⚡ Frontend만 빌드 (로컬에서)"
        ;;
    3)
        BUILD_BACKEND=true
        BUILD_FRONTEND=true
        echo "⚡ 전체 빌드 (서버: Backend, 로컬: Frontend)"
        ;;
esac

# Backend: 서버에서 빌드 (로컬 머신에 네트워크 제한이 있을 때)
if [ "$BUILD_BACKEND" = true ]; then
    echo "[1/5] Backend 소스 전송 및 서버에서 빌드 중..."
    # backend 소스 코드를 서버로 전송
    rsync -avz --progress -e "ssh -i ${SSH_KEY}" \
        --exclude='build' \
        --exclude='.gradle' \
        "${REPO_PATH}/backend/" \
        ${USER}@${SERVER}:${REMOTE_PATH}/backend/

    # 서버에서 빌드 실행
    ssh -i ${SSH_KEY} ${USER}@${SERVER} 'cd /mnt/200gb/apps/backend && \
        gradle clean bootJar --no-daemon && \
        echo "✓ Backend 빌드 완료"'
else
    echo "[1/5] Backend 빌드 스킵"
fi

if [ "$BUILD_FRONTEND" = true ]; then
    echo "[2/5] Frontend 클린 빌드 중..."
    cd "${REPO_PATH}/frontend"
    rm -rf dist
    npm run build
    cd "${REPO_PATH}"

    # frontend를 서버로 전송
    echo "[2/5] Frontend 파일 서버로 전송 중..."
    rsync -avz --progress -e "ssh -i ${SSH_KEY}" \
        frontend/dist/ \
        ${USER}@${SERVER}:${REMOTE_PATH}/static/
else
    echo "[2/5] Frontend 빌드 스킵"
fi

# 3. JAR 파일 서버 위치 확인 및 Docker 재시작
echo "[3/5] Docker 재시작 중..."

ssh -i ${SSH_KEY} ${USER}@${SERVER} 'cd /mnt/200gb/apps && \
  # JAR 파일이 존재하는지 확인
  if [ ! -f backend/build/libs/lifemetrics.jar ]; then
    echo "❌ 오류: lifemetrics.jar을 찾을 수 없습니다"
    exit 1
  fi && \

  # JAR을 배포 위치로 복사
  cp backend/build/libs/lifemetrics.jar ./lifemetrics.jar && \
  echo "✓ JAR 파일 준비 완료" && \

  { [ -f Dockerfile.bak ] || cp Dockerfile Dockerfile.bak; } && \
  sed -i "s/^COPY static \/app\/static/#COPY static \/app\/static/" Dockerfile && \
  docker build -t lifemetrics:new . && \
  docker stop lifemetrics 2>/dev/null; \
  docker rm lifemetrics 2>/dev/null; \
  docker tag lifemetrics:latest lifemetrics:prev 2>/dev/null; \
  docker tag lifemetrics:new lifemetrics:latest && \
  docker run -d \
    --name lifemetrics \
    -p 8080:8080 \
    --add-host=host.docker.internal:host-gateway \
    --env-file /mnt/200gb/project/data_pipeline/.env \
    -v /mnt/200gb/NAS/inbody/raw:/mnt/200gb/NAS/inbody/raw \
    -v /mnt/200gb/NAS/career-media:/mnt/200gb/NAS/career-media \
    -v /data/home/tho881/project/NAS/brevet:/data/home/tho881/project/NAS/brevet \
    -v /mnt/200gb/NAS/data/permanent:/mnt/200gb/NAS/data/permanent \
    -v /mnt/200gb/NAS/data/lotto:/mnt/200gb/NAS/data/lotto \
    -e SPRING_PROFILES_ACTIVE=prod \
    --restart unless-stopped \
    lifemetrics:latest && \
  echo "✓ Docker 컨테이너 시작 완료"'

# 4. 상태 확인
echo "[4/5] 상태 확인..."
ssh -i ${SSH_KEY} ${USER}@${SERVER} 'docker ps | grep lifemetrics'

echo "=== 배포 완료! ==="
echo "http://${SERVER}:8080"
echo ""
echo "📝 노트:"
echo "- Backend는 서버에서 빌드되었습니다 (로컬 네트워크 제한 우회)"
echo "- Frontend는 로컬에서 빌드되고 static 파일로 서버로 전송됩니다"
