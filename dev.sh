#!/bin/bash
# 로컬 개발 서버 일괄 기동: Ollama → Backend → Frontend
# Ctrl+C 한 번으로 backend/frontend 모두 종료됨 (Ollama는 brew 상주 서비스라 그대로 둠)

set -e

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="${ROOT_DIR}/.dev-logs"
mkdir -p "${LOG_DIR}"

BACKEND_PID=""
FRONTEND_PID=""

cleanup() {
    echo ""
    echo "🛑 종료 중..."
    [ -n "$BACKEND_PID" ] && kill "$BACKEND_PID" 2>/dev/null
    [ -n "$FRONTEND_PID" ] && kill "$FRONTEND_PID" 2>/dev/null
    wait 2>/dev/null
    echo "✅ backend/frontend 종료 완료 (Ollama는 계속 실행됨)"
}
trap cleanup EXIT INT TERM

# 1. Ollama 확인 (brew services로 상주 실행되는 게 보통이라 새로 띄우진 않고 대기만 함)
echo "[1/3] Ollama 확인 중..."
if curl -s -m 2 http://localhost:11434/api/tags > /dev/null 2>&1; then
    echo "✅ Ollama 이미 실행 중"
else
    echo "⚠️  Ollama가 꺼져 있음 → brew services로 기동 시도"
    brew services start ollama
    for i in $(seq 1 15); do
        curl -s -m 2 http://localhost:11434/api/tags > /dev/null 2>&1 && break
        sleep 1
    done
    if curl -s -m 2 http://localhost:11434/api/tags > /dev/null 2>&1; then
        echo "✅ Ollama 기동 완료"
    else
        echo "❌ Ollama 기동 실패 - 로그 확인 필요 (brew services info ollama)"
        exit 1
    fi
fi

# 2. Backend
echo "[2/3] Backend 기동 중... (로그: ${LOG_DIR}/backend.log)"
(cd "${ROOT_DIR}/backend" && ./gradlew bootRun --console=plain) > "${LOG_DIR}/backend.log" 2>&1 &
BACKEND_PID=$!

echo "⏳ Backend 준비될 때까지 대기 중..."
for i in $(seq 1 60); do
    curl -s -m 2 http://localhost:8080/actuator/health > /dev/null 2>&1 && break
    # 프로세스가 죽었으면 바로 중단
    if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
        echo "❌ Backend 프로세스가 중간에 종료됨 - ${LOG_DIR}/backend.log 확인"
        exit 1
    fi
    sleep 2
done

if curl -s -m 2 http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "✅ Backend 준비 완료"
else
    echo "❌ Backend 기동 타임아웃 - ${LOG_DIR}/backend.log 확인"
    exit 1
fi

# 3. Frontend
echo "[3/3] Frontend 기동 중... (로그: ${LOG_DIR}/frontend.log)"
(cd "${ROOT_DIR}/frontend" && npm run dev) > "${LOG_DIR}/frontend.log" 2>&1 &
FRONTEND_PID=$!

echo ""
echo "실행 중 (Ctrl+C로 종료):"
echo "  backend  PID=${BACKEND_PID}  tail -f ${LOG_DIR}/backend.log"
echo "  frontend PID=${FRONTEND_PID}  tail -f ${LOG_DIR}/frontend.log"
echo ""

tail -f "${LOG_DIR}/backend.log" "${LOG_DIR}/frontend.log" &
TAIL_PID=$!
trap "kill $TAIL_PID 2>/dev/null; cleanup" EXIT INT TERM

wait "$BACKEND_PID" "$FRONTEND_PID"
