package echobridge.com.java_app.core.services;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import echobridge.com.java_app.api.ProcessedTranscriptionWebSocketHandler;
import echobridge.com.java_app.api.WebSocketController;
import echobridge.com.java_app.streams.AkkaStreamsOrchestrator;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RealTimeTranslationService implements AkkaStreamsOrchestrator.WebSocketMessageSender {

    private final AkkaStreamsOrchestrator akkaStreamsOrchestrator;
    @Lazy
    private final WebSocketController webSocketController;
    private final ProcessedTranscriptionWebSocketHandler processedTranscriptionHandler;
    
    
    private final Map<String, TranslationSession> sessions = new ConcurrentHashMap<>();

    public RealTimeTranslationService(
            AkkaStreamsOrchestrator akkaStreamsOrchestrator,
            @Lazy WebSocketController webSocketController,
            ProcessedTranscriptionWebSocketHandler processedTranscriptionHandler) {
        this.akkaStreamsOrchestrator = akkaStreamsOrchestrator;
        this.webSocketController = webSocketController;
        this.processedTranscriptionHandler = processedTranscriptionHandler;
    }

    public void startSession(String sessionId, String sourceLanguage, String targetLanguage, boolean enableTTS) {
        TranslationSession session = new TranslationSession();
        session.setSessionId(sessionId);
        session.setSourceLanguage(sourceLanguage);
        session.setTargetLanguage(targetLanguage);
        session.setEnableTTS(enableTTS);
        session.setActive(true);
        
        sessions.put(sessionId, session);
        
        // Start Akka Streams pipeline for parallel processing
        akkaStreamsOrchestrator.startParallelPipeline(sessionId, sourceLanguage, targetLanguage, this);
        
        log.info("Started translation session: {} -> {} for session {}", sourceLanguage, targetLanguage, sessionId);
    }

    public void stopSession(String sessionId) {
        TranslationSession session = sessions.get(sessionId);
        if (session != null) {
            session.setActive(false);
            sessions.remove(sessionId);
            
            // Stop Akka Streams pipeline
            akkaStreamsOrchestrator.stopPipeline(sessionId);
            
            log.info("Stopped translation session: {}", sessionId);
        }
    }


    /**
     * Feed audio chunk into Akka Streams pipeline for a specific session
     */
    public void feedAudioChunk(String sessionId, String audioChunk) {
        TranslationSession session = sessions.get(sessionId);
        if (session != null && session.isActive()) {
            // Actually feed the audio chunk into Akka Streams
            akkaStreamsOrchestrator.feedAudioChunk(sessionId, audioChunk);
            log.debug("🎤 Fed audio chunk to Akka Streams for session: {}", sessionId);
        } else {
            log.warn("No active session found for audio chunk: {}", sessionId);
        }
    }

    public boolean isSessionTTSEnabled(String sessionId) {
        TranslationSession session = sessions.get(sessionId);
        return session != null && session.isEnableTTS();
    }

    public void configureSession(String sessionId, String ttsVoice, Double ttsSpeed, String translationProvider) {
        TranslationSession session = sessions.get(sessionId);
        if (session != null) {
            if (ttsVoice != null) session.setTtsVoice(ttsVoice);
            if (ttsSpeed != null) session.setTtsSpeed(ttsSpeed);
            if (translationProvider != null) session.setTranslationProvider(translationProvider);
        }
        log.info("Configured session {} with TTS voice: {}, speed: {}, translation provider: {}", 
                sessionId, ttsVoice, ttsSpeed, translationProvider);
    }

    @Override
    public void broadcastToSession(String sessionId, Map<String, Object> message) {
        // This will be called by AkkaStreamsOrchestrator
        // Send to both the original WebSocket controller and the new processed transcription handler
        if (webSocketController != null) {
            webSocketController.broadcastToSession(sessionId, message);
        }
        
        if (processedTranscriptionHandler != null) {
            // Send processed transcription results to the dedicated endpoint
            processedTranscriptionHandler.sendTranscriptionResult(sessionId, message);
        }
        
        if (webSocketController == null && processedTranscriptionHandler == null) {
            log.warn("No WebSocket handlers available for session: {}", sessionId);
        }
    }
    
    /**
     * Broadcast message to all active sessions
     */
    public void broadcastToAllSessions(Map<String, Object> message) {
        if (webSocketController != null) {
            // Get all session IDs and broadcast to each
            for (String sessionId : sessions.keySet()) {
                webSocketController.broadcastToSession(sessionId, message);
            }
        }
        
        if (processedTranscriptionHandler != null) {
            // Broadcast to all connected clients on the processed endpoint
            processedTranscriptionHandler.broadcastTranscriptionResult(message);
        }
        
        log.info("📡 Broadcasted message to {} active sessions", sessions.size());
    }

    @Data
    public static class TranslationSession {
        private String sessionId;
        private String sourceLanguage;
        private String targetLanguage;
        private boolean enableTTS;
        private boolean active;
        private String ttsVoice;
        private Double ttsSpeed;
        private String translationProvider;
    }
}
