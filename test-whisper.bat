@echo off
echo ========================================
echo Testing Whisper API
echo ========================================
echo.

echo [1/3] Testing Whisper API health...
curl -s http://localhost:9000/health
echo.

echo [2/3] Testing Whisper API info...
curl -s http://localhost:9000/
echo.

echo [3/3] Testing Whisper transcription with sample audio...
echo Creating test audio request...
curl -X POST http://localhost:9000/asr ^
  -H "Content-Type: application/json" ^
  -d "{\"audio\":\"\",\"format\":\"audio/wav\",\"sample_rate\":16000,\"language\":\"en\"}"
echo.

echo ========================================
echo Whisper API Test Complete!
echo ========================================
echo.
echo If you see JSON responses above, Whisper is working!
echo Now restart the EchoBridge application:
echo ./gradlew bootRun
echo.
pause
