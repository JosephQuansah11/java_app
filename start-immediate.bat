@echo off
REM Immediate Start Script - Works Without Docker

echo 🚀 Starting EchoBridge (Immediate Mode)...

echo ℹ️  This mode uses local services that work immediately:
echo    - Vosk (local transcription)
echo    - Fallback translation 
echo    - Silent TTS fallback
echo.

REM Check if Vosk model exists
if not exist "src\main\resources\vosk-model-small-en-us-0.15" (
    echo ⚠️  Vosk model not found. Downloading...
    if not exist "src\main\resources" mkdir "src\main\resources"
    cd "src\main\resources"
    
    echo 📥 Downloading Vosk model (this may take a few minutes)...
    powershell -Command "Invoke-WebRequest -Uri 'https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip' -OutFile 'vosk-model-small-en-us-0.15.zip'"
    
    echo 📦 Extracting model...
    powershell -Command "Expand-Archive -Path 'vosk-model-small-en-us-0.15.zip' -DestinationPath '.'"
    
    cd ..\..
    echo ✅ Vosk model downloaded and extracted
) else (
    echo ✅ Vosk model found
)

echo.
echo 🎯 Starting EchoBridge with local services...
echo    This will work immediately for transcription!
echo.

REM Start the application
echo 🚀 Starting Spring Boot application...
gradlew bootRun

echo.
echo ✅ EchoBridge is running!
echo 🌐 Open http://localhost:8080 in your browser
echo.
echo 📋 What works immediately:
echo    ✅ Real-time transcription with Vosk
echo    ✅ Web interface with microphone controls
echo    ✅ Session management
echo    ✅ Fallback translation (shows [EN→ES] hello)
echo.
echo 📋 What requires external services:
echo    ❌ Whisper API (higher accuracy transcription)
echo    ❌ Real translation via LibreTranslate
echo    ❌ Audio playback via TTS services
echo.
echo 💡 To add enhanced features later:
echo    1. Install Docker Desktop
echo    2. Run: docker-compose up -d
echo    3. Update application.properties to use enhanced providers
echo.

pause
