@echo off
echo ========================================
echo Testing Piper TTS Server
echo ========================================
echo.

echo [1/3] Testing Piper server health...
curl -s http://localhost:5003/health
echo.

echo [2/3] Testing Piper server info...
curl -s http://localhost:5003/
echo.

echo [3/3] Testing synthesis endpoint...
curl -X POST http://localhost:5003/synthesize ^
  -H "Content-Type: application/json" ^
  -d "{\"text\":\"Hello world\",\"voice_model\":\"medium\",\"language\":\"en\"}"
echo.

echo ========================================
echo Test Complete!
echo ========================================
echo.
pause
