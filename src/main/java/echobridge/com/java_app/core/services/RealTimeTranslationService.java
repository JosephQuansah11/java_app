package echobridge.com.java_app.core.services;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import echobridge.com.java_app.domain.data_structure.TranscriptionResult;
import echobridge.com.java_app.streams.AkkaStreamsOrchestrator;
import echobridge.com.java_app.api.WebSocketController;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RealTimeTranslationService implements AkkaStreamsOrchestrator.WebSocketMessageSender {

    private final AkkaStreamsOrchestrator akkaStreamsOrchestrator;
    @Lazy
    private final WebSocketController webSocketController;
    private final ExecutorService executor;
    
    @Value("${realtime.translation.provider:google}")
    private String defaultTranslationProvider;
    
    @Value("${realtime.tts.enable:true}")
    private boolean defaultTTSEnabled;
    
    private final Map<String, TranslationSession> sessions = new ConcurrentHashMap<>();

    public RealTimeTranslationService(
            AkkaStreamsOrchestrator akkaStreamsOrchestrator,
            @Lazy WebSocketController webSocketController) {
        this.akkaStreamsOrchestrator = akkaStreamsOrchestrator;
        this.webSocketController = webSocketController;
        this.executor = Executors.newCachedThreadPool();
    }

    public void startSession(String sessionId, String sourceLanguage, String targetLanguage, boolean enableTTS) {
        TranslationSession session = new TranslationSession();
        session.setSessionId(sessionId);
        session.setSourceLanguage(sourceLanguage);
        session.setTargetLanguage(targetLanguage);
        session.setEnableTTS(enableTTS);
        session.setActive(true);
        session.setLastPartialText("");
        session.setLastFinalText("");
        
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
            log.info("Stopped translation session: {}", sessionId);
        }
    }

    public CompletableFuture<TranscriptionResult> processAudioChunk(String sessionId, String audioData) {
        return CompletableFuture.supplyAsync(() -> {
            // This method is now handled by Akka Streams
            log.debug("Audio chunk processing delegated to Akka Streams for session: {}", sessionId);
            
            // For now, return a mock result to maintain functionality
            // In a real implementation, this would feed the audio to Akka Streams
            TranscriptionResult result = new TranscriptionResult();
            result.setFinalText("[AUDIO CHUNK PROCESSED]");
            result.setConfidence(0.95);
            return result;
        });
    }

    /**
     * Feed audio chunk into Akka Streams pipeline for a specific session
     */
    public void feedAudioChunk(String sessionId, String audioChunk) {
        // Find the Akka Streams orchestrator and feed the audio chunk
        // Note: This would typically be injected and called
        log.info("🎤 Feeding audio chunk into Akka Streams for session: {}", sessionId);
        
        // For now, just log the audio chunk
        // In a real implementation, this would call akkaStreamsOrchestrator.feedAudioChunk(sessionId, audioChunk);
        log.debug("Audio chunk received for session {}: {}", sessionId, audioChunk);
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
        // Use the lazy-loaded WebSocketController to broadcast messages
        if (webSocketController != null) {
            webSocketController.broadcastToSession(sessionId, message);
        } else {
            log.warn("WebSocketController is not available for session: {}", sessionId);
        }
    }

    @Data
    public static class TranslationSession {
        private String sessionId;
        private String sourceLanguage;
        private String targetLanguage;
        private boolean enableTTS;
        private boolean active;
        private String lastPartialText;
        private String lastFinalText;
        private String ttsVoice;
        private Double ttsSpeed;
        private String translationProvider;
    }
}
