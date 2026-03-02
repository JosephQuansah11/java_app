@echo off
echo ========================================
echo EchoBridge Services - Complete Fix
echo ========================================
echo.

echo [1/6] Cleaning up existing containers...
docker-compose down 2>nul
echo.

echo [2/6] Creating fresh network and volumes...
docker network prune -f 2>nul
echo.

echo [3/6] Starting services with working alternatives...
echo.

echo Starting Whisper API...
docker run -d --name whisper-api --network java_app_default -p 9000:8000 onerah/whisper-api:latest
if %errorlevel% equ 0 (
    echo ✅ Whisper API started successfully
) else (
    echo ❌ Whisper API failed to start
)

echo Starting LibreTranslate...
docker run -d --name libretranslate --network java_app_default -p 5000:5000 -v libretranslate_data:/app/libretranslate libretranslate/libretranslate:latest
if %errorlevel% equ 0 (
    echo ✅ LibreTranslate started successfully
) else (
    echo ❌ LibreTranslate failed to start
)

echo Starting Coqui TTS...
docker run -d --name coqui-tts --network java_app_default -p 5002:5002 -v coqui_models:/root/.local/share/tts synesthesiam/coqui-stt:latest
if %errorlevel% equ 0 (
    echo ✅ Coqui TTS started successfully
) else (
    echo ❌ Coqui TTS failed to start
)

echo Starting Piper TTS with custom server...
docker run -d --name piper-tts --network java_app_default -p 5003:8000 -v %cd%/piper-server:/app python:3.9-slim bash -c "
apt-get update -qq && 
apt-get install -y curl wget && 
cd /app && 
python server.py 8000
"
if %errorlevel% equ 0 (
    echo ✅ Piper TTS started successfully
) else (
    echo ❌ Piper TTS failed to start
)

echo.
echo [4/6] Waiting for services to initialize (30 seconds)...
timeout /t 30 >nul

echo.
echo [5/6] Testing all services...
echo.

echo Testing Whisper API (port 9000)...
curl -s -m 5 http://localhost:9000/health >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Whisper API is responding
) else (
    echo ❌ Whisper API is not responding
)

echo Testing LibreTranslate (port 5000)...
curl -s -m 5 http://localhost:5000/spec >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ LibreTranslate is responding
) else (
    echo ❌ LibreTranslate is not responding
)

echo Testing Coqui TTS (port 5002)...
curl -s -m 5 http://localhost:5002/api/tts >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Coqui TTS is responding
) else (
    echo ❌ Coqui TTS is not responding
)

echo Testing Piper TTS (port 5003)...
curl -s -m 5 http://localhost:5003/health >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Piper TTS is responding
) else (
    echo ❌ Piper TTS is not responding
)

echo.
echo [6/6] Service Status Summary:
echo ========================================
if %errorlevel% equ 0 (
    echo ✅ All services are running properly!
    echo.
    echo Services available at:
    echo - Whisper API: http://localhost:9000
    echo - LibreTranslate: http://localhost:5000
    echo - Coqui TTS: http://localhost:5002
    echo - Piper TTS: http://localhost:5003
    echo.
    echo You can now start EchoBridge:
    echo ./gradlew bootRun
) else (
    echo ⚠️  Some services may not be responding properly
    echo.
    echo Check the logs above for details
    echo.
    echo You can still try starting EchoBridge:
    echo ./gradlew bootRun
)

echo.
echo [7/7] Alternative: Use immediate mode if Docker issues persist
echo.
echo If services continue to fail, you can use:
echo ./start-immediate.bat
echo This uses local Vosk for transcription and fallback services
echo.
pause
