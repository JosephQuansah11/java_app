package echobridge.com.java_app.core.pipeline;

import java.util.Optional;

import org.springframework.stereotype.Component;

import akka.NotUsed;
import akka.stream.KillSwitch;
import akka.stream.KillSwitches;
import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
import akka.stream.UniqueKillSwitch;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import echobridge.com.java_app.core.services.EnhancedSpeechRecognitionService;
import echobridge.com.java_app.domain.source.MicrophoneSource;
import echobridge.com.java_app.core.services.RealTimeTranslationService;
import jakarta.annotation.PreDestroy;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@AllArgsConstructor

public class StreamProcessingPipeline {

    private final MicrophoneSource microphoneSource;
    private final EnhancedSpeechRecognitionService speechRecognitionService;
    private final RealTimeTranslationService translationService;

    // FIXED: Use KillSwitch instead of Cancellable
    private Optional<UniqueKillSwitch> killSwitch = Optional.empty();
    private final Materializer materializer;
    
    // ULTRA-OPTIMIZED: Deduplication using Set for O(1) performance
    private final java.util.Set<String> recentTranscriptions = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final int DEDUP_WINDOW_SIZE = 100;

    // Remove @PostConstruct - microphone will only start when frontend requests it
    public void start() {
        if (killSwitch.isPresent()) {
            log.warn("Stream processing pipeline is already running");
            return;
        }
        log.info("Starting stream processing pipeline on demand");
        this.killSwitch = Optional.of(createPipeline()
        .viaMat(KillSwitches.single(), Keep.right()).toMat(Sink.foreach(this::handleTranscription), Keep.left())
                .run(materializer));
    }
    
    public void stop() {
        if (!killSwitch.isPresent()) {
            log.warn("Stream processing pipeline is not running");
            return;
        }
        log.info("Stopping stream processing pipeline on demand");
        killSwitch.ifPresent(KillSwitch::shutdown);
        killSwitch = Optional.empty();
    }
    
    public boolean isRunning() {
        return killSwitch.isPresent();
    }

    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up stream processing pipeline");
        stop();
    }



    /**
     * The main streaming pipeline - beginner-friendly breakdown:
     * 
     * 1. Source: Microphone produces audio chunks
     * 2. Flow.mapAsync: Send to speech recognition (parallel, unordered for speed)
     * 3. Flow.filter: Remove empty results
     * 4. Flow.map: Clean up text
     */

    private Source<String, NotUsed> createPipeline() {
        return microphoneSource.createSource()
                // ULTRA-OPTIMIZED: Remove buffer for immediate processing
                .mapAsync(64, samples -> {  // 64 parallel transcriptions
                    log.debug("Sending {} samples to speech recognition", samples.samples().length);
                    return speechRecognitionService.transcribe(samples.samples()).exceptionally(throwable -> {
                        log.error("Error transcribing audio: {}", throwable.getMessage(), throwable);
                        return "";
                    });
                })
                // ULTRA-OPTIMIZED: Complete sentence filtering and deduplication
                .filter(text -> {
                    if (text == null || text.trim().isEmpty()) return false;
                    
                    // Only process complete sentences (ending with .!? or longer than 20 chars)
                    String trimmed = text.trim();
                    boolean isComplete = trimmed.length() > 20 || 
                                       trimmed.matches(".*[.!?]$") ||
                                       trimmed.matches(".*\\?$");
                    
                    if (!isComplete) {
                        log.debug("Skipping partial text: '{}'", trimmed);
                        return false;
                    }
                    
                    // Fast deduplication using Set
                    boolean isNew = recentTranscriptions.add(trimmed);
                    
                    // Maintain window size to prevent memory leak
                    if (recentTranscriptions.size() > DEDUP_WINDOW_SIZE) {
                        recentTranscriptions.clear();
                        log.debug("Cleared deduplication window - {} items processed", DEDUP_WINDOW_SIZE);
                    }
                    
                    return isNew;
                })
                
                // Optional: Add deduplication or smoothing here
                .map(this::postProcessTranscription)

                // Log for monitoring
                .wireTap(text -> log.info("🎤 COMPLETE SENTENCE: '{}'", text));
    }



    private String postProcessTranscription(String text) {
        // Clean up transcription and fix encoding issues
        return text.trim()
                .replaceAll("\\s+", " ") // normalize whitespace
                .replaceAll("[^\\w\\s\\.,!?¡¿áéíóúÁÉÍÓÚñÑüÜ]", "") // keep only valid chars
                .replaceAll("[.,]$", "") // remove trailing punctuation
                .replaceAll("¡", "¿") // fix Spanish punctuation
                .trim();
    }


    
    private void handleTranscription(String text) {
        // Broadcast transcription result via WebSocket to all connected sessions
        log.info("🎤 TRANSCRIBED: '{}'", text);
        
        // Create transcription result message
        java.util.Map<String, Object> message = java.util.Map.of(
            "type", "transcription_result",
            "finalText", text,
            "translatedText", "", // Will be filled by translation service
            "confidence", 0.95,
            "timestamp", System.currentTimeMillis(),
            "processingMode", "direct-pipeline"
        );
        
        // Broadcast to all sessions via translation service
        translationService.broadcastToAllSessions(message);
        
        log.info("📡 BROADCASTED transcription to frontend: '{}'", text);
    }
}
