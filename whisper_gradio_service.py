import os
import sys
import json
import base64
import tempfile
from pathlib import Path

# Add the gradio_client to path if needed
try:
    from gradio_client import Client, handle_file
except ImportError:
    print("Error: gradio_client not installed. Please run: pip install gradio_client")
    sys.exit(1)

class WhisperGradioService:
    def __init__(self, gradio_url="http://localhost:9000"):
        """
        Initialize the Whisper Gradio service
        """
        self.gradio_url = gradio_url
        self.client = None
        self._connect()
    
    def _connect(self):
        """Connect to the Gradio app"""
        try:
            self.client = Client(self.gradio_url)
            # Don't print connection message to reduce console noise
        except Exception as e:
            print(f"Error: {e}")
            raise
    
    def transcribe_audio(self, wav_file_path, model="Systran/faster-whisper-small", task="transcribe"):
        """
        Transcribe audio using Gradio client
        
        Args:
            wav_file_path: Path to WAV file (already properly formatted)
            model: Whisper model to use
            task: Task type (transcribe/translate)
            
        Returns:
            Transcription result as JSON
        """
        try:
            # Use Gradio client to transcribe with correct parameter names
            result = self.client.predict(
                file_path=handle_file(wav_file_path),  # Correct parameter name
                model=model,
                task=task,
                temperature=0,
                stream=True,
                api_name="/predict"
            )
            
            return json.dumps({
                "success": True,
                "text": result,
                "model": model,
                "task": task
            })
                
        except Exception as e:
            return json.dumps({
                "success": False,
                "error": str(e),
                "model": model,
                "task": task
            })
    
    def _create_wav_header(self, data_size):
        """Create a proper WAV file header for 16kHz, 16-bit, mono audio"""
        import struct
        
        sample_rate = 16000
        channels = 1
        bits_per_sample = 16
        byte_rate = sample_rate * channels * bits_per_sample // 8
        block_align = channels * bits_per_sample // 8
        file_size = 36 + data_size
        
        return struct.pack('<4sL8sLHHLLHH4sL',
            b'RIFF',           # ChunkID
            file_size,         # ChunkSize
            b'WAVE',           # Format
            b'fmt ',           # Subchunk1ID
            16,                # Subchunk1Size (PCM)
            1,                 # AudioFormat (PCM)
            channels,          # NumChannels
            sample_rate,       # SampleRate
            byte_rate,         # ByteRate
            block_align,       # BlockAlign
            bits_per_sample,   # BitsPerSample
            b'data',           # Subchunk2ID
            data_size          # Subchunk2Size
        )
    
    def view_api_info(self):
        """View API information"""
        try:
            if self.client:
                return self.client.view_api()
            else:
                return "Client not connected"
        except Exception as e:
            return f"Error getting API info: {e}"

def main():
    """Main function for command line usage"""
    if len(sys.argv) < 2:
        print("Usage: python whisper_gradio_service.py <command> [args]")
        print("Commands:")
        print("  transcribe <wav_file_path> [model] [task]")
        print("  api_info")
        print("  test")
        sys.exit(1)
    
    command = sys.argv[1].lower()
    service = WhisperGradioService()
    
    if command == "transcribe":
        if len(sys.argv) < 3:
            print("Error: transcribe command requires wav_file_path argument")
            sys.exit(1)
        
        wav_file_path = sys.argv[2]
        model = sys.argv[3] if len(sys.argv) > 3 else "Systran/faster-whisper-small"
        task = sys.argv[4] if len(sys.argv) > 4 else "transcribe"
        
        result = service.transcribe_audio(wav_file_path, model, task)
        print(result)
    
    elif command == "transcribe_from_file":
        # Legacy support - treat the same as transcribe
        if len(sys.argv) < 3:
            print("Error: transcribe_from_file command requires wav_file_path argument")
            sys.exit(1)
        
        wav_file_path = sys.argv[2]
        model = sys.argv[3] if len(sys.argv) > 3 else "Systran/faster-whisper-small"
        task = sys.argv[4] if len(sys.argv) > 4 else "transcribe"
        
        result = service.transcribe_audio(wav_file_path, model, task)
        print(result)
    
    elif command == "api_info":
        print(service.view_api_info())
    
    elif command == "test":
        # Test with a simple audio file path or base64
        print("Testing Whisper Gradio service...")
        try:
            # Try to get API info as a basic test
            api_info = service.view_api_info()
            print("API Info retrieved successfully")
            print(api_info)
        except Exception as e:
            print(f"Test failed: {e}")
    
    else:
        print(f"Unknown command: {command}")
        sys.exit(1)

if __name__ == "__main__":
    main()
