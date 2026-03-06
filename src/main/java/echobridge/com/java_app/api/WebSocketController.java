package echobridge.com.java_app.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import echobridge.com.java_app.core.services.TranslationService;
import echobridge.com.java_app.core.services.RealTimeTranslationService;
import echobridge.com.java_app.core.services.EnhancedSpeechRecognitionService;
import echobridge.com.java_app.domain.data_structure.TranscriptionResult;
import lombok.extern.slf4j.Slf4j;

import echobridge.com.java_app.core.services.RealTimeTranslationService;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class WebSocketController extends TextWebSocketHandler {

    private final RealTimeTranslationService translationService;
    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public WebSocketController(RealTimeTranslationService translationService, ObjectMapper objectMapper) {
        this.translationService = translationService;
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
        log.info("WebSocket connection closed: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            String payloadStr = message.getPayload();
            log.info("🔵 WebSocket RECEIVED from {}: {}", session.getId(), payloadStr);
            
            Map<String, Object> payload = objectMapper.readValue(payloadStr, Map.class);
            String type = (String) payload.get("type");
            
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
        
        translationService.startSession(session.getId(), sourceLanguage, targetLanguage, enableTTS);
        
        sendMessage(session, Map.of(
            "type", "transcription_started",
            "sourceLanguage", sourceLanguage,
            "targetLanguage", targetLanguage,
            "enableTTS", enableTTS
        ));
    }

    private void handleStopTranscription(WebSocketSession session, Map<String, Object> payload) {
        translationService.stopSession(session.getId());
        
        sendMessage(session, Map.of(
            "type", "transcription_stopped"
        ));
    }

    private void handleAudioChunk(WebSocketSession session, Map<String, Object> payload) {
        String audioData = (String) payload.get("audioData");
        
        // Process audio chunk asynchronously and feed it to Akka Streams
        translationService.feedAudioChunk(session.getId(), audioData);
        
        // Send transcription results back to client
        TranscriptionResult result = new TranscriptionResult();
        result.setFinalText("[AUDIO PROCESSED]");
        result.setConfidence(0.95);
        
        sendMessage(session, Map.of(
            "type", "transcription_result",
            "partialText", result.getPartialText(),
            "finalText", result.getFinalText(),
            "translatedText", result.getTranslatedText(),
            "confidence", result.getConfidence(),
            "timestamp", System.currentTimeMillis()
        ));
        
        // Send TTS request if translation is available
        if (result.hasTranslatedText() && translationService.isSessionTTSEnabled(session.getId())) {
            sendMessage(session, Map.of(
                "type", "tts_request",
                "text", result.getTranslatedText(),
                "isPartial", result.hasPartialText() && !result.hasFinalText(),
                "timestamp", System.currentTimeMillis()
            ));
        }
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
