package echobridge.com.java_app.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sound.sampled.LineUnavailableException;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import echobridge.com.java_app.core.services.RealTimeTranslationService;
import echobridge.com.java_app.domain.source.MicrophoneSource;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class WebSocketController extends TextWebSocketHandler {

    private final RealTimeTranslationService translationService;
    private final MicrophoneSource microphoneSource;
    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public WebSocketController(RealTimeTranslationService translationService, 
                              MicrophoneSource microphoneSource,
                              ObjectMapper objectMapper) {
        this.translationService = translationService;
        this.microphoneSource = microphoneSource;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        log.info("WebSocket connection established: {}", session.getId());
        
        // Send initial connection message
        sendMessage(session, Map.of(
            "type", "connection",
            "status", "connected",
            "sessionId", session.getId()
        ));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        log.info("🔌 WebSocket connection CLOSED: {} - Status: {} - Reason: {}", 
            session.getId(), status, status.getReason());
        
        // Stop microphone if this was the last active session
        if (sessions.isEmpty()) {
            log.info("🛑 No more active sessions - checking microphone status");
            // Note: We don't auto-stop microphone anymore - let user control it
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            String payloadStr = message.getPayload();
            Map<String, Object> payload = objectMapper.readValue(payloadStr, Map.class);
            String type = (String) payload.get("type");
            
            // Don't log full audio chunk data - only log type
            if ("audio_chunk".equals(type)) {
                log.debug("🔵 WebSocket RECEIVED audio_chunk from {}", session.getId());
            } else {
                log.info("🔵 WebSocket RECEIVED {} from {}: {}", type, session.getId(), payloadStr);
            }
            
            switch (type) {
                case "start_transcription":
                    handleStartTranscription(session, payload);
                    break;
                case "stop_transcription":
                    handleStopTranscription(session, payload);
                    break;
                case "audio_chunk":
                    handleAudioChunk(session, payload);
                    break;
                case "configure":
                    handleConfiguration(session, payload);
                    break;
                default:
                    log.warn("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            log.error("Error handling WebSocket message", e);
            sendMessage(session, Map.of(
                "type", "error",
                "message", "Failed to process message: " + e.getMessage()
            ));
        }
    }

    private void handleStartTranscription(WebSocketSession session, Map<String, Object> payload) {
        String sourceLanguage = (String) payload.getOrDefault("sourceLanguage", "auto");
        String targetLanguage = (String) payload.getOrDefault("targetLanguage", "es");
        boolean enableTTS = (Boolean) payload.getOrDefault("enableTTS", true);
        
        // Start microphone when client requests recording
        try {
            if (!microphoneSource.isMicrophoneActive()) {
                microphoneSource.startMicrophone();
                log.info("🎤 Started microphone for session: {}", session.getId());
            }
        } catch (LineUnavailableException e) {
            log.error("Failed to start microphone: {}", e.getMessage());
            sendMessage(session, Map.of(
                "type", "error",
                "message", "Failed to start microphone: " + e.getMessage()
            ));
            return;
        }
        
        // Start translation session
        translationService.startSession(session.getId(), sourceLanguage, targetLanguage, enableTTS);
        
        sendMessage(session, Map.of(
            "type", "transcription_started",
            "sourceLanguage", sourceLanguage,
            "targetLanguage", targetLanguage,
            "enableTTS", enableTTS,
            "microphoneActive", true
        ));
        
        log.info("🎤 Frontend started recording for session: {}", session.getId());
    }

    private void handleStopTranscription(WebSocketSession session, Map<String, Object> payload) {
        // Stop microphone when client requests stop
        if (microphoneSource.isMicrophoneActive()) {
            microphoneSource.stopMicrophone();
            log.info("🛑 Stopped microphone for session: {}", session.getId());
        }
        
        // Stop translation session
        translationService.stopSession(session.getId());
        
        sendMessage(session, Map.of(
            "type", "transcription_stopped",
            "microphoneActive", false
        ));
        
        log.info("🛑 Frontend stopped recording for session: {}", session.getId());
    }

    private void handleAudioChunk(WebSocketSession session, Map<String, Object> payload) {
        String audioData = (String) payload.get("audioData");
        
        // Feed real microphone audio chunk to Akka Streams for processing
        // Results will be sent back via the Akka Streams pipeline
        translationService.feedAudioChunk(session.getId(), audioData);
        
        log.debug("🎤 Fed real microphone audio chunk to Akka Streams for session: {}", session.getId());
    }

    private void handleConfiguration(WebSocketSession session, Map<String, Object> payload) {
        // Handle configuration changes like TTS voice, speed, etc.
        String ttsVoice = (String) payload.get("ttsVoice");
        Double ttsSpeed = (Double) payload.get("ttsSpeed");
        String translationProvider = (String) payload.get("translationProvider");
        
        translationService.configureSession(session.getId(), ttsVoice, ttsSpeed, translationProvider);
        
        sendMessage(session, Map.of(
            "type", "configuration_updated",
            "ttsVoice", ttsVoice,
            "ttsSpeed", ttsSpeed,
            "translationProvider", translationProvider
        ));
    }

    public void broadcastToSession(String sessionId, Map<String, Object> message) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            sendMessage(session, message);
        }
    }

    private void sendMessage(WebSocketSession session, Map<String, Object> message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            log.info("🟢 WebSocket SENDING to {}: {}", session.getId(), jsonMessage);
            session.sendMessage(new TextMessage(jsonMessage));
        } catch (Exception e) {
            log.error("Error sending WebSocket message", e);
        }
    }

    public Map<String, WebSocketSession> getActiveSessions() {
        return new ConcurrentHashMap<>(sessions);
    }
}
