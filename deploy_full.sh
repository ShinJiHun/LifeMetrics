#!/bin/bash

# ===================================================
# Lifemetrics 전체 배포 스크립트
# Backend 빌드 → Frontend 빌드 → GCP 전송 → Docker 재시작
# ===================================================

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 설정
SERVER="34.172.162.148"
USER="jihoon"
REMOTE_PATH="/mnt/200gb/apps"
SSH_KEY="/Users/jihoon/.ssh/riding_key_nopass"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}🚀 Lifemetrics 전체 배포 시작${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${YELLOW}서버:${NC} $SERVER"
echo -e "${YELLOW}사용자:${NC} $USER"
echo -e "${YELLOW}경로:${NC} $REMOTE_PATH"
echo ""

# ===================================================
# [1/5] Backend 클린 빌드
# ===================================================
echo -e "${BLUE}[1/5]${NC} ${YELLOW}Backend 클린 빌드 중...${NC}"
cd backend
echo "📦 Gradle 빌드 중..."
./gradlew clean bootJar --no-daemon
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Backend 빌드 성공${NC}"
else
    echo -e "${RED}❌ Backend 빌드 실패${NC}"
    exit 1
fi
cd ..
echo ""

# ===================================================
# [2/5] Frontend 클린 빌드
# ===================================================
echo -e "${BLUE}[2/5]${NC} ${YELLOW}Frontend 클린 빌드 중...${NC}"
cd frontend
echo "🗑️  dist 폴더 삭제 중..."
rm -rf dist
echo "📦 npm 빌드 중..."
npm run build
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Frontend 빌드 성공${NC}"
else
    echo -e "${RED}❌ Frontend 빌드 실패${NC}"
    exit 1
fi
cd ..
echo ""

# ===================================================
# [3/5] Backend JAR 파일 GCP로 전송
# ===================================================
echo -e "${BLUE}[3/5]${NC} ${YELLOW}Backend JAR 파일을 GCP로 전송 중...${NC}"
echo "📤 lifemetrics.jar 전송..."
scp -i ${SSH_KEY} backend/build/libs/lifemetrics.jar ${USER}@${SERVER}:${REMOTE_PATH}/lifemetrics.jar
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ JAR 파일 전송 성공${NC}"
else
    echo -e "${RED}❌ JAR 파일 전송 실패${NC}"
    exit 1
fi
echo ""

# ===================================================
# [4/5] Frontend Static 파일 GCP로 전송
# ===================================================
echo -e "${BLUE}[4/5]${NC} ${YELLOW}Frontend Static 파일을 GCP로 전송 중...${NC}"
echo "📤 dist/ 폴더 rsync 전송..."
rsync -avz --delete --progress \
  -e "ssh -i ${SSH_KEY}" \
  frontend/dist/ \
  ${USER}@${SERVER}:${REMOTE_PATH}/static/
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Static 파일 전송 성공${NC}"
else
    echo -e "${RED}❌ Static 파일 전송 실패${NC}"
    exit 1
fi
echo ""

# ===================================================
# [5/5] GCP 서버에서 Docker 빌드 및 재시작
# ===================================================
echo -e "${BLUE}[5/5]${NC} ${YELLOW}GCP 서버에서 Docker 빌드 및 재시작 중...${NC}"
ssh -i ${SSH_KEY} ${USER}@${SERVER} << 'REMOTE_SCRIPT'

echo "🐳 Docker 작업 시작..."

# Dockerfile 복원 (static 마운트 활성화)
cd /mnt/200gb/apps
if [ -f Dockerfile.bak ]; then
    cp Dockerfile.bak Dockerfile
    echo "📄 Dockerfile 복원"
else
    echo "⚠️  Dockerfile.bak 없음 - 기존 Dockerfile 사용"
fi

# Docker 이미지 빌드
echo "🔨 Docker 이미지 빌드 중 (lifemetrics:new)..."
docker build -t lifemetrics:new .
if [ $? -ne 0 ]; then
    echo "❌ Docker 빌드 실패"
    exit 1
fi

# 기존 컨테이너 중지 및 제거
echo "🛑 기존 컨테이너 중지..."
docker stop lifemetrics 2>/dev/null || true
docker rm lifemetrics 2>/dev/null || true

# 이전 이미지 백업
echo "💾 이전 이미지 백업 (lifemetrics:prev)..."
docker tag lifemetrics:latest lifemetrics:prev 2>/dev/null || true

# 새 이미지를 latest로 태그
echo "🏷️  새 이미지를 latest로 태그..."
docker tag lifemetrics:new lifemetrics:latest

# 새 컨테이너 시작 (static 볼륨 마운트)
echo "🚀 새 컨테이너 시작 중..."
docker run -d \
  --name lifemetrics \
  -p 8080:8080 \
  --add-host=host.docker.internal:host-gateway \
  --env-file /mnt/200gb/project/data_pipeline/.env \
  -v /mnt/200gb/apps/static:/app/static \
  -v /mnt/200gb/NAS/inbody/raw:/mnt/200gb/NAS/inbody/raw \
  -v /mnt/200gb/NAS/career-media:/mnt/200gb/NAS/career-media \
  -v /data/home/tho881/project/NAS/brevet:/data/home/tho881/project/NAS/brevet \
  -v /mnt/200gb/NAS/data/permanent:/mnt/200gb/NAS/data/permanent \
  -v /mnt/200gb/NAS/data/lotto:/mnt/200gb/NAS/data/lotto \
  -e SPRING_PROFILES_ACTIVE=prod \
  --restart unless-stopped \
  lifemetrics:latest

if [ $? -eq 0 ]; then
    echo "✅ Docker 컨테이너 시작 성공"
else
    echo "❌ Docker 컨테이너 시작 실패"
    exit 1
fi

# 상태 확인
echo ""
echo "📊 현재 Docker 상태:"
docker ps | grep lifemetrics
echo ""

REMOTE_SCRIPT

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Docker 배포 성공${NC}"
else
    echo -e "${RED}❌ Docker 배포 실패${NC}"
    exit 1
fi

# ===================================================
# 배포 완료 요약
# ===================================================
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}✅ 배포 완료!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${YELLOW}서비스 정보:${NC}"
echo "  🌐 웹사이트: http://ride.tho881.me/"
echo "  🔗 API: http://ride.tho881.me/api/*"
echo "  📍 IP: http://${SERVER}:8080"
echo ""
echo -e "${YELLOW}확인 명령어:${NC}"
echo "  curl http://ride.tho881.me/"
echo "  curl http://ride.tho881.me/api/pension/latest | jq ."
echo ""
echo -e "${YELLOW}원격 확인 (SSH):${NC}"
echo "  ssh -i ${SSH_KEY} ${USER}@${SERVER}"
echo "  docker logs -f lifemetrics"
echo ""
