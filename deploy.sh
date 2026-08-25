#!/bin/bash

# 설정
SERVER="34.172.162.148"
USER="jihoon"
REMOTE_PATH="/mnt/200gb/apps"
SSH_KEY="/Users/jihoon/.ssh/riding_key_nopass"

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
        echo "⚡ Backend만 빌드"
        ;;
    2)
        BUILD_BACKEND=false
        BUILD_FRONTEND=true
        echo "⚡ Frontend만 빌드"
        ;;
    3)
        BUILD_BACKEND=true
        BUILD_FRONTEND=true
        echo "⚡ 전체 빌드"
        ;;
esac

if [ "$BUILD_BACKEND" = true ]; then
    echo "[1/5] Backend 빌드 중..."
    cd backend
    ./gradlew bootJar --no-daemon
    cd ..
else
    echo "[1/5] Backend 빌드 스킵"
fi

if [ "$BUILD_FRONTEND" = true ]; then
    echo "[2/5] Frontend 빌드 중..."
    cd frontend
    npm run build
    cd ..
else
    echo "[2/5] Frontend 빌드 스킵"
fi

# 3. 파일 전송
echo "[3/5] 파일 전송 중..."
scp -i ${SSH_KEY} backend/build/libs/lifemetrics.jar ${USER}@${SERVER}:${REMOTE_PATH}/lifemetrics.jar
rsync -avz --progress -e "ssh -i ${SSH_KEY}" frontend/dist/ ${USER}@${SERVER}:${REMOTE_PATH}/static/
scp -i ${SSH_KEY} backend/src/main/resources/.env ${USER}@${SERVER}:${REMOTE_PATH}/.env

# 4. Docker 재시작
echo "[4/5] Docker 재시작 중..."

ssh -i ${SSH_KEY} ${USER}@${SERVER} 'cd /mnt/200gb/apps && \
  docker build -t lifemetrics:new . && \
  docker stop lifemetrics 2>/dev/null; \
  docker rm lifemetrics 2>/dev/null; \
  docker tag lifemetrics:latest lifemetrics:prev 2>/dev/null; \
  docker tag lifemetrics:new lifemetrics:latest && \
  docker run -d \
    --name lifemetrics \
    -p 8080:8080 \
    --add-host=host.docker.internal:host-gateway \
    --env-file /mnt/200gb/apps/.env \
    -v /mnt/200gb/NAS/inbody/raw:/mnt/200gb/NAS/inbody/raw \
    -v /mnt/200gb/NAS/career-media:/mnt/200gb/NAS/career-media \
    -v /data/home/tho881/project/NAS/brevet:/data/home/tho881/project/NAS/brevet \
    -e SPRING_PROFILES_ACTIVE=prod \
    --restart unless-stopped \
    lifemetrics:latest'


# 5. 상태 확인
echo "[5/5] 상태 확인..."
ssh -i ${SSH_KEY} ${USER}@${SERVER} 'docker ps | grep lifemetrics'

echo "=== 배포 완료! ==="
echo "http://${SERVER}:8080"
