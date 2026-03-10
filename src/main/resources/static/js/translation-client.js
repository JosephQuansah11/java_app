class RealTimeTranslationClient {
    constructor() {
        this.rawWs = null;  // WebSocket for raw audio input
        this.processedWs = null;  // WebSocket for processed results
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
        
        // Test if transcript element exists
        if (!this.transcriptEl) {
            console.error('❌ CRITICAL ERROR: transcript element not found!');
            return;
        } else {
            console.log('✅ transcript element found:', this.transcriptEl);
        }
        
        this.audioLevelEl = document.getElementById('audioLevel');
        this.audioLevelTextEl = document.getElementById('audioLevelText'); // This doesn't exist in HTML
        this.logContentEl = document.getElementById('logContent'); // This doesn't exist in HTML
        this.websocketLogEl = document.getElementById('websocketLog');
        
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
        
        // Message queue for high-frequency processing
        this.messageQueue = [];
        this.isProcessingMessages = false;
        
        this.initializeEventListeners();
        this.initializeAudioLevel();
    }
    
    initializeAudioLevel() {
        // Initialize audio level elements
        this.audioLevelBar = document.getElementById('audioLevelBar');
        if (!this.audioLevelBar) {
            console.warn('Audio level bar element not found');
        }
    }
    
    initializeEventListeners() {
        // Check if elements exist before adding listeners
        if (this.connectBtn) {
            this.connectBtn.addEventListener('click', () => this.connect());
        }
        if (this.startBtn) {
            this.startBtn.addEventListener('click', () => this.startRecording());
        }
        if (this.stopBtn) {
            this.stopBtn.addEventListener('click', () => this.stopRecording());
        }
        if (this.clearBtn) {
            this.clearBtn.addEventListener('click', () => this.clearTranscript());
        }
        
        if (this.ttsSpeedEl) {
            this.ttsSpeedEl.addEventListener('input', (e) => {
                this.ttsSpeedValueEl.textContent = parseFloat(e.target.value).toFixed(1);
            });
        }
        
        // Load voices when they're ready
        if (this.synthesis) {
            this.synthesis.addEventListener('voiceschanged', () => this.loadVoices());
        }
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
            // Connect to raw WebSocket for audio input
            const rawWsUrl = `ws://${window.location.host}/ws-raw`;
            console.log('🔌 Connecting to RAW WebSocket:', rawWsUrl);
            this.logWebSocket(`Connecting to RAW: ${rawWsUrl}`, 'info');
            
            this.rawWs = new WebSocket(rawWsUrl);
            
            this.rawWs.onopen = () => {
                console.log('✅ RAW WebSocket connected');
                
                // Now connect to processed WebSocket
                this.connectProcessedWebSocket();
            };
            
            this.rawWs.onmessage = (event) => {
                const message = JSON.parse(event.data);
                console.log('📨 RAW WebSocket message received:', event.data);
                this.handleRawMessage(message);
            };
            
            this.rawWs.onclose = () => {
                console.log('❌ RAW WebSocket disconnected');
                this.disconnect();
            };
            
            this.rawWs.onerror = (error) => {
                console.error('❌ RAW WebSocket error:', error);
                this.logWebSocket(`❌ RAW Error: ${error}`, 'error');
                this.updateStatus('Connection Error', 'disconnected');
            };
            
        } catch (error) {
            this.logWebSocket(`❌ Failed to connect: ${error}`, 'error');
            console.error('❌ Failed to connect:', error);
            this.updateStatus('Connection Failed', 'disconnected');
        }
    }
    
    connectProcessedWebSocket() {
        try {
            const processedWsUrl = `ws://${window.location.host}/ws-processed`;
            console.log('� Connecting to PROCESSED WebSocket:', processedWsUrl);
            this.logWebSocket(`Connecting to PROCESSED: ${processedWsUrl}`, 'info');
            
            this.processedWs = new WebSocket(processedWsUrl);
            
            this.processedWs.onopen = () => {
                console.log('✅ PROCESSED WebSocket connected successfully!');
                this.isConnected = true;
                this.updateStatus('Connected', 'connected');
                this.connectBtn.textContent = 'Disconnect';
                this.startBtn.disabled = false;
                this.logWebSocket('✅ Both WebSockets connected successfully', 'receive');
                console.log('✅ Both WebSockets connected successfully');
            };
            
            this.processedWs.onmessage = (event) => {
                console.log('📨 PROCESSED WebSocket message received:', event.data);
                
                let message;
                try {
                    message = JSON.parse(event.data);
                    console.log('📨 Message type:', message.type);
                    console.log('📨 Message finalText:', message.finalText);
                    console.log('📨 Message translatedText:', message.translatedText);
                } catch (error) {
                    console.error('❌ JSON parse error:', error, 'Raw data:', event.data);
                    return;
                }
                
                // Add a visible test message to the transcript
                const testDiv = document.createElement('div');
                testDiv.innerHTML = `🔔 DEBUG: Type=${message.type}, Final="${message.finalText}", Trans="${message.translatedText}"`;
                testDiv.style.color = 'red';
                testDiv.style.fontWeight = 'bold';
                testDiv.style.marginBottom = '5px';
                this.transcriptEl.appendChild(testDiv);
                
                // Process WebSocket message and display results directly
                switch (message.type) {
                    case 'transcription_result':
                    case 'final_transcription':
                        console.log('🎯 Displaying transcription result:', message);
                        console.log('🎯 Has data field:', !!message.data);
                        
                        // Get actual transcription data from data field or direct fields
                        let transcriptionData = message.data || message;
                        console.log('🎯 Using transcription data:', transcriptionData);
                        
                        // Create result container with clean styling
                        const resultContainer = document.createElement('div');
                        resultContainer.style.cssText = `
                            background: #f8f9fa;
                            border: 1px solid #e0e0e0;
                            border-radius: 8px;
                            padding: 15px;
                            margin-bottom: 15px;
                        `;
                        
                        // Add original text
                        if (transcriptionData.finalText && transcriptionData.finalText.trim()) {
                            const originalDiv = document.createElement('div');
                            originalDiv.innerHTML = `<strong>🎤 Original:</strong> ${transcriptionData.finalText}`;
                            originalDiv.style.cssText = `
                                color: #333;
                                font-size: 14px;
                                margin-bottom: 8px;
                            `;
                            resultContainer.appendChild(originalDiv);
                        }
                        
                        // Add translated text
                        if (transcriptionData.translatedText && transcriptionData.translatedText.trim()) {
                            const translatedDiv = document.createElement('div');
                            translatedDiv.innerHTML = `<strong>🌐 Translated:</strong> ${transcriptionData.translatedText}`;
                            translatedDiv.style.cssText = `
                                color: #0066cc;
                                font-size: 14px;
                                font-weight: 500;
                            `;
                            resultContainer.appendChild(translatedDiv);
                            
                            // Trigger TTS if enabled
                            if (this.enableTTSEl && this.enableTTSEl.checked) {
                                this.queueTTS(transcriptionData.translatedText);
                            }
                        }
                        
                        // Add to transcript and scroll
                        this.transcriptEl.appendChild(resultContainer);
                        this.transcriptEl.scrollTop = this.transcriptEl.scrollHeight;
                        
                        console.log('✅ Transcription result displayed successfully!');
                        break;
                    case 'partial_transcription':
                        this.handlePartialTranscription(message);
                        break;
                    case 'processing_status':
                        this.handleProcessingStatus(message);
                        break;
                    case 'connection':
                        console.log('🔗 PROCESSED Connection message:', message);
                        break;
                    case 'error':
                        this.logWebSocket(`PROCESSED ERROR: ${message.message}`, 'error');
                        console.error('❌ PROCESSED Server error:', message.message);
                        break;
                    default:
                        console.log('❓ Unknown PROCESSED message type:', message.type);
                }
            };
            
            this.processedWs.onclose = () => {
                console.log('❌ PROCESSED WebSocket disconnected');
                this.disconnect();
            };
            
            this.processedWs.onerror = (error) => {
                console.error('❌ PROCESSED WebSocket error:', error);
                this.logWebSocket(`❌ PROCESSED Error: ${error}`, 'error');
                this.updateStatus('Connection Error', 'disconnected');
            };
            
        } catch (error) {
            this.logWebSocket(`❌ Failed to connect processed WebSocket: ${error}`, 'error');
            console.error('❌ Failed to connect processed WebSocket:', error);
        }
    }
    
    processMessageQueue() {
        if (this.messageQueue.length > 0 && !this.isProcessingMessages) {
            this.isProcessingMessages = true;
            const message = this.messageQueue.shift();
            
            // Process the message based on type
            switch (message.type) {
                case 'transcription_result':
                case 'final_transcription':
                    this.handleTranscriptionResult(message);
                    break;
                case 'partial_transcription':
                    this.handlePartialTranscription(message);
                    break;
                case 'processing_status':
                    this.handleProcessingStatus(message);
                    break;
                default:
                    console.log('❓ Unknown queued message type:', message.type);
            }
            
            this.isProcessingMessages = false;
        }
    }
    
    handlePartialTranscription(message) {
        console.log('📝 Partial transcription received:', message);
        // Display partial results if needed
        if (message.partialText) {
            const partialDiv = document.createElement('div');
            partialDiv.className = 'partial-text';
            partialDiv.textContent = `[Partial] ${message.partialText}`;
            partialDiv.style.color = '#888';
            partialDiv.style.fontStyle = 'italic';
            this.transcriptEl.appendChild(partialDiv);
            this.transcriptEl.scrollTop = this.transcriptEl.scrollHeight;
        }
    }
    
    handleProcessingStatus(message) {
        console.log('📊 Processing status:', message);
        // Update UI with processing status if needed
        if (message.status && message.message) {
            this.updateStatus(message.message, 'recording');
        }
    }
    
    disconnect() {
        if (this.rawWs) {
            this.rawWs.close();
            this.rawWs = null;
        }
        if (this.processedWs) {
            this.processedWs.close();
            this.processedWs = null;
        }
        
        // Clear message processing interval
        if (this.messageProcessingInterval) {
            clearInterval(this.messageProcessingInterval);
            this.messageProcessingInterval = null;
        }
        
        this.isConnected = false;
        this.updateStatus('Disconnected', 'disconnected');
        this.connectBtn.textContent = 'Connect';
        this.startBtn.disabled = true;
        this.stopBtn.disabled = true;
        this.logWebSocket('❌ Disconnected', 'error');
        console.log('❌ Both WebSockets disconnected');
    }
    
    sendMessage(message) {
        if (this.rawWs && this.rawWs.readyState === WebSocket.OPEN) {
            const messageStr = JSON.stringify(message);
            this.rawWs.send(messageStr);
            this.logWebSocket(`SEND: ${messageStr}`, 'send');
            console.log('🟢 Frontend SENT via RAW:', message);
        } else {
            console.error('RAW WebSocket not connected');
            this.logWebSocket('❌ RAW WebSocket not connected', 'error');
        }
    }
    
    handleRawMessage(message) {
        const messageStr = JSON.stringify(message);
        this.logWebSocket(`RAW RECV: ${messageStr}`, 'receive');
        console.log('🟢 Frontend received RAW message:', message);
        
        switch (message.type) {
            case 'connection':
                console.log('🔗 RAW Connection message:', message);
                break;
            case 'transcription_started':
                console.log('✅ Transcription started');
                break;
            case 'transcription_stopped':
                console.log('🛑 Transcription stopped');
                break;
            case 'configuration_updated':
                console.log('⚙️ Configuration updated:', message);
                break;
            case 'error':
                this.logWebSocket(`RAW ERROR: ${message.message}`, 'error');
                console.error('❌ RAW Server error:', message.message);
                this.updateStatus(`Error: ${message.message}`, 'disconnected');
                break;
            default:
                console.log('❓ Unknown RAW message type:', message.type);
        }
    }
    
    handleProcessedMessage(message) {
        const messageStr = JSON.stringify(message);
        this.logWebSocket(`PROCESSED RECV: ${messageStr}`, 'receive');
        console.log('🟢 Frontend received PROCESSED message:', message);
        
        // Queue message for high-frequency processing (3ms interval)
        this.messageQueue.push(message);
        
        // Limit queue size to prevent memory issues
        if (this.messageQueue.length > 100) {
            this.messageQueue.shift(); // Remove oldest message
        }
    }
    
    handleMessage(message) {
        // Legacy method - delegate to appropriate handler
        // This maintains compatibility with existing code
        this.handleRawMessage(message);
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
        
        // Update audio level bar if it exists
        if (this.audioLevelBar) {
            this.audioLevelBar.style.width = `${level}%`;
        }
        
        // Update audio level text if it exists
        if (this.audioLevelTextEl) {
            this.audioLevelTextEl.textContent = `${Math.round(level)}%`;
        }
    }
    
    handleMessage(message) {
        const messageStr = JSON.stringify(message);
        this.logWebSocket(`RECV: ${messageStr}`, 'receive');
        console.log('🟢 Frontend RECEIVED message:', message);
        console.log('🟢 Message type:', message.type);
        console.log('🟢 Message keys:', Object.keys(message));
        
        switch (message.type) {
            case 'connection':
                console.log('🔗 Connection message:', message);
                break;
                
            case 'transcription_started':
                console.log('✅ Transcription started');
                break;
                
            case 'transcription_stopped':
                console.log('🛑 Transcription stopped');
                break;
                
            case 'transcription_result':
                console.log('📝 TRANSCRIPTION_RESULT received:', message);
                console.log('📝 finalText:', message.finalText);
                console.log('📝 translatedText:', message.translatedText);
                console.log('📝 confidence:', message.confidence);
                this.handleTranscriptionResult(message);
                break;
                
            case 'tts_request':
                console.log('🔊 TTS_REQUEST received:', message);
                this.handleTTSRequest(message);
                break;
                
            case 'configuration_updated':
                console.log('⚙️ Configuration updated:', message);
                break;
                
            case 'error':
                this.logWebSocket(`ERROR: ${message.message}`, 'error');
                console.error('❌ Server error:', message.message);
                this.updateStatus(`Error: ${message.message}`, 'disconnected');
                break;
                
            default:
                this.logWebSocket(`UNKNOWN: ${message.type}`, 'error');
                console.log('❓ Unknown message type:', message.type);
                console.log('❓ Full message:', message);
        }
    }
    
    handleTranscriptionResult(message) {
        // Log transcription details to console (behind the scenes)
        console.log('🎯 handleTranscriptionResult called with:', message);
        console.log('🎯 finalText:', message.finalText);
        console.log('🎯 translatedText:', message.translatedText);
        console.log('🎯 confidence:', message.confidence);
        console.log('🎯 processingMode:', message.processingMode);
        
        console.log('🎯 About to create result container...');
        
        // Create a container for both original and translated text
        const resultContainer = document.createElement('div');
        resultContainer.className = 'transcription-result';
        resultContainer.style.marginBottom = '15px';
        resultContainer.style.padding = '10px';
        resultContainer.style.border = '1px solid #e0e0e0';
        resultContainer.style.borderRadius = '8px';
        resultContainer.style.backgroundColor = '#f8f9fa';
        
        console.log('🎯 Result container created:', resultContainer);
        
        // Show original text
        if (message.finalText && message.finalText.trim() !== '') {
            console.log('🎯 Adding original text:', message.finalText);
            const originalDiv = document.createElement('div');
            originalDiv.className = 'original-text';
            originalDiv.innerHTML = '<strong>🎤 Original:</strong> ' + message.finalText;
            originalDiv.style.marginBottom = '8px';
            originalDiv.style.color = '#333';
            originalDiv.style.fontSize = '14px';
            resultContainer.appendChild(originalDiv);
        }
        
        // Show translated text from LibreTranslate
        if (message.translatedText && message.translatedText.trim() !== '') {
            console.log('🎯 Adding translated text:', message.translatedText);
            const translatedDiv = document.createElement('div');
            translatedDiv.className = 'translated-text';
            translatedDiv.innerHTML = '<strong>🌐 Translated:</strong> ' + message.translatedText;
            translatedDiv.style.color = '#0066cc';
            translatedDiv.style.fontSize = '14px';
            translatedDiv.style.fontWeight = '500';
            resultContainer.appendChild(translatedDiv);
            console.log('✅ TRANSLATION DISPLAYED in UI:', message.translatedText);
            
            // Trigger TTS for the translated text
            console.log('🎯 Checking TTS element:', this.enableTTSEl);
            console.log('🎯 TTS element exists:', !!this.enableTTSEl);
            console.log('🎯 TTS element checked:', this.enableTTSEl ? this.enableTTSEl.checked : 'null');
            
            if (this.enableTTSEl && this.enableTTSEl.checked) {
                console.log('🔊 TTS enabled, queueing:', message.translatedText);
                this.queueTTS(message.translatedText);
            } else {
                console.log('🔊 TTS disabled, skipping');
            }
        } else {
            console.log('❌ No translatedText available');
        }
        
        console.log('🎯 About to append to transcript element:', this.transcriptEl);
        
        // Add the result container to the transcript
        this.transcriptEl.appendChild(resultContainer);
        
        console.log('🎯 Result container appended');
        
        // SIMPLE FALLBACK TEST: Just try to add text directly
        const fallbackTest = document.createElement('div');
        fallbackTest.innerHTML = `🔥 FALLBACK TEST: finalText="${message.finalText}" translatedText="${message.translatedText}"`;
        fallbackTest.style.color = 'green';
        fallbackTest.style.border = '2px solid red';
        fallbackTest.style.padding = '10px';
        fallbackTest.style.margin = '10px 0';
        this.transcriptEl.appendChild(fallbackTest);
        
        // Auto-scroll to bottom
        this.transcriptEl.scrollTop = this.transcriptEl.scrollHeight;
        console.log('📜 Auto-scrolled to bottom');
    }
    
    triggerBrowserTTS(text) {
        console.log('🔊 triggerBrowserTTS called with:', text);
        
        if ('speechSynthesis' in window) {
            console.log('🔊 Speech synthesis supported');
            // Don't cancel - let it finish naturally
            
            const utterance = new SpeechSynthesisUtterance(text);
            utterance.rate = parseFloat(this.ttsSpeedEl.value);
            utterance.pitch = 1.0;
            utterance.volume = 1.0;
            
            console.log('🔊 TTS settings - rate:', utterance.rate, 'pitch:', utterance.pitch, 'volume:', utterance.volume);
            
            // Set voice if specified
            if (this.ttsVoiceEl.value !== 'default') {
                const voices = window.speechSynthesis.getVoices();
                const selectedVoice = voices.find(voice => 
                    voice.name.toLowerCase().includes(this.ttsVoiceEl.value.toLowerCase())
                );
                if (selectedVoice) {
                    utterance.voice = selectedVoice;
                    console.log('🔊 Using voice:', selectedVoice.name);
                } else {
                    console.log('🔊 Voice not found, using default');
                }
            }
            
            utterance.onstart = () => {
                console.log('🔊 TTS STARTED for:', text);
            };
            
            utterance.onend = () => {
                console.log('🔊 TTS FINISHED for:', text);
                this.isTTSSpeaking = false;
                this.processTTSQueue();
            };
            
            utterance.onerror = (error) => {
                console.error('🔊 TTS ERROR:', error);
                this.isTTSSpeaking = false;
                this.processTTSQueue();
            };
            
            this.isTTSSpeaking = true;
            console.log('🔊 Calling speechSynthesis.speak()');
            window.speechSynthesis.speak(utterance);
        } else {
            console.warn('🔊 Speech synthesis not supported in this browser');
        }
    }
    
    queueTTS(text) {
        console.log('🔊 queueTTS called with:', text);
        // Avoid queueing duplicate or very similar text
        if (this.ttsQueue.length > 0 && this.ttsQueue[this.ttsQueue.length - 1] === text) {
            console.log('🔊 Duplicate text, skipping queue');
            return;
        }
        
        console.log('🔊 Queueing TTS for:', text);
        console.log('🔊 Current queue length:', this.ttsQueue.length);
        this.ttsQueue.push(text);
        
        // Limit queue size to prevent memory issues
        if (this.ttsQueue.length > 10) {
            this.ttsQueue.shift(); // Remove oldest
            console.log('🔊 Queue trimmed to 10 items');
        }
        
        if (!this.isTTSSpeaking) {
            console.log('🔊 Not currently speaking, processing queue immediately');
            this.processTTSQueue();
        } else {
            console.log('🔊 Currently speaking, added to queue');
        }
    }
    
    processTTSQueue() {
        console.log('🔊 processTTSQueue called');
        console.log('🔊 Queue length:', this.ttsQueue.length);
        console.log('🔊 isTTSSpeaking:', this.isTTSSpeaking);
        
        if (this.ttsQueue.length > 0 && !this.isTTSSpeaking) {
            const nextText = this.ttsQueue.shift();
            console.log('🔊 Processing TTS queue item:', nextText);
            
            // Small delay to ensure smooth transition between utterances
            setTimeout(() => {
                console.log('🔊 Triggering browser TTS for:', nextText);
                this.triggerBrowserTTS(nextText);
            }, 100);
        } else {
            console.log('🔊 Queue empty or already speaking');
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
