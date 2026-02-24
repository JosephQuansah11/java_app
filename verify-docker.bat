@echo off
REM Docker Setup Verification Script

echo 🔍 Verifying Docker Setup for EchoBridge...

REM Check Docker installation
echo 1. Checking Docker installation...
docker --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Docker is not installed
    echo 📝 Please install Docker Desktop: https://www.docker.com/products/docker-desktop
    goto :end
)
echo ✅ Docker is installed

REM Check Docker is running
echo 2. Checking if Docker is running...
docker info >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Docker is not running
    echo 📝 Please start Docker Desktop
    goto :end
)
echo ✅ Docker is running

REM Check Docker Compose
echo 3. Checking Docker Compose...
docker-compose --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Docker Compose is not installed
    goto :end
)
echo ✅ Docker Compose is available

REM Check docker-compose.yml exists
echo 4. Checking docker-compose.yml file...
if not exist "docker-compose.yml" (
    echo ❌ docker-compose.yml not found
    goto :end
)
echo ✅ docker-compose.yml found

REM Check available disk space (simplified)
echo 5. Checking disk space...
for /f "tokens=3" %%a in ('dir c:\ ^| find "bytes free"') do set freespace=%%a
set freespace=%freespace:,=%
if %freespace% LSS 5000000000 (
    echo ⚠️  Low disk space (less than 5GB available)
    echo    Services may need ~4GB for models
) else (
    echo ✅ Sufficient disk space available
)

REM Check available memory (simplified check)
echo 6. Checking system resources...
wmic computersystem get TotalPhysicalMemory /value | find "TotalPhysicalMemory" > temp_memory.txt
for /f "tokens=2 delims==" %%a in (temp_memory.txt) do set memory=%%a
set memory=%memory:~0,-6%
if %memory% LSS 8000000 (
    echo ⚠️  Low memory (less than 8GB)
    echo    Services may run slowly
) else (
    echo ✅ Sufficient memory available
)
del temp_memory.txt

echo.
echo 🎯 Ready to start EchoBridge services!
echo.
echo 📋 Next steps:
echo    1. Run: start-services.bat
echo    2. Wait for services to start (1-2 minutes)
echo    3. Open: http://localhost:8080
echo.
echo 🐳 Services that will start:
echo    • Whisper API (Port 9000) - Speech Recognition
echo    • LibreTranslate (Port 5000) - Translation
echo    • Coqui TTS (Port 5002) - Text-to-Speech
echo    • Piper TTS (Port 5003) - Alternative TTS
echo.

:end
echo Press any key to exit...
pause >nul
