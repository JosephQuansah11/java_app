package echobridge.com.java_app.core.services;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import echobridge.com.java_app.domain.data_structure.TranscriptionResult;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RealTimeTranslationService {

    private final EnhancedSpeechRecognitionService speechRecognition;
    private final TranslationService translationService;
    private final ExecutorService executor;
    
    @Value("${realtime.translation.provider:google}")
    private String defaultTranslationProvider;
    
    @Value("${realtime.tts.enable:true}")
    private boolean defaultTTSEnabled;
    
    private final Map<String, TranslationSession> sessions = new ConcurrentHashMap<>();

    public RealTimeTranslationService(
            EnhancedSpeechRecognitionService speechRecognition,
            TranslationService translationService) {
        this.speechRecognition = speechRecognition;
        this.translationService = translationService;
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
            TranslationSession session = sessions.get(sessionId);
            if (session == null || !session.isActive()) {
                return new TranscriptionResult("", "", "", 0.0);
            }

            try {
                // Decode base64 audio data
                byte[] audioBytes = java.util.Base64.getDecoder().decode(audioData);
                short[] audioSamples = bytesToShorts(audioBytes);

                // Get transcription
                CompletionStage<String> transcriptionFuture = speechRecognition.transcribe(audioSamples);
                String transcription = transcriptionFuture.toCompletableFuture().join();
                
                TranscriptionResult result = new TranscriptionResult();
                result.setPartialText("");
                result.setFinalText("");
                result.setTranslatedText("");
                result.setConfidence(0.95);

                // Determine if this is a partial or final result
                if (isPartialResult(transcription)) {
                    result.setPartialText(transcription);
                    
                    // Only process if different from last partial
                    if (!transcription.equals(session.getLastPartialText())) {
                        session.setLastPartialText(transcription);
                        
                        // Translate partial result for immediate feedback
                        if (!transcription.trim().isEmpty()) {
                            translateAndSpeak(session, transcription, result, true);
                        }
                    }
                } else {
                    result.setFinalText(transcription);
                    session.setLastFinalText(transcription);
                    session.setLastPartialText(""); // Clear partial when final is received
                    
                    // Translate final result
                    if (!transcription.trim().isEmpty()) {
                        translateAndSpeak(session, transcription, result, false);
                    }
                }

                return result;

            } catch (Exception e) {
                log.error("Error processing audio chunk for session {}", sessionId, e);
                return new TranscriptionResult("", "", "", 0.0);
            }
        }, executor);
    }

    private void translateAndSpeak(TranslationSession session, String text, TranscriptionResult result, boolean isPartial) {
        // Translate asynchronously
        translationService.translate(text, session.getSourceLanguage(), session.getTargetLanguage())
            .thenAccept(translatedText -> {
                result.setTranslatedText(translatedText);
                
                // Trigger TTS if enabled and text is meaningful
                if (session.isEnableTTS() && shouldSpeakText(text, isPartial)) {
                    triggerBrowserTTS(session.getSessionId(), translatedText, isPartial);
                }
            })
            .exceptionally(throwable -> {
                log.error("Translation failed for session {}", session.getSessionId(), throwable);
                return null;
            });
    }

    private boolean shouldSpeakText(String text, boolean isPartial) {
        // Don't speak very short partial results to avoid choppiness
        if (isPartial && text.trim().length() < 3) {
            return false;
        }
        
        // Don't speak if it looks like incomplete words
        if (isPartial && !text.matches(".*[.!?]\\s*$")) {
            return text.trim().length() > 10; // Only speak longer partial sentences
        }
        
        return true;
    }

    private void triggerBrowserTTS(String sessionId, String text, boolean isPartial) {
        // This will be sent to the client via WebSocket for browser-native TTS
        log.debug("Triggering TTS for session {}: {}", sessionId, text);
        // The WebSocket controller will handle broadcasting this
    }

    private boolean isPartialResult(String transcription) {
        // Simple heuristic: partial results often don't end with punctuation
        // This could be improved based on the speech recognition service used
        return !transcription.matches(".*[.!?]\\s*$") && 
               !transcription.trim().isEmpty() &&
               transcription.length() < 100; // Shorter results are more likely partial
    }

    private short[] bytesToShorts(byte[] bytes) {
        short[] shorts = new short[bytes.length / 2];
        java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
        return shorts;
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
            
            log.info("Updated configuration for session {}: voice={}, speed={}, provider={}", 
                    sessionId, ttsVoice, ttsSpeed, translationProvider);
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
        private String ttsVoice = "default";
        private Double ttsSpeed = 1.0;
        private String translationProvider = "google";
    }
}
