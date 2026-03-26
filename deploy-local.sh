#!/bin/bash
# EchoBridge Local Deployment Script
# Run this in bash or Git Bash

echo "🚀 Starting EchoBridge Local Deployment..."

# Check Java
echo "🔍 Checking Java installation..."
if command -v java &> /dev/null; then
    java_version=$(java -version 2>&1)
    echo "✅ Java found: $java_version"
else
    echo "❌ Java not found. Please install Java 17+ from https://adoptium.net/"
    read -p "Press Enter to exit"
    exit 1
fi

# Check Docker
echo "🔍 Checking Docker..."
if command -v docker &> /dev/null; then
    docker_version=$(docker --version)
    echo "✅ Docker found: $docker_version"
    docker_available=true
else
    echo "❌ Docker not found. Please install Docker Desktop"
    echo "📝 Download from: https://www.docker.com/products/docker-desktop"
    read -p "Continue without Docker? (y/n) " choice
    if [[ $choice != "y" ]]; then
        exit 1
    fi
    docker_available=false
fi

# Check if Vosk model exists
vosk_path="src/main/resources/vosk-model-small-en-us-0.15"
if [ ! -d "$vosk_path" ]; then
    echo "📥 Downloading Vosk model..."
    mkdir -p "src/main/resources"
    
    if command -v wget &> /dev/null; then
        wget -O "src/main/resources/vosk-model-small-en-us-0.15.zip" "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    elif command -v curl &> /dev/null; then
        curl -o "src/main/resources/vosk-model-small-en-us-0.15.zip" "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    else
        echo "❌ Neither wget nor curl found. Please download Vosk model manually."
        exit 1
    fi
    
    echo "📦 Extracting model..."
    if command -v unzip &> /dev/null; then
        cd "src/main/resources"
        unzip "vosk-model-small-en-us-0.15.zip"
        rm "vosk-model-small-en-us-0.15.zip"
        cd ../..
        echo "✅ Vosk model ready"
    else
        echo "❌ unzip not found. Please extract the model manually."
        exit 1
    fi
else
    echo "✅ Vosk model found"
fi

# Start Docker services if available
if [ "$docker_available" = true ]; then
    echo "🐳 Starting Docker services..."
    
    # Stop existing containers
    echo "🛑 Stopping existing containers..."
    docker stop whisper-api libretranslate coqui-tts piper-tts 2>/dev/null || true
    docker rm whisper-api libretranslate coqui-tts piper-tts 2>/dev/null || true
    
    # Start services
    echo "🎤 Starting Whisper API..."
    docker run -d --name whisper-api -p 9000:8000 onerah/whisper-api 2>/dev/null || echo "⚠️ Whisper API failed to start"
    
    echo "🌐 Starting LibreTranslate..."
    docker run -d --name libretranslate -p 5000:5000 libretranslate/libretranslate 2>/dev/null || echo "⚠️ LibreTranslate failed to start"
    
    echo "🔊 Starting Coqui TTS..."
    docker run -d --name coqui-tts -p 5002:5002 synesthesiam/coqui-stt:latest python -m tts.server.server --model_name "tts_models/en/ljspeech/tacotron2-DDC" --port 5002 --host 0.0.0.0 2>/dev/null || echo "⚠️ Coqui TTS failed to start"
    
    echo "🎵 Starting Piper placeholder..."
    docker run -d --name piper-tts -p 5003:5000 python:3.9-slim python -m http.server 5000 2>/dev/null || echo "⚠️ Piper placeholder failed to start"
    
    echo "⏳ Waiting for services to initialize..."
    sleep 15
    
    # Check services
    echo "🔍 Checking service status..."
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
    
    echo ""
    echo "🌐 Service URLs:"
    echo "    Whisper API:     http://localhost:9000"
    echo "    LibreTranslate:  http://localhost:5000"
    echo "    Coqui TTS:       http://localhost:5002"
    echo "    Piper TTS:       http://localhost:5003"
    echo ""
fi

# Build and run the application
echo "🏗️ Building EchoBridge application..."
if ./gradlew clean build; then
    echo "✅ Build successful"
else
    echo "❌ Build failed. Check the error messages above."
    read -p "Press Enter to exit"
    exit 1
fi

echo "🚀 Starting EchoBridge application..."
echo "🌐 Application will be available at: http://localhost:8080"
echo ""
echo "📋 Features available:"
if [ "$docker_available" = true ]; then
    echo "    ✅ Real-time transcription (Whisper + Vosk)"
    echo "    ✅ Real translation (LibreTranslate)"
    echo "    ✅ Text-to-speech (Coqui TTS)"
else
    echo "    ✅ Real-time transcription (Vosk local)"
    echo "    ⚠️ Translation (fallback mode)"
    echo "    ⚠️ Text-to-speech (silent fallback)"
fi
echo ""

# Start the application
./gradlew bootRun

echo ""
echo "🛑 To stop services:"
if [ "$docker_available" = true ]; then
    echo "    docker stop whisper-api libretranslate coqui-tts piper-tts"
fi
echo "    Press Ctrl+C in this window to stop the application"
echo ""
echo "🎉 EchoBridge deployment complete!"
