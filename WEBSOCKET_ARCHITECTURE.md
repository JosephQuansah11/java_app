# EchoBridge WebSocket Architecture

## Overview
The EchoBridge application now features a comprehensive WebSocket architecture that supports both raw audio input streaming and processed transcription output in real-time.

## WebSocket Endpoints

### 1. Raw Audio Input Endpoint: `/ws-raw`
- **Purpose**: Receives raw audio chunks from frontend clients
- **Handler**: `WebSocketController`
- **Message Types**:
  - `start_transcription`: Initialize transcription session
  - `stop_transcription`: Stop transcription session
  - `audio_chunk`: Send base64-encoded audio data
  - `configure`: Update session configuration

### 2. Processed Results Endpoint: `/ws-processed`
- **Purpose**: Sends processed transcription results to frontend clients
- **Handler**: `ProcessedTranscriptionWebSocketHandler`
- **Message Types**:
  - `transcription_result`: Complete transcription with translation
  - `partial_transcription`: Real-time partial results
  - `final_transcription`: Final transcription with confidence
  - `processing_status`: Processing status updates
  - `connection`: Connection status messages

## Architecture Flow

```
Frontend Client
    ↓ (audio chunks via /ws-raw)
WebSocketController
    ↓ (feeds audio to Akka Streams)
RealTimeTranslationService
    ↓ (starts Akka Streams pipeline)
AkkaStreamsOrchestrator
    ↓ (processes audio → transcription → translation)
ProcessedTranscriptionWebSocketHandler
    ↓ (sends results via /ws-processed)
Frontend Client
```

## Key Components

### AkkaStreamsOrchestrator
- Manages real-time audio processing pipelines
- Handles parallel transcription and translation
- Routes processed results to WebSocket handlers

### RealTimeTranslationService
- Implements `WebSocketMessageSender` interface
- Coordinates between Akka Streams and WebSocket handlers
- Broadcasts results to both endpoints

### ProcessedTranscriptionWebSocketHandler
- Dedicated handler for processed transcription results
- Supports multiple client connections
- Provides specialized methods for different result types

## Usage Example

### Frontend JavaScript

```javascript
// Connect to raw audio endpoint
const rawWs = new WebSocket('ws://localhost:8080/ws-raw');

// Connect to processed results endpoint
const processedWs = new WebSocket('ws://localhost:8080/ws-processed');

// Start transcription
rawWs.send(JSON.stringify({
    type: 'start_transcription',
    sourceLanguage: 'auto',
    targetLanguage: 'es',
    enableTTS: true
}));

// Send audio chunk
rawWs.send(JSON.stringify({
    type: 'audio_chunk',
    audioData: base64AudioData
}));

// Receive processed results
processedWs.onmessage = function(event) {
    const result = JSON.parse(event.data);
    console.log('Transcription result:', result);
};
```

## Features

1. **Dual WebSocket Architecture**: Separate endpoints for input and output
2. **Real-time Processing**: Akka Streams for high-performance audio processing
3. **Parallel Processing**: Multiple transcription and translation workers
4. **Session Management**: Individual session tracking and routing
5. **Error Handling**: Comprehensive error handling and status reporting
6. **Scalability**: Support for multiple concurrent clients

## Testing

A test client is available at `websocket-test.html` for testing the WebSocket connections and message flow.

## Configuration

The WebSocket endpoints are configured in `WebSocketConfig.java` and automatically registered when the application starts.

## Benefits

- **Separation of Concerns**: Raw audio and processed results are handled separately
- **Scalability**: Multiple clients can connect to either endpoint independently
- **Real-time Performance**: Optimized for low-latency audio processing
- **Flexibility**: Frontend can choose which endpoints to use based on needs
