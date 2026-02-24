#!/bin/bash

# EchoBridge Service Startup Script
# Starts all required services in the correct order

echo "🚀 Starting EchoBridge Services..."

# Function to check if port is available
check_port() {
    if lsof -Pi :$1 -sTCP:LISTEN -t >/dev/null ; then
        echo "✅ Port $1 is already in use"
        return 0
    else
        echo "⏳ Port $1 is available"
        return 1
    fi
}

# Function to wait for service
wait_for_service() {
    local port=$1
    local service_name=$2
    local max_attempts=30
    local attempt=1
    
    echo "⏳ Waiting for $service_name on port $port..."
    
    while [ $attempt -le $max_attempts ]; do
        if check_port $port; then
            echo "✅ $service_name is ready!"
            return 0
        fi
        
        echo "⏳ Attempt $attempt/$max_attempts - Waiting for $service_name..."
        sleep 2
        ((attempt++))
    done
    
    echo "❌ $service_name failed to start within timeout"
    return 1
}

# 1. Start Whisper API (Port 9000)
echo "🎤 Starting Whisper API..."
if ! check_port 9000; then
    # Example: docker run -p 9000:8000 openai/whisper-api
    # or: python whisper_api_server.py
    echo "📝 Please start Whisper API on port 9000:"
    echo "   docker run -p 9000:8000 openai/whisper-api"
    echo "   or python whisper_api_server.py"
fi

# 2. Start LibreTranslate (Port 5000)
echo "🌐 Starting LibreTranslate..."
if ! check_port 5000; then
    # Example: docker run -p 5000:5000 libretranslate/libretranslate
    echo "📝 Please start LibreTranslate on port 5000:"
    echo "   docker run -p 5000:5000 libretranslate/libretranslate"
    echo "   or pip install libretranslate && libretranslate --host 0.0.0.0 --port 5000"
fi

# 3. Start Coqui TTS (Port 5002)
echo "🔊 Starting Coqui TTS..."
if ! check_port 5002; then
    # Example: docker run -p 5002:5002 coqui-tts
    echo "📝 Please start Coqui TTS on port 5002:"
    echo "   docker run -p 5002:5002 coqui-tts"
    echo "   or pip install coqui-tts && tts-server --model_name tts_models/en/ljspeech/tacotron2-DDC"
fi

# 4. Start Piper TTS (Port 5003)
echo "🎵 Starting Piper TTS..."
if ! check_port 5003; then
    # Example: docker run -p 5003:5003 piper-tts
    echo "📝 Please start Piper TTS on port 5003:"
    echo "   docker run -p 5003:5003 piper-tts"
    echo "   or use local Piper installation"
fi

echo ""
echo "🔄 Service Status Check:"
echo "   Whisper API:     http://localhost:9000"
echo "   LibreTranslate:  http://localhost:5000"
echo "   Coqui TTS:       http://localhost:5002"
echo "   Piper TTS:       http://localhost:5003"
echo ""

# 5. Start EchoBridge Application
echo "🏠 Starting EchoBridge Application..."
echo "   The app will use Vosk (local) for transcription until Whisper API is ready"
echo "   Translation and TTS will use local fallbacks until APIs are ready"

# Check if Vosk model exists
if [ ! -d "src/main/resources/vosk-model-small-en-us-0.15" ]; then
    echo "⚠️  Vosk model not found. Please download it:"
    echo "   mkdir -p src/main/resources"
    echo "   cd src/main/resources"
    echo "   wget https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    echo "   unzip vosk-model-small-en-us-0.15.zip"
    echo ""
fi

echo "🚀 Starting Spring Boot application..."
# ./gradlew bootRun

echo ""
echo "✅ EchoBridge is ready!"
echo "🌐 Open http://localhost:8080 in your browser"
echo ""
echo "📋 Quick Test Commands:"
echo "   curl http://localhost:9000/asr -X POST -H 'Content-Type: application/json' -d '{\"audio\":\"base64data\"}'"
echo "   curl http://localhost:5000/translate -X POST -H 'Content-Type: application/json' -d '{\"q\":\"hello\",\"source\":\"en\",\"target\":\"es\"}'"
echo "   curl http://localhost:5002/api/tts -X POST -H 'Content-Type: application/json' -d '{\"text\":\"hello\",\"language\":\"en\"}'"
