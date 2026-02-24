@echo off
REM Docker Troubleshooting and Alternative Setup Script

echo 🔧 Docker Troubleshooting for EchoBridge...

REM Check Docker Desktop status
echo 1. Checking Docker Desktop status...
docker --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Docker is not installed
    echo 📝 Please install Docker Desktop: https://www.docker.com/products/docker-desktop
    goto :alternative
)

echo ✅ Docker is installed

REM Check if Docker daemon is running
echo 2. Checking Docker daemon...
docker info >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Docker daemon is not running
    echo 📝 Please start Docker Desktop and wait for it to fully initialize
    echo 💡 Look for Docker Desktop icon in system tray
    goto :alternative
)

echo ✅ Docker daemon is running

REM Try to restart Docker Desktop
echo 3. Attempting to restart Docker services...
docker system prune -f >nul 2>&1

REM Try starting services again
echo 4. Attempting to start services...
docker-compose up -d --timeout 60

if %errorlevel% neq 0 (
    echo ❌ Failed to start services with Docker
    goto :alternative
)

echo ✅ Services started successfully
goto :test_services

:alternative
echo.
echo 🔄 Docker is not working properly. Let's try alternatives...
echo.

REM Option 1: Individual Docker containers
echo 📋 Option 1: Start services individually with Docker
echo    Whisper API:
echo      docker run -d -p 9000:8000 --name whisper-api openai/whisper-api
echo.
echo    LibreTranslate:
echo      docker run -d -p 5000:5000 --name libretranslate libretranslate/libretranslate
echo.
echo    Coqui TTS:
echo      docker run -d -p 5002:5002 --name coqui-tts ghcr.io/coqui-ai/tts:latest tts-server --model_name "tts_models/en/ljspeech/tacotron2-DDC" --port 5002 --host 0.0.0.0
echo.
echo    Then run: gradlew bootRun
echo.

REM Option 2: Local Python installation
echo 📋 Option 2: Install services locally (Python required)
echo    1. Install Python 3.8+ from https://python.org
echo    2. Run these commands:
echo       pip install openai-whisper fastapi uvicorn
echo       pip install libretranslate
echo       pip install TTS
echo.
echo    3. Start services manually:
echo       python -m whisper.server --port 9000
echo       libretranslate --host 0.0.0.0 --port 5000
echo       tts-server --model_name "tts_models/en/ljspeech/tacotron2-DDC" --port 5002
echo.

REM Option 3: Use local fallbacks
echo 📋 Option 3: Use local fallbacks (works immediately)
echo    Your app already works with:
echo    - Vosk (local transcription)
echo    - Fallback translation (shows [EN→ES] hello)
echo    - Silent TTS fallback
echo.
echo    Just run: ./gradlew bootRun
echo    Open: http://localhost:8080
echo.

:test_services
echo.
echo 🧪 Testing service connectivity...

REM Test Whisper API
echo Testing Whisper API (port 9000)...
curl -s http://localhost:9000/health >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Whisper API is running
) else (
    echo ❌ Whisper API is not responding
)

REM Test LibreTranslate
echo Testing LibreTranslate (port 5000)...
curl -s http://localhost:5000/spec >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ LibreTranslate is running
) else (
    echo ❌ LibreTranslate is not responding
)

REM Test Coqui TTS
echo Testing Coqui TTS (port 5002)...
curl -s http://localhost:5002/api/tts >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Coqui TTS is running
) else (
    echo ❌ Coqui TTS is not responding
)

echo.
echo 🎯 Recommendation:
echo.
echo If Docker is not working, use Option 3 (local fallbacks) for immediate testing.
echo Your EchoBridge app will work right away with Vosk transcription!
echo.

pause
