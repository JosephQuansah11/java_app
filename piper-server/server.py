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
from pathlib import Path

class PiperTTSHandler(http.server.SimpleHTTPRequestHandler):
    
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.piper_path = self.find_piper_executable()
        
    def find_piper_executable(self):
        """Find Piper executable in common locations"""
        possible_paths = [
            "/usr/local/bin/piper",
            "/usr/bin/piper", 
            "./piper",
            "/app/piper"
        ]
        
        for path in possible_paths:
            if os.path.exists(path):
                print(f"Found Piper at: {path}")
                return path
        
        print("Piper executable not found, using mock responses")
        return None
    
    def do_GET(self):
        """Handle GET requests"""
        parsed_path = urllib.parse.urlparse(self.path)
        
        if parsed_path.path == '/' or parsed_path.path == '':
            self.send_response(200, {
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
            self.send_response(200, {
                "status": "healthy",
                "piper_available": self.piper_path is not None,
                "server": "Python HTTP Server"
            })
        else:
            self.send_response(404, {"error": f"Endpoint {self.path} not found"})
    
    def do_POST(self):
        """Handle POST requests"""
        parsed_path = urllib.parse.urlparse(self.path)
        
        if parsed_path.path == '/synthesize':
            self.handle_synthesize()
        else:
            self.send_response(404, {"error": f"Endpoint {self.path} not found"})
    
    def handle_synthesize(self):
        """Handle text-to-speech synthesis request"""
        try:
            # Read request body
            content_length = int(self.headers.get('Content-Length', 0))
            post_data = self.rfile.read(content_length)
            
            # Parse JSON
            try:
                data = json.loads(post_data.decode('utf-8'))
            except json.JSONDecodeError:
                self.send_response(400, {"error": "Invalid JSON"})
                return
            
            # Extract parameters
            text = data.get('text', '')
            voice_model = data.get('voice_model', 'medium')
            language = data.get('language', 'en')
            
            if not text:
                self.send_response(400, {"error": "Text parameter is required"})
                return
            
            print(f"Synthesizing: '{text}' with voice: {voice_model}")
            
            if self.piper_path:
                # Use real Piper
                audio_data = self.synthesize_with_piper(text, voice_model, language)
                if audio_data:
                    self.send_audio_response(audio_data)
                else:
                    self.send_response(500, {"error": "Piper synthesis failed"})
            else:
                # Mock response when Piper not available
                self.send_response(200, {
                    "text": text,
                    "voice_model": voice_model,
                    "language": language,
                    "mock": True,
                    "message": "Piper not available, returning mock response",
                    "audio_length": 0
                })
                
        except Exception as e:
            print(f"Error in synthesis: {e}")
            self.send_response(500, {"error": f"Internal server error: {str(e)}"})
    
    def synthesize_with_piper(self, text, voice_model, language):
        """Synthesize speech using Piper command-line tool"""
        try:
            # Create output file path
            output_file = f"/tmp/piper_output_{int(time.time())}.wav"
            
            # Build Piper command
            cmd = [
                self.piper_path,
                "--model", f"{voice_model}",
                "--output_file", output_file
            ]
            
            # Write text to temporary file
            text_file = f"/tmp/piper_text_{int(time.time())}.txt"
            with open(text_file, 'w') as f:
                f.write(text)
            
            cmd.extend(["--file", text_file])
            
            # Run Piper
            print(f"Running: {' '.join(cmd)}")
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
                print(f"Piper failed: {result.stderr}")
                return None
                
        except subprocess.TimeoutExpired:
            print("Piper synthesis timed out")
            return None
        except Exception as e:
            print(f"Piper synthesis error: {e}")
            return None
    
    def send_response(self, status_code, data):
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
        self.send_response_only(status_code)
        self.send_header('Content-Type', content_type)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()
    
    def send_response_only(self, status_code):
        """Send status code only"""
        self.send_response(status_code)
        self.send_header('Server', 'Piper-TTS-Server/1.0')
        self.send_header('Date', self.date_time_string())
        self.end_headers()
    
    def send_header(self, name, value):
        """Send a single header"""
        self.wfile.write(f"{name}: {value}\r\n".encode('utf-8'))
    
    def end_headers(self):
        """End header section"""
        self.wfile.write(b"\r\n")
    
    def date_time_string(self):
        """Get current date/time string"""
        return time.strftime("%a, %d %b %Y %H:%M:%S GMT", time.gmtime())

def run_server(port=8000):
    """Run the HTTP server"""
    handler = PiperTTSHandler
    
    # Allow server to reuse address
    server_address = ('', port)
    
    with socketserver.TCPServer(server_address, handler) as httpd:
        print(f"Piper TTS Server running on port {port}")
        print("Available endpoints:")
        print("  GET  /           - Server info")
        print("  GET  /health      - Health check")
        print("  POST /synthesize  - Text-to-speech synthesis")
        print()
        
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\nShutting down server...")
            httpd.shutdown()

if __name__ == "__main__":
    import sys
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
    run_server(port)
