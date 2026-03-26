@echo off
REM EchoBridge Quick Start Script
REM This script works even if Docker is not available

echo ========================================
echo   EchoBridge Quick Start Script
echo ========================================
echo.

echo This script will start EchoBridge with local services.
echo Works immediately for speech transcription!
echo.

REM Check if Vosk model exists
if not exist "src\main\resources\vosk-model-small-en-us-0.15" (
    echo [1/3] Downloading Vosk model (one-time setup)...
    if not exist "src\main\resources" mkdir "src\main\resources"
    cd "src\main\resources"
    
    echo Downloading Vosk model (40MB)...
    powershell -Command "Invoke-WebRequest -Uri 'https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip' -OutFile 'vosk-model-small-en-us-0.15.zip'"
    
    echo Extracting model...
    powershell -Command "Expand-Archive -Path 'vosk-model-small-en-us-0.15.zip' -DestinationPath '.'"
    del "vosk-model-small-en-us-0.15.zip"
    
    cd ..\..
    echo [✓] Vosk model downloaded
) else (
    echo [✓] Vosk model found
)

echo.
echo [2/3] Building application...
call gradlew.bat clean build
if %errorlevel% neq 0 (
    echo [✗] Build failed
    pause
    exit /b 1
)
echo [✓] Build successful

echo.
echo [3/3] Starting EchoBridge...
echo.
echo ========================================
echo   EchoBridge is Starting!
echo ========================================
echo.
echo Web Interface: http://localhost:8080
echo.
echo Features Available:
echo   ✓ Real-time speech transcription
echo   ✓ Web interface with microphone
echo   ✓ Session management
echo   ✓ Basic translation indicators
echo.
echo To add enhanced features later:
echo   1. Install Docker Desktop
echo   2. Run: docker-compose up -d
echo   3. Restart this application
echo.
echo Press Ctrl+C to stop the application
echo ========================================
echo.

REM Start the application
call gradlew.bat bootRun

echo.
echo EchoBridge stopped.
pause
