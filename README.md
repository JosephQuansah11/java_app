# EchoBridge Speech Translation System

## 🚀 Quick Start

### **Step 1: Start External Services (Required for Full Features)**

The application needs these external services running for full functionality:

```bash
# 1. Whisper API (Port 9000) - Speech Recognition
docker run -p 9000:8000 openai/whisper-api

# 2. LibreTranslate (Port 5000) - Translation  
docker run -p 5000:5000 libretranslate/libretranslate

# 3. Coqui TTS (Port 5002) - Text-to-Speech
docker run -p 5002:5002 coqui-tts

# 4. Piper TTS (Port 5003) - Alternative TTS
docker run -p 5003:5003 piper-tts
```

### **Step 2: Download Vosk Model (Local Fallback)**

```bash
mkdir -p src/main/resources
cd src/main/resources
wget https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
unzip vosk-model-small-en-us-0.15.zip
```

### **Step 3: Start EchoBridge Application**

```bash
# Windows
start-services.bat

# Linux/Mac  
./start-services.sh

# Or directly:
./gradlew bootRun
```

### **Step 4: Open Web Interface**

Navigate to: **http://localhost:8080**

## 🎯 Service Configuration

### **Current Configuration (Fallback Mode)**
```properties
# Using local Vosk until Whisper API is ready
speech.recognition.provider=vosk

# Using fallback translation until LibreTranslate is ready  
translation.provider=libre

# Using local Piper until Coqui TTS is ready
tts.provider=piper
```

### **Switch to Enhanced APIs**
Once services are running, update `application.properties`:

```properties
speech.recognition.provider=whisper
translation.provider=libre  
tts.provider=coqui
```

## 🔄 Service Startup Order

1. **Whisper API** (Port 9000) - Speech Recognition
2. **LibreTranslate** (Port 5000) - Translation
3. **Coqui TTS** (Port 5002) - Text-to-Speech  
4. **Piper TTS** (Port 5003) - Alternative TTS
5. **EchoBridge App** (Port 8080) - Main Application

## 🛠️ Fallback Behavior

### **When APIs Are Not Running:**
- ✅ **Transcription**: Uses local Vosk model
- ✅ **Translation**: Shows `[EN→ES] hello` format
- ✅ **Text-to-Speech**: Uses local Piper or silent fallback

### **When APIs Are Running:**
- 🚀 **Transcription**: Whisper API (higher accuracy)
- 🌐 **Translation**: Real translation via LibreTranslate/Argos
- 🔊 **Text-to-Speech**: High-quality audio via Coqui/Piper

## 📊 Testing Services

```bash
# Test Whisper API
curl http://localhost:9000/asr -X POST \
  -H 'Content-Type: application/json' \
  -d '{"audio":"base64audio","format":"wav","sample_rate":16000}'

# Test LibreTranslate  
curl http://localhost:5000/translate -X POST \
  -H 'Content-Type: application/json' \
  -d '{"q":"hello","source":"en","target":"es"}'

# Test Coqui TTS
curl http://localhost:5002/api/tts -X POST \
  -H 'Content-Type: application/json' \
  -d '{"text":"hello","language":"en"}'
```

## 🎤 Using the Web Interface

1. **Click "Start Microphone"** to begin real-time transcription
2. **Speak clearly** - you'll see transcription appear immediately
3. **Translation appears** alongside the original text
4. **Click "Stop Microphone"** when finished
5. **Use manual translation** for text input testing

## 🔧 Troubleshooting

### **Connection Refused Errors**
```bash
# Check if services are running
netstat -an | findstr ":9000"  # Whisper
netstat -an | findstr ":5000"  # LibreTranslate
netstat -an | findstr ":5002"  # Coqui TTS
netstat -an | findstr ":5003"  # Piper TTS
```

### **Vosk Model Issues**
```bash
# Verify model exists
ls -la src/main/resources/vosk-model-small-en-us-0.15/

# Should contain: am/final.mdl, conf/mfcc.conf, etc.
```

### **Application Won't Start**
```bash
# Check Java version (requires Java 17+)
java -version

# Clean and rebuild
./gradlew clean build
```

## 📁 Project Structure

```
src/main/java/echobridge/com/java_app/
├── core/services/
│   ├── EnhancedSpeechRecognitionService.java  # Vosk + Whisper
│   ├── EnhancedTranslationService.java        # Libre + Argos
│   ├── EnhancedTextToSpeechService.java       # Coqui + Piper
│   └── FallbackTranslationService.java        # Local fallback
├── configuration/
│   ├── WebClientConfig.java                   # HTTP clients
│   └── JacksonConfig.java                     # JSON handling
└── api/
    └── EchoBridgeController.java               # REST endpoints

src/main/resources/
├── templates/index.html                       # Web interface
├── application.properties                     # Configuration
└── vosk-model-small-en-us-0.15/              # Local Vosk model
```

## 🚀 Features

### **Multiple Model Support**
- **Speech Recognition**: Vosk (local) + Whisper (API)
- **Translation**: LibreTranslate + Argos (self-hosted APIs)
- **Text-to-Speech**: Coqui TTS + Piper (API + local)

### **Real-Time Processing**
- Live transcription as you speak
- Immediate translation display
- Audio playback of translated speech
- WebSocket-like updates via polling

### **Fallback Mechanisms**
- Graceful degradation when APIs unavailable
- Local Vosk model always available
- Simple translation indicators
- Silent TTS fallback

## 🌐 API Endpoints

### **EchoBridge REST API**
```bash
POST /api/echo/microphone/start    # Start recording
POST /api/echo/microphone/stop     # Stop recording  
GET  /api/echo/microphone/status   # Check status
GET  /api/echo/results/latest      # Get latest results
POST /api/echo/translate           # Manual translation
POST /api/echo/session/clear       # Clear session
```

## 📝 Development Notes

- **Spring Boot 3.x** with **Java 17+**
- **Akka Streams** for audio processing
- **RestTemplate** for API calls (WebClient alternative)
- **Jackson** for JSON serialization
- **Lombok** for boilerplate reduction

## 🤝 Contributing

1. Ensure all services are running before testing
2. Use Vosk model for local development
3. Test fallback behavior when APIs are down
4. Check logs for connection errors
5. Verify real-time updates in web interface

---

**🎉 Your EchoBridge system is now ready with robust fallback mechanisms!**
