# EchoBridge Local Deployment Script
# Run this in PowerShell as Administrator

Write-Host "🚀 Starting EchoBridge Local Deployment..." -ForegroundColor Green

# Check if running as Administrator
if (-NOT ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole] "Administrator")) {
    Write-Host "❌ Please run this script as Administrator" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

# Check Java
Write-Host "🔍 Checking Java installation..." -ForegroundColor Yellow
try {
    $javaVersion = java -version 2>&1
    Write-Host "✅ Java found: $javaVersion" -ForegroundColor Green
} catch {
    Write-Host "❌ Java not found. Please install Java 17+ from https://adoptium.net/" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

# Check Docker
Write-Host "🔍 Checking Docker..." -ForegroundColor Yellow
try {
    $dockerVersion = docker --version
    Write-Host "✅ Docker found: $dockerVersion" -ForegroundColor Green
} catch {
    Write-Host "❌ Docker not found. Please install Docker Desktop" -ForegroundColor Red
    Write-Host "📝 Download from: https://www.docker.com/products/docker-desktop" -ForegroundColor Yellow
    $choice = Read-Host "Continue without Docker? (y/n)"
    if ($choice -ne "y") {
        exit 1
    }
}

# Check if Vosk model exists
$voskPath = "src\main\resources\vosk-model-small-en-us-0.15"
if (-not (Test-Path $voskPath)) {
    Write-Host "📥 Downloading Vosk model..." -ForegroundColor Yellow
    if (-not (Test-Path "src\main\resources")) {
        New-Item -ItemType Directory -Path "src\main\resources" -Force
    }
    
    try {
        Invoke-WebRequest -Uri "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip" -OutFile "src\main\resources\vosk-model-small-en-us-0.15.zip"
        Write-Host "📦 Extracting model..." -ForegroundColor Yellow
        Expand-Archive -Path "src\main\resources\vosk-model-small-en-us-0.15.zip" -DestinationPath "src\main\resources\" -Force
        Remove-Item "src\main\resources\vosk-model-small-en-us-0.15.zip" -Force
        Write-Host "✅ Vosk model ready" -ForegroundColor Green
    } catch {
        Write-Host "⚠️ Failed to download Vosk model. Will use fallback." -ForegroundColor Yellow
    }
} else {
    Write-Host "✅ Vosk model found" -ForegroundColor Green
}

# Start Docker services if available
$dockerAvailable = $true
try {
    docker ps | Out-Null
} catch {
    $dockerAvailable = $false
}

if ($dockerAvailable) {
    Write-Host "🐳 Starting Docker services..." -ForegroundColor Yellow
    
    # Stop existing containers
    Write-Host "🛑 Stopping existing containers..." -ForegroundColor Yellow
    docker stop whisper-api libretranslate coqui-tts piper-tts 2>$null
    docker rm whisper-api libretranslate coqui-tts piper-tts 2>$null
    
    # Start services
    Write-Host "🎤 Starting Whisper API..." -ForegroundColor Yellow
    docker run -d --name whisper-api -p 9000:8000 onerah/whisper-api 2>$null
    
    Write-Host "🌐 Starting LibreTranslate..." -ForegroundColor Yellow
    docker run -d --name libretranslate -p 5000:5000 libretranslate/libretranslate 2>$null
    
    Write-Host "🔊 Starting Coqui TTS..." -ForegroundColor Yellow
    docker run -d --name coqui-tts -p 5002:5002 synesthesiam/coqui-stt:latest python -m tts.server.server --model_name "tts_models/en/ljspeech/tacotron2-DDC" --port 5002 --host 0.0.0.0 2>$null
    
    Write-Host "🎵 Starting Piper placeholder..." -ForegroundColor Yellow
    docker run -d --name piper-tts -p 5003:5000 python:3.9-slim python -m http.server 5000 2>$null
    
    Write-Host "⏳ Waiting for services to initialize..." -ForegroundColor Yellow
    Start-Sleep -Seconds 15
    
    # Check services
    Write-Host "🔍 Checking service status..." -ForegroundColor Yellow
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
    
    Write-Host ""
    Write-Host "🌐 Service URLs:" -ForegroundColor Green
    Write-Host "    Whisper API:     http://localhost:9000" -ForegroundColor Cyan
    Write-Host "    LibreTranslate:  http://localhost:5000" -ForegroundColor Cyan
    Write-Host "    Coqui TTS:       http://localhost:5002" -ForegroundColor Cyan
    Write-Host "    Piper TTS:       http://localhost:5003" -ForegroundColor Cyan
    Write-Host ""
}

# Build and run the application
Write-Host "🏗️ Building EchoBridge application..." -ForegroundColor Yellow
try {
    & .\gradlew.bat clean build
    Write-Host "✅ Build successful" -ForegroundColor Green
} catch {
    Write-Host "❌ Build failed. Check the error messages above." -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host "🚀 Starting EchoBridge application..." -ForegroundColor Yellow
Write-Host "🌐 Application will be available at: http://localhost:8080" -ForegroundColor Green
Write-Host ""
Write-Host "📋 Features available:" -ForegroundColor Green
if ($dockerAvailable) {
    Write-Host "    ✅ Real-time transcription (Whisper + Vosk)" -ForegroundColor Green
    Write-Host "    ✅ Real translation (LibreTranslate)" -ForegroundColor Green
    Write-Host "    ✅ Text-to-speech (Coqui TTS)" -ForegroundColor Green
} else {
    Write-Host "    ✅ Real-time transcription (Vosk local)" -ForegroundColor Green
    Write-Host "    ⚠️ Translation (fallback mode)" -ForegroundColor Yellow
    Write-Host "    ⚠️ Text-to-speech (silent fallback)" -ForegroundColor Yellow
}
Write-Host ""

# Start the application
try {
    & .\gradlew.bat bootRun
} catch {
    Write-Host "❌ Failed to start application" -ForegroundColor Red
    Write-Host "📝 Check the logs above for errors" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "🛑 To stop services:" -ForegroundColor Yellow
if ($dockerAvailable) {
    Write-Host "    docker stop whisper-api libretranslate coqui-tts piper-tts" -ForegroundColor Cyan
}
Write-Host "    Press Ctrl+C in this window to stop the application" -ForegroundColor Cyan
Write-Host ""
Write-Host "🎉 EchoBridge deployment complete!" -ForegroundColor Green
