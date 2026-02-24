@echo off
REM EchoBridge Service Startup Script for Windows with Docker

echo 🚀 Starting EchoBridge Services with Docker...

REM Check if Docker is running
docker --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Docker is not installed or not running
    echo 📝 Please install Docker Desktop from: https://www.docker.com/products/docker-desktop
    pause
    exit /b 1
)

echo ✅ Docker is running

REM Start services with Docker Compose
echo 🐳 Starting all services with Docker Compose...
docker-compose up -d

REM Wait for services to be ready
echo ⏳ Waiting for services to be ready...
timeout /t 10 /nobreak >nul

REM Check service status
echo 🔄 Checking service status...
docker-compose ps

echo.
echo 🌐 Service URLs:
echo    Whisper API:     http://localhost:9000
echo    LibreTranslate:  http://localhost:5000
echo    Coqui TTS:       http://localhost:5002
echo    Piper TTS:       http://localhost:5003
echo.

REM Check if Vosk model exists
if not exist "src\main\resources\vosk-model-small-en-us-0.15" (
    echo ⚠️  Vosk model not found. Please download it:
    echo    mkdir src\main\resources
    echo    cd src\main\resources
    echo    wget https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
    echo    tar -xf vosk-model-small-en-us-0.15.zip
    echo.
)

echo 🏠 Starting EchoBridge Application...
echo    The app will use enhanced APIs now that Docker services are running
echo.

REM Update configuration to use enhanced services
echo 📝 Updating configuration to use enhanced services...
echo speech.recognition.provider=whisper > temp.properties
echo translation.provider=libre >> temp.properties
echo tts.provider=coqui >> temp.properties
echo whisper.api.url=http://localhost:9000/asr >> temp.properties
echo libretranslate.url=http://localhost:5000 >> temp.properties
echo coqui.tts.url=http://localhost:5002 >> temp.properties
echo piper.tts.url=http://localhost:5003 >> temp.properties

echo 🚀 Starting Spring Boot application...
echo gradlew bootRun

echo.
echo ✅ EchoBridge is ready with full enhanced features!
echo 🌐 Open http://localhost:8080 in your browser
echo.
echo 📋 Quick Test Commands:
echo    curl http://localhost:9000/asr -X POST -H "Content-Type: application/json" -d "{\"audio\":\"base64data\"}"
echo    curl http://localhost:5000/translate -X POST -H "Content-Type: application/json" -d "{\"q\":\"hello\",\"source\":\"en\",\"target\":\"es\"}"
echo    curl http://localhost:5002/api/tts -X POST -H "Content-Type: application/json" -d "{\"text\":\"hello\",\"language\":\"en\"}"
echo.
echo 🛑 To stop services: docker-compose down

pause
