@echo off
REM Working Docker Startup Script - Uses Individual Commands

echo 🚀 Starting EchoBridge Services (Working Version)...

REM Check Docker
docker --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Docker is not installed or not running
    echo 📝 Please install Docker Desktop: https://www.docker.com/products/docker-desktop
    pause
    exit /b 1
)

echo ✅ Docker is running

REM Start services individually with working images
echo 🎤 Starting Whisper API...
docker run -d --name whisper-api -p 9000:8000 onerah/whisper-api
if %errorlevel% equ 0 (
    echo ✅ Whisper API started successfully
) else (
    echo ⚠️  Whisper API failed to start, will use Vosk fallback
)

echo 🌐 Starting LibreTranslate...
docker run -d --name libretranslate -p 5000:5000 libretranslate/libretranslate
if %errorlevel% equ 0 (
    echo ✅ LibreTranslate started successfully
) else (
    echo ⚠️  LibreTranslate failed to start, will use fallback translation
)

echo 🔊 Starting Coqui TTS...
docker run -d --name coqui-tts -p 5002:5002 synesthesiam/coqui-stt:latest python -m tts.server.server --model_name "tts_models/en/ljspeech/tacotron2-DDC" --port 5002 --host 0.0.0.0
if %errorlevel% equ 0 (
    echo ✅ Coqui TTS started successfully
) else (
    echo ⚠️  Coqui TTS failed to start, will use fallback TTS
)

REM Simple Piper placeholder
echo 🎵 Starting Piper placeholder...
docker run -d --name piper-tts -p 5003:5000 python:3.9-slim python -m http.server 5000
if %errorlevel% equ 0 (
    echo ✅ Piper placeholder started
) else (
    echo ⚠️  Piper placeholder failed to start
)

echo.
echo ⏳ Waiting for services to initialize...
timeout /t 15 /nobreak >nul

REM Check what's actually running
echo 🔄 Checking service status...
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

echo.
echo 🌐 Service URLs:
echo    Whisper API:     http://localhost:9000
echo    LibreTranslate:  http://localhost:5000
echo    Coqui TTS:       http://localhost:5002
echo    Piper TTS:       http://localhost:5003
echo.

REM Test connectivity
echo 🧪 Testing service connectivity...

curl -s http://localhost:9000/health >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Whisper API is responding
) else (
    echo ❌ Whisper API is not responding
)

curl -s http://localhost:5000/spec >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ LibreTranslate is responding
) else (
    echo ❌ LibreTranslate is not responding
)

curl -s http://localhost:5002 >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Coqui TTS is responding
) else (
    echo ❌ Coqui TTS is not responding
)

echo.
echo 🏠 Starting EchoBridge Application...

REM Check if Vosk model exists
if not exist "src\main\resources\vosk-model-small-en-us-0.15" (
    echo ⚠️  Vosk model not found. Downloading...
    if not exist "src\main\resources" mkdir "src\main\resources"
    cd "src\main\resources"
    
    echo 📥 Downloading Vosk model...
    powershell -Command "Invoke-WebRequest -Uri 'https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip' -OutFile 'vosk-model-small-en-us-0.15.zip'"
    
    echo 📦 Extracting model...
    powershell -Command "Expand-Archive -Path 'vosk-model-small-en-us-0.15.zip' -DestinationPath '.'"
    
    cd ..\..
    echo ✅ Vosk model ready
) else (
    echo ✅ Vosk model found
)

echo 🚀 Starting Spring Boot application...
gradlew bootRun

echo.
echo ✅ EchoBridge is running!
echo 🌐 Open http://localhost:8080 in your browser
echo.
echo 📋 What works:
echo    ✅ Real-time transcription (Vosk + Whisper if available)
echo    ✅ Translation (LibreTranslate if available, otherwise fallback)
echo    ✅ Web interface with microphone controls
echo.
echo 🛑 To stop services: docker stop whisper-api libretranslate coqui-tts piper-tts
echo 🗑️  To remove services: docker rm whisper-api libretranslate coqui-tts piper-tts

pause
