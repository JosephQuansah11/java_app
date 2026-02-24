# 🐳 Docker Setup Guide (Recommended)

## Quick Start with Docker

### 1. Install Docker Desktop
```bash
# Windows: https://www.docker.com/products/docker-desktop
# Mac: https://www.docker.com/products/docker-desktop  
# Linux: sudo apt install docker.io docker-compose
```

### 2. Start All Services with One Command
```bash
cd c:\Users\Quand\Documents\java_echobridge\java_app
docker-compose up -d
```

### 3. Verify Services Are Running
```bash
docker-compose ps
# Should show all 4 services as "healthy" or "running"
```

### 4. Start EchoBridge Application
```bash
./gradlew bootRun
```

### 5. Open Web Interface
Navigate to: **http://localhost:8080**

---

# 💻 Local Installation Guide (Advanced)

## Option 1: Whisper (Speech Recognition)

### Python Installation
```bash
# Install Python 3.8+ first
pip install openai-whisper
pip install fastapi uvicorn

# Create whisper server
python -c "
import whisper
import base64
import io
from fastapi import FastAPI
import uvicorn

app = FastAPI()
model = whisper.load_model('base')

@app.post('/asr')
async def transcribe(data: dict):
    audio_data = base64.b64decode(data['audio'])
    audio_file = io.BytesIO(audio_data)
    result = model.transcribe(audio_file)
    return {'text': result['text']}

if __name__ == '__main__':
    uvicorn.run(app, host='0.0.0.0', port=9000)
"
```

### Alternative: Use Existing Whisper Server
```bash
git clone https://github.com/openai/whisper
cd whisper
pip install -e .
python -m whisper.server --port 9000
```

## Option 2: LibreTranslate (Translation)

### Docker (Easiest)
```bash
docker run -p 5000:5000 libretranslate/libretranslate
```

### Python Installation
```bash
pip install libretranslate
libretranslate --host 0.0.0.0 --port 5000
```

### Download Language Models
```bash
# After starting LibreTranslate, visit: http://localhost:5000
# Click "Language Models" and download needed languages
```

## Option 3: Coqui TTS (Text-to-Speech)

### Python Installation
```bash
pip install TTS
tts-server --model_name "tts_models/en/ljspeech/tacotron2-DDC" --port 5002
```

### Alternative Models
```bash
# List available models
tts-server --list_models

# Use different model
tts-server --model_name "tts_models/multilingual/multi-dataset/xtts_v2" --port 5002
```

## Option 4: Piper TTS (Text-to-Speech)

### Binary Installation
```bash
# Download Piper binary
wget https://github.com/rhasspy/piper/releases/latest/download/piper_linux_x86_64.tar.gz
tar -xzf piper_linux_x86_64.tar.gz

# Download voice model
wget https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/en/en_US/lessac/medium/lessac_medium.onnx

# Create simple server
python -c "
from flask import Flask, request, jsonify
import subprocess
import tempfile
import os

app = Flask(__name__)

@app.route('/synthesize', methods=['POST'])
def synthesize():
    data = request.json
    text = data['text']
    
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as f:
        subprocess.run([
            './piper/piper',
            '--model', 'lessac_medium.onnx',
            '--output_file', f.name
        ], input=text.encode(), check=True)
        
        with open(f.name, 'rb') as audio_file:
            audio_data = audio_file.read()
        
        os.unlink(f.name)
        return audio_data

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5003)
"
```

---

# 🚀 Service Management Commands

## Docker Commands
```bash
# Start all services
docker-compose up -d

# Stop all services  
docker-compose down

# View logs
docker-compose logs -f

# Restart specific service
docker-compose restart whisper-api

# Check service health
docker-compose ps
```

## Local Service Testing
```bash
# Test Whisper
curl http://localhost:9000/asr -X POST \
  -H 'Content-Type: application/json' \
  -d '{"audio":"base64audio"}'

# Test LibreTranslate
curl http://localhost:5000/translate -X POST \
  -H 'Content-Type: application/json' \
  -d '{"q":"hello","source":"en","target":"es"}'

# Test Coqui TTS
curl http://localhost:5002/api/tts -X POST \
  -H 'Content-Type: application/json' \
  -d '{"text":"hello","language":"en"}'

# Test Piper TTS
curl http://localhost:5003/synthesize -X POST \
  -H 'Content-Type: application/json' \
  -d '{"text":"hello"}'
```

---

# 🔧 Configuration Updates

Once services are running, update `application.properties`:

```properties
# Enable enhanced services
speech.recognition.provider=whisper
translation.provider=libre
tts.provider=coqui

# Service URLs (Docker exposes these ports)
whisper.api.url=http://localhost:9000/asr
libretranslate.url=http://localhost:5000
coqui.tts.url=http://localhost:5002
piper.tts.url=http://localhost:5003
```

---

# 📊 Resource Requirements

## Docker Resource Usage
- **Whisper API**: ~2GB RAM, 1-2 CPU cores
- **LibreTranslate**: ~500MB RAM, 0.5 CPU core  
- **Coqui TTS**: ~1GB RAM, 1 CPU core
- **Piper TTS**: ~200MB RAM, 0.5 CPU core
- **Total**: ~3.7GB RAM, 4 CPU cores

## Local Installation Requirements
- **Python 3.8+** with pip
- **~10GB free disk space** for models
- **GPU support** (optional but recommended for Whisper)
- **FFmpeg** for audio processing

---

# 🐛 Troubleshooting

## Docker Issues
```bash
# Check if Docker is running
docker --version
docker-compose --version

# Fix permission issues (Linux)
sudo usermod -aG docker $USER
newgrp docker

# Clear Docker cache
docker system prune -a
```

## Port Conflicts
```bash
# Check what's using ports
netstat -an | findstr ":9000"
netstat -an | findstr ":5000"

# Kill processes using ports (Windows)
taskkill /PID <PID> /F

# Kill processes using ports (Linux)
sudo kill -9 <PID>
```

## Model Download Issues
```bash
# For Whisper: Models download automatically on first use
# For LibreTranslate: Visit http://localhost:5000 to download models
# For Coqui TTS: Models download automatically
# For Piper: Download models from Hugging Face manually
```

---

# 🎯 Recommendation

**Use Docker** if you:
- Want the easiest setup
- Don't want to manage Python environments
- Need consistent environments
- Want to start/stop services easily

**Use Local Installation** if you:
- Want more control over configurations
- Need specific model versions
- Have GPU acceleration available
- Are comfortable with Python package management

---

**🎉 Docker is the recommended approach for most users - it's faster, more reliable, and handles all dependencies automatically!**
