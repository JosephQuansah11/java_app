#!/usr/bin/env python3
"""
Simple HTTP Server for Piper TTS
Provides REST API endpoints for text-to-speech synthesis using Piper
"""

import http.server
import socketserver
import json
import urllib.parse
import subprocess
import os
import threading
import time
import sys
from pathlib import Path

class PiperTTSHandler(http.server.SimpleHTTPRequestHandler):
    
    def __init__(self, *args, **kwargs):
        # Initialize piper_path before calling super().__init__
        self.piper_path = None
        super().__init__(*args, **kwargs)
        # Now find piper executable
        self.piper_path = self.find_piper_executable()
        
    def find_piper_executable(self):
        """Find Piper executable in common locations"""
        possible_paths = [
            "/usr/local/bin/piper",
            "/usr/bin/piper", 
            "./piper",
            "/app/piper",
            "piper",
            os.path.expanduser("~/.local/bin/piper")
        ]
        
        for path in possible_paths:
            if os.path.exists(path):
                print(f"Found Piper at: {path}", file=sys.stderr)
                return path
        
        # Try to find in PATH
        try:
            result = subprocess.run(["which", "piper"], capture_output=True, text=True)
            if result.returncode == 0 and result.stdout.strip():
                path = result.stdout.strip()
                print(f"Found Piper in PATH: {path}", file=sys.stderr)
                return path
        except:
            pass
            
        print("Piper executable not found, using mock responses", file=sys.stderr)
        return None
    
    def log_message(self, format, *args):
        """Override to log to stderr instead of stdout"""
        print(f"[{self.log_date_time_string()}] {format % args}", file=sys.stderr)
    
    def do_GET(self):
        """Handle GET requests"""
        parsed_path = urllib.parse.urlparse(self.path)
        
        if parsed_path.path == '/' or parsed_path.path == '':
            self.send_json_response(200, {
                "service": "Piper TTS Server",
                "status": "running",
                "piper_path": self.piper_path,
                "endpoints": ["/synthesize", "/health", "/"],
                "methods": ["GET", "POST"],
                "usage": {
                    "synthesize": {
                        "method": "POST",
                        "url": "/synthesize",
                        "body": {
                            "text": "string (required)",
                            "voice_model": "string (optional, default: medium)",
                            "language": "string (optional, default: en)"
                        }
                    }
                }
            })
        elif parsed_path.path == '/health':
            self.send_json_response(200, {
                "status": "healthy",
                "piper_available": self.piper_path is not None,
                "server": "Python HTTP Server"
            })
        else:
            self.send_json_response(404, {"error": f"Endpoint {self.path} not found"})
    
    def do_POST(self):
        """Handle POST requests"""
        parsed_path = urllib.parse.urlparse(self.path)
        
        if parsed_path.path == '/synthesize':
            self.handle_synthesize()
        else:
            self.send_json_response(404, {"error": f"Endpoint {self.path} not found"})
    
    def handle_synthesize(self):
        """Handle text-to-speech synthesis request"""
        try:
            # Read request body
            content_length = int(self.headers.get('Content-Length', 0))
            post_data = self.rfile.read(content_length)
            
            # Parse JSON
            try:
                data = json.loads(post_data.decode('utf-8'))
            except json.JSONDecodeError as e:
                self.send_json_response(400, {"error": "Invalid JSON", "details": str(e)})
                return
            
            # Extract parameters
            text = data.get('text', '')
            voice_model = data.get('voice_model', 'medium')
            language = data.get('language', 'en')
            
            if not text:
                self.send_json_response(400, {"error": "Text parameter is required"})
                return
            
            print(f"Synthesizing: '{text}' with voice: {voice_model}", file=sys.stderr)
            
            if self.piper_path:
                # Use real Piper
                audio_data = self.synthesize_with_piper(text, voice_model, language)
                if audio_data:
                    self.send_audio_response(audio_data)
                else:
                    self.send_json_response(500, {"error": "Piper synthesis failed"})
            else:
                # Generate a simple silent WAV file when Piper not available
                silent_audio = self.generate_silent_wav(1.0)  # 1 second of silence
                self.send_audio_response(silent_audio)
                
        except Exception as e:
            print(f"Error in synthesis: {e}", file=sys.stderr)
            self.send_json_response(500, {"error": f"Internal server error: {str(e)}"})
    
    def synthesize_with_piper(self, text, voice_model, language):
        """Synthesize speech using Piper command-line tool"""
        try:
            timestamp = int(time.time())
            output_file = f"/tmp/piper_output_{timestamp}.wav"
            text_file = f"/tmp/piper_text_{timestamp}.txt"
            
            # Write text to temporary file
            with open(text_file, 'w') as f:
                f.write(text)
            
            # Build Piper command - adjust model path as needed
            model_path = self.get_model_path(voice_model, language)
            
            cmd = [
                self.piper_path,
                "--model", model_path,
                "--output_file", output_file,
                "--file", text_file
            ]
            
            # Run Piper
            print(f"Running: {' '.join(cmd)}", file=sys.stderr)
            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=30
            )
            
            # Clean up text file
            try:
                os.remove(text_file)
            except:
                pass
            
            if result.returncode == 0 and os.path.exists(output_file):
                # Read audio file
                with open(output_file, 'rb') as f:
                    audio_data = f.read()
                
                # Clean up audio file
                try:
                    os.remove(output_file)
                except:
                    pass
                
                return audio_data
            else:
                print(f"Piper failed: {result.stderr}", file=sys.stderr)
                return None
                
        except subprocess.TimeoutExpired:
            print("Piper synthesis timed out", file=sys.stderr)
            return None
        except Exception as e:
            print(f"Piper synthesis error: {e}", file=sys.stderr)
            return None
    
    def generate_silent_wav(self, duration_seconds):
        """Generate a simple silent WAV file"""
        import struct
        
        sample_rate = 16000
        num_samples = int(sample_rate * duration_seconds)
        
        # WAV header
        header = struct.pack('<4sL4s4sLHHLLHH4sL',
            b'RIFF',           # ChunkID
            36 + num_samples * 2, # ChunkSize
            b'WAVE',           # Format
            b'fmt ',           # Subchunk1ID
            16,                # Subchunk1Size
            1,                 # AudioFormat (PCM)
            1,                 # NumChannels
            sample_rate,       # SampleRate
            sample_rate * 2,   # ByteRate
            2,                 # BlockAlign
            16,                # BitsPerSample
            b'data',           # Subchunk2ID
            num_samples * 2    # Subchunk2Size
        )
        
        # Silent audio data (all zeros)
        silent_data = b'\x00\x00' * num_samples
        
        return header + silent_data
    
    def get_model_path(self, voice_model, language):
        """Get path to voice model file"""
        # Map common model names to actual file paths
        model_dir = os.environ.get('PIPER_MODEL_DIR', './models')
        
        model_mapping = {
            'medium': f'{model_dir}/en_US-lessac-medium.onnx',
            'small': f'{model_dir}/en_US-lessac-small.onnx',
            'high': f'{model_dir}/en_US-lessac-high.onnx',
            'default': f'{model_dir}/en_US-lessac-medium.onnx'
        }
        
        if voice_model in model_mapping:
            return model_mapping[voice_model]
        
        # If voice_model looks like a path, use it directly
        if os.path.exists(voice_model):
            return voice_model
            
        # Default fallback
        return model_mapping.get('default', voice_model)
    
    def send_json_response(self, status_code, data):
        """Send JSON response"""
        self.send_response_headers(status_code, 'application/json')
        response = json.dumps(data).encode('utf-8')
        self.wfile.write(response)
    
    def send_audio_response(self, audio_data):
        """Send audio file response"""
        self.send_response_headers(200, 'audio/wav')
        self.wfile.write(audio_data)
    
    def send_response_headers(self, status_code, content_type):
        """Send response headers"""
        self.send_response(status_code)
        self.send_header('Content-Type', content_type)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()

def find_available_port(preferred_port=5003, fallback_port=5013):
    """Find an available port, preferring preferred_port, then fallback_port"""
    ports_to_try = [preferred_port, fallback_port]
    
    for port in ports_to_try:
        try:
            # Test if port is available
            test_socket = socketserver.TCPServer(("", port), None, bind_and_activate=False)
            test_socket.server_bind()
            test_socket.server_close()
            print(f"Port {port} is available", file=sys.stderr)
            return port
        except OSError as e:
            print(f"Port {port} is in use or unavailable: {e}", file=sys.stderr)
            continue
    
    # If both are taken, find any available port
    print("Preferred ports in use, finding any available port...", file=sys.stderr)
    test_socket = socketserver.TCPServer(("", 0), None, bind_and_activate=False)
    test_socket.server_bind()
    port = test_socket.server_address[1]
    test_socket.server_close()
    print(f"Using available port: {port}", file=sys.stderr)
    return port

def run_server():
    """Run the HTTP server with automatic port selection"""
    # Check command line args first
    if len(sys.argv) > 1:
        try:
            preferred_port = int(sys.argv[1])
        except ValueError:
            preferred_port = 5003
    else:
        preferred_port = 5003
    
    fallback_port = 5013
    
    # Find available port
    port = find_available_port(preferred_port, fallback_port)
    
    handler = PiperTTSHandler
    
    # Allow server to reuse address
    socketserver.TCPServer.allow_reuse_address = True
    server_address = ('', port)
    
    with socketserver.TCPServer(server_address, handler) as httpd:
        # Print the actual port to stdout for Gradle to capture
        print(f"PORT:{port}")
        sys.stdout.flush()
        
        print(f"Piper TTS Server running on port {port}", file=sys.stderr)
        print(f"Available endpoints:", file=sys.stderr)
        print(f"  GET  /           - Server info", file=sys.stderr)
        print(f"  GET  /health      - Health check", file=sys.stderr)
        print(f"  POST /synthesize  - Text-to-speech synthesis", file=sys.stderr)
        print(f"", file=sys.stderr)
        
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\nShutting down server...", file=sys.stderr)
            httpd.shutdown()

if __name__ == "__main__":
    run_server()