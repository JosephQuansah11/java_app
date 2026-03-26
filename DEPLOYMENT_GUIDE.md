# EchoBridge Deployment Guide

## Local Deployment (Recommended)

### Prerequisites
- Java 17 or higher
- Docker Desktop installed and running
- Git for Windows (if using bash commands)

### Option 1: Quick Start (Immediate Mode)
This mode works without Docker and uses local services:

1. **Open Command Prompt or PowerShell as Administrator**
2. **Navigate to project directory:**
   ```cmd
   cd c:\Users\Quand\Documents\java_echobridge\java_app
   ```

3. **Run the immediate start script:**
   ```cmd
   start-immediate.bat
   ```

4. **Open browser:** http://localhost:8080

### Option 2: Full Docker Setup
This provides all enhanced features:

1. **Start Docker Desktop** (make sure it's running)

2. **Open Command Prompt as Administrator:**
   ```cmd
   cd c:\Users\Quand\Documents\java_echobridge\java_app
   ```

3. **Start Docker services:**
   ```cmd
   docker-compose up -d
   ```

4. **Wait for services to start (30 seconds):**
   ```cmd
   timeout /t 30
   ```

5. **Start the application:**
   ```cmd
   gradlew.bat bootRun
   ```

6. **Open browser:** http://localhost:8080

### Service URLs (when using Docker)
- Whisper API: http://localhost:9000
- LibreTranslate: http://localhost:5000
- Coqui TTS: http://localhost:5002
- Piper TTS: http://localhost:5003
- EchoBridge App: http://localhost:8080

## Troubleshooting

### If Docker Commands Don't Work
Use the working Docker script:
```cmd
start-working-docker.bat
```

### If Java is Not Found
1. Download Java 17+ from: https://adoptium.net/
2. Install and restart Command Prompt

### If Microphone Doesn't Work
1. Check browser permissions for microphone
2. Ensure no other app is using the microphone
3. Try refreshing the page

### If Services Won't Start
1. Check Docker Desktop is running
2. Stop existing containers:
   ```cmd
   docker stop whisper-api libretranslate coqui-tts piper-tts
   docker rm whisper-api libretranslate coqui-tts piper-tts
   ```
3. Try again

## Cloud Deployment Options

### Option 1: PieHost (Recommended for Web)
1. Go to https://piehost.com/app/v4/register
2. Create a free account
3. Choose a plan (Free plan available)
4. Deploy using Git repository
5. Note: Java apps require paid plan

### Option 2: Railway
1. Go to https://railway.app/
2. Connect GitHub repository
3. Add environment variables for service URLs
4. Deploy

### Option 3: Heroku
1. Install Heroku CLI
2. Create Procfile for web process
3. Set environment variables
4. Deploy: `git push heroku main`

### Option 4: AWS/Azure/GCP
Use container services:
- AWS: ECS or App Runner
- Azure: Container Instances
- GCP: Cloud Run

## Mobile App Deployment

### Option 1: WebView App (Easiest)
Create a simple mobile app that loads the web interface:
- Android: WebView component
- iOS: WKWebView component
- React Native: WebView component

### Option 2: Progressive Web App (PWA)
1. Add manifest.json to resources
2. Implement service worker
3. Enable HTTPS
4. Test on mobile devices

## Environment Variables

Create `.env` file for production:
```
SPRING_PROFILES_ACTIVE=production
WHISPER_API_KEY=your_api_key
LIBRETRANSLATE_URL=https://your-translate-service.com
COQUI_TTS_URL=https://your-tts-service.com
PIPER_TTS_URL=https://your-piper-service.com
```

## Production Configuration

Update `application.properties` for production:
```properties
# Use production URLs
speech.recognition.provider=whisper
whisper.api.url=https://your-whisper-service.com/v1/audio/transcriptions

# Enable all features
tts.provider=coqui
realtime.tts.enable=true

# Security
server.ssl.enabled=true
server.port=443

# Performance
executor.corePoolSize=8
executor.maxPoolSize=16
```

## Testing Deployment

### Health Checks
```bash
# Application health
curl http://localhost:8080/api/health

# Service status
curl http://localhost:8080/api/services/status

# Test transcription
curl -X POST http://localhost:8080/api/microphone/start
```

### Load Testing
```bash
# Install Apache Bench
ab -n 100 -c 10 http://localhost:8080/api/health

# WebSocket testing
wscat -c ws://localhost:8080/ws/transcription
```

## Monitoring

### Application Logs
```bash
# View logs
tail -f logs/application.log

# Check errors
grep "ERROR" logs/application.log
```

### Docker Logs
```bash
# View all service logs
docker-compose logs -f

# Specific service
docker-compose logs -f whisper-api
```

## Security Notes

1. **API Keys:** Never commit API keys to Git
2. **HTTPS:** Use SSL in production
3. **Firewall:** Restrict access to service ports
4. **Authentication:** Add user authentication for production
5. **Rate Limiting:** Implement rate limiting for APIs

## Backup and Recovery

### Data Backup
```bash
# Export Docker volumes
docker run --rm -v whisper_models:/data -v $(pwd):/backup alpine tar czf /backup/whisper-backup.tar.gz -C /data .

# Backup application data
cp -r src/main/resources/ backup/
```

### Recovery
```bash
# Restore volumes
docker run --rm -v whisper_models:/data -v $(pwd):/backup alpine tar xzf /backup/whisper-backup.tar.gz -C /data
```

## Performance Optimization

### JVM Tuning
```bash
export JAVA_OPTS="-Xmx2g -Xms1g -XX:+UseG1GC"
```

### Docker Optimization
```yaml
# In docker-compose.yml
deploy:
  resources:
    limits:
      memory: 1G
    reservations:
      memory: 512M
```

## Support

For issues:
1. Check logs: `logs/application.log`
2. Verify service status: `docker ps`
3. Test connectivity: `curl` commands
4. Check configuration: `application.properties`

---

**Your EchoBridge system is now ready for deployment!**
