class RealTimeTranslationClient {
    constructor() {
        this.ws = null;
        this.mediaRecorder = null;
        this.audioContext = null;
        this.microphone = null;
        this.isRecording = false;
        this.isConnecting = false;
        
        // DOM elements
        this.connectBtn = document.getElementById('connectBtn');
        this.startBtn = document.getElementById('startBtn');
        this.stopBtn = document.getElementById('stopBtn');
        this.clearBtn = document.getElementById('clearBtn');
        this.statusEl = document.getElementById('status');
        this.transcriptEl = document.getElementById('transcript');
        this.audioLevelEl = document.getElementById('audioLevel');
        this.audioLevelTextEl = document.getElementById('audioLevelText');
        this.logContentEl = document.getElementById('logContent');
        
        // Settings
        this.sourceLanguageEl = document.getElementById('sourceLanguage');
        this.targetLanguageEl = document.getElementById('targetLanguage');
        this.translationProviderEl = document.getElementById('translationProvider');
        this.ttsVoiceEl = document.getElementById('ttsVoice');
        this.ttsSpeedEl = document.getElementById('ttsSpeed');
        this.ttsSpeedValueEl = document.getElementById('ttsSpeedValue');
        this.enableTTSEl = document.getElementById('enableTTS');
        
        // TTS queue for sequential reading
        this.ttsQueue = [];
        this.isTTSSpeaking = false;
        
        this.initializeEventListeners();
        this.initializeAudioLevel();
    }
    
    initializeEventListeners() {
        this.connectBtn.addEventListener('click', () => this.connect());
        this.startBtn.addEventListener('click', () => this.startRecording());
        this.stopBtn.addEventListener('click', () => this.stopRecording());
        this.clearBtn.addEventListener('click', () => this.clearTranscript());
        
        this.ttsSpeedEl.addEventListener('input', (e) => {
            this.ttsSpeedValueEl.textContent = parseFloat(e.target.value).toFixed(1);
        });
        
        // Load voices when they're ready
        this.synthesis.addEventListener('voiceschanged', () => this.loadVoices());
    }
    
    loadVoices() {
        this.voices = this.synthesis.getVoices();
        console.log('Available voices:', this.voices.length);
    }
    
    connect() {
        if (this.isConnected) {
            this.disconnect();
            return;
        }
        
        try {
            const wsUrl = `ws://${window.location.host}/ws-raw`;
            console.log('🔌 Connecting to WebSocket:', wsUrl);
            this.logWebSocket(`Connecting to: ${wsUrl}`, 'info');
            
            this.ws = new WebSocket(wsUrl);
            
            this.ws.onopen = () => {
                this.isConnected = true;
                this.updateStatus('Connected', 'connected');
                this.connectBtn.textContent = 'Disconnect';
                this.startBtn.disabled = false;
                this.logWebSocket('✅ Connected successfully', 'receive');
                console.log('✅ WebSocket connected successfully');
            };
            
            this.ws.onmessage = (event) => {
                const message = JSON.parse(event.data);
                this.handleMessage(message);
            };
            
            this.ws.onclose = () => {
                this.isConnected = false;
                this.updateStatus('Disconnected', 'disconnected');
                this.connectBtn.textContent = 'Connect';
                this.startBtn.disabled = true;
                this.stopBtn.disabled = true;
                this.logWebSocket('❌ Disconnected', 'error');
                console.log('❌ WebSocket disconnected');
            };
            
            this.ws.onerror = (error) => {
                this.logWebSocket(`❌ Error: ${error}`, 'error');
                console.error('❌ WebSocket error:', error);
                this.updateStatus('Connection Error', 'disconnected');
            };
            
        } catch (error) {
            this.logWebSocket(`❌ Failed to connect: ${error}`, 'error');
            console.error('❌ Failed to connect:', error);
            this.updateStatus('Connection Failed', 'disconnected');
        }
    }
    
    disconnect() {
        if (this.ws) {
            this.ws.close();
            this.ws = null;
        }
    }
    
    sendMessage(message) {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            const messageStr = JSON.stringify(message);
            this.ws.send(messageStr);
            this.logWebSocket(`SEND: ${messageStr}`, 'send');
            console.log('🟢 Frontend SENT:', message);
        } else {
            console.error('WebSocket not connected');
            this.logWebSocket('❌ WebSocket not connected', 'error');
        }
    }
    
    logWebSocket(message, type) {
        if (this.websocketLogEl) {
            const timestamp = new Date().toLocaleTimeString();
            const logEntry = document.createElement('div');
            logEntry.className = `log-${type}`;
            logEntry.textContent = `[${timestamp}] ${message}`;
            this.websocketLogEl.appendChild(logEntry);
            this.websocketLogEl.scrollTop = this.websocketLogEl.scrollHeight;
        }
    }
    
    async startRecording() {
        try {
            const stream = await navigator.mediaDevices.getUserMedia({ 
                audio: {
                    echoCancellation: false,  // Disable for lower latency
                    noiseSuppression: false,  // Disable for lower latency
                    sampleRate: 16000,
                    latency: 0.001  // 1ms hardware latency
                } 
            });
            
            this.audioContext = new (window.AudioContext || window.webkitAudioContext)({
                latencyHint: 'interactive'  // Lowest possible latency
            });
            
            // Reduce buffer size for faster processing (256 samples = 16ms at 16kHz)
            this.bufferSize = 256;
            this.microphone = this.audioContext.createMediaStreamSource(stream);
            
            // Create audio processor with smaller buffer for ultra-low latency
            this.processor = this.audioContext.createScriptProcessor(this.bufferSize, 1, 1);
            this.processor.onaudioprocess = (event) => {
                const inputData = event.inputBuffer.getChannelData(0);
                this.processAudioChunk(inputData);
                this.updateAudioLevel(inputData);
            };
            
            this.microphone.connect(this.processor);
            // Don't connect to destination to avoid playback delay
            // this.processor.connect(this.audioContext.destination);
            
            this.isRecording = true;
            this.updateStatus('Recording...', 'recording');
            this.startBtn.disabled = true;
            this.stopBtn.disabled = false;
            
            // Start transcription session immediately
            this.sendMessage({
                type: 'start_transcription',
                sourceLanguage: this.sourceLanguageEl.value,
                targetLanguage: this.targetLanguageEl.value,
                enableTTS: this.enableTTSEl.checked
            });
            
        } catch (error) {
            console.error('Failed to start recording:', error);
            this.updateStatus('Microphone Access Denied', 'disconnected');
        }
    }
    
    stopRecording() {
        if (this.processor) {
            this.processor.disconnect();
            this.processor = null;
        }
        
        if (this.microphone) {
            this.microphone.disconnect();
            this.microphone = null;
        }
        
        if (this.audioContext) {
            this.audioContext.close();
            this.audioContext = null;
        }
        
        this.isRecording = false;
        this.updateStatus('Connected', 'connected');
        this.startBtn.disabled = false;
        this.stopBtn.disabled = true;
        
        // Stop transcription session
        this.sendMessage({ type: 'stop_transcription' });
    }
    
    processAudioChunk(audioData) {
        // Convert Float32Array to Int16Array for backend
        const int16Data = new Int16Array(audioData.length);
        for (let i = 0; i < audioData.length; i++) {
            int16Data[i] = Math.max(-32768, Math.min(32767, audioData[i] * 32768));
        }
        
        // Convert to base64
        const base64Audio = btoa(String.fromCharCode.apply(null, int16Data));
        
        // Send to server
        this.sendMessage({
            type: 'audio_chunk',
            audioData: base64Audio
        });
    }
    
    updateAudioLevel(audioData) {
        // Calculate RMS for audio level visualization
        let sum = 0;
        for (let i = 0; i < audioData.length; i++) {
            sum += audioData[i] * audioData[i];
        }
        const rms = Math.sqrt(sum / audioData.length);
        const level = Math.min(100, rms * 200);
        this.audioLevelBar.style.width = `${level}%`;
    }
    
    handleMessage(message) {
        const messageStr = JSON.stringify(message);
        this.logWebSocket(`RECV: ${messageStr}`, 'receive');
        console.log('🟢 Frontend RECEIVED:', message);
        
        switch (message.type) {
            case 'connection':
                console.log('Connection message:', message);
                break;
                
            case 'transcription_started':
                console.log('Transcription started');
                break;
                
            case 'transcription_stopped':
                console.log('Transcription stopped');
                break;
                
            case 'transcription_result':
                this.handleTranscriptionResult(message);
                break;
                
            case 'tts_request':
                this.handleTTSRequest(message);
                break;
                
            case 'configuration_updated':
                console.log('Configuration updated:', message);
                break;
                
            case 'error':
                this.logWebSocket(`ERROR: ${message.message}`, 'error');
                console.error('Server error:', message.message);
                this.updateStatus(`Error: ${message.message}`, 'disconnected');
                break;
                
            default:
                this.logWebSocket(`UNKNOWN: ${message.type}`, 'error');
                console.log('Unknown message type:', message);
        }
    }
    
    handleTranscriptionResult(message) {
        // Log transcription details to console (behind the scenes)
        console.log('📝 TRANSCRIPTION (behind scenes):', {
            partialText: message.partialText,
            finalText: message.finalText,
            confidence: message.confidence,
            timestamp: message.timestamp,
            processingMode: message.processingMode
        });
        
        // Only show translation in the UI, hide original transcription
        if (message.translatedText) {
            const translatedDiv = document.createElement('div');
            translatedDiv.className = 'translated-text';
            translatedDiv.textContent = message.translatedText;
            this.transcriptEl.appendChild(translatedDiv);
            console.log('✅ TRANSLATION DISPLAYED:', message.translatedText);
            
            // Trigger TTS for the translated text
            if (this.enableTTSEl.checked && message.translatedText) {
                this.queueTTS(message.translatedText);
            }
        } else {
            console.log('❌ No translatedText in message - showing original for debugging:', message);
            // Fallback: show original if translation fails (for debugging)
            if (message.finalText) {
                const fallbackDiv = document.createElement('div');
                fallbackDiv.className = 'translated-text';
                fallbackDiv.textContent = `[Original] ${message.finalText}`;
                fallbackDiv.style.color = '#ff6b6b';
                this.transcriptEl.appendChild(fallbackDiv);
            }
        }
        
        // Auto-scroll to bottom
        this.transcriptEl.scrollTop = this.transcriptEl.scrollHeight;
    }
    
    triggerBrowserTTS(text) {
        if ('speechSynthesis' in window) {
            // Don't cancel - let it finish naturally
            
            const utterance = new SpeechSynthesisUtterance(text);
            utterance.rate = parseFloat(this.ttsSpeedEl.value);
            utterance.pitch = 1.0;
            utterance.volume = 1.0;
            
            // Set voice if specified
            if (this.ttsVoiceEl.value !== 'default') {
                const voices = window.speechSynthesis.getVoices();
                const selectedVoice = voices.find(voice => 
                    voice.name.toLowerCase().includes(this.ttsVoiceEl.value.toLowerCase())
                );
                if (selectedVoice) {
                    utterance.voice = selectedVoice;
                }
            }
            
            utterance.onend = () => {
                console.log('🔊 TTS finished for:', text);
                this.isTTSSpeaking = false;
                this.processTTSQueue();
            };
            
            utterance.onerror = (error) => {
                console.error('🔊 TTS error:', error);
                this.isTTSSpeaking = false;
                this.processTTSQueue();
            };
            
            this.isTTSSpeaking = true;
            window.speechSynthesis.speak(utterance);
        } else {
            console.warn('🔊 Speech synthesis not supported');
        }
    }
    
    queueTTS(text) {
        console.log('🔊 Queueing TTS for:', text);
        this.ttsQueue.push(text);
        
        if (!this.isTTSSpeaking) {
            this.processTTSQueue();
        }
    }
    
    processTTSQueue() {
        if (this.ttsQueue.length > 0 && !this.isTTSSpeaking) {
            const nextText = this.ttsQueue.shift();
            console.log('🔊 Processing TTS queue item:', nextText);
            this.triggerBrowserTTS(nextText);
        }
    }
    
    handleTTSRequest(message) {
        if (!this.enableTTSEl.checked) return;
        
        if ('speechSynthesis' in window) {
            // Cancel any ongoing speech
            this.synthesis.cancel();
            
            const utterance = new SpeechSynthesisUtterance(message.text);
            
            // Configure voice
            const voice = this.voices.find(v => 
                v.name.includes(this.ttsVoiceEl.value) || 
                v.lang.includes(this.ttsVoiceEl.value)
            );
            
            if (voice) {
                utterance.voice = voice;
            }
            
            utterance.rate = parseFloat(this.ttsSpeedEl.value);
            utterance.pitch = 1.0;
            utterance.volume = 1.0;
            
            this.synthesis.speak(utterance);
            console.log('🔊 TTS played:', message.text);
        }
    }
    
    updateStatus(text, type) {
        this.statusEl.textContent = text;
        this.statusEl.className = `status ${type}`;
    }
    
    clearTranscript() {
        this.transcriptEl.innerHTML = '';
        this.lastPartialText = '';
        console.log('📋 Transcript cleared');
    }
}

// Initialize the client when the page loads
document.addEventListener('DOMContentLoaded', () => {
    console.log('🚀 Initializing Real-time Translation Client...');
    window.translationClient = new RealTimeTranslationClient();
    console.log('✅ Translation client initialized');
});
