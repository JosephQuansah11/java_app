@echo off
echo ========================================
echo Setting up Python Whisper Gradio Service
echo ========================================
echo.

echo [1/4] Checking Python installation...
python --version
if %errorlevel% neq 0 (
    echo ERROR: Python is not installed or not in PATH
    echo Please install Python 3.10 or higher
    pause
    exit /b 1
)

echo.
echo [2/4] Installing gradio_client...
pip install --upgrade gradio_client
if %errorlevel% neq 0 (
    echo ERROR: Failed to install gradio_client
    pause
    exit /b 1
)

echo.
echo [3/4] Testing Python Whisper service...
python whisper_gradio_service.py test
if %errorlevel% neq 0 (
    echo WARNING: Python Whisper service test failed
    echo Make sure Whisper Gradio app is running on http://localhost:9000
)

echo.
echo [4/4] Setup complete!
echo.
echo The Python Whisper Gradio service is ready.
echo Make sure the Whisper Gradio app is running on http://localhost:9000
echo.
echo Usage examples:
echo   python whisper_gradio_service.py api_info
echo   python whisper_gradio_service.py test
echo.
pause
