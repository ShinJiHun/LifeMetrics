@echo off
chcp 65001 > nul
setlocal enabledelayedexpansion

:: 설정
set SERVER=34.172.162.148
set USER=jihoon
set REMOTE_PATH=/mnt/200gb/apps
set SSH_KEY=D:\development\key\gcp-win

:: 옵션 파싱
set BUILD=true
if "%1"=="--no-build" set BUILD=false
if "%1"=="-n" set BUILD=false

if "%BUILD%"=="false" (
    echo ⚡ 빌드 스킵 모드
    goto TRANSFER
)

:: 1. Backend 빌드
echo [1/5] Backend 빌드 중...
cd backend
call gradlew.bat bootJar --no-daemon
if errorlevel 1 (
    echo ❌ Backend 빌드 실패
    exit /b 1
)
cd ..

:: 2. Frontend 빌드
echo [2/5] Frontend 빌드 중...
cd frontend
call npm run build
if errorlevel 1 (
    echo ❌ Frontend 빌드 실패
    exit /b 1
)
cd ..

:TRANSFER
:: 3. 파일 전송
echo [3/5] 파일 전송 중...
scp -i %SSH_KEY% backend\build\libs\lifemetrics.jar %USER%@%SERVER%:%REMOTE_PATH%/lifemetrics.jar
if errorlevel 1 (
    echo ❌ JAR 전송 실패
    exit /b 1
)

scp -i %SSH_KEY% -r frontend\dist\* %USER%@%SERVER%:%REMOTE_PATH%/static/
if errorlevel 1 (
    echo ❌ Frontend 전송 실패
    exit /b 1
)

:: 4. Docker 재시작
echo [4/5] Docker 재시작 중...
ssh -i %SSH_KEY% %USER%@%SERVER% "cd /mnt/200gb/apps && docker stop lifemetrics 2>/dev/null; docker rm lifemetrics 2>/dev/null; docker build -t lifemetrics . && docker run -d --name lifemetrics -p 8080:8080 --env-file /mnt/200gb/apps/.env -v /mnt/200gb/NAS/inbody/raw:/mnt/200gb/NAS/inbody/raw -v /data/home/tho881/project/NAS/brevet:/data/home/tho881/project/NAS/brevet -e SPRING_PROFILES_ACTIVE=prod --restart unless-stopped lifemetrics"
if errorlevel 1 (
    echo ❌ Docker 재시작 실패
    exit /b 1
)

:: 5. 상태 확인
echo [5/5] 상태 확인...
ssh -i %SSH_KEY% %USER%@%SERVER% "docker ps | grep lifemetrics"

echo.
echo === 배포 완료! ===
echo http://%SERVER%:8080

endlocal
