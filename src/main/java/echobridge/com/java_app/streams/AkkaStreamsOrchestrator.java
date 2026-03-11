package echobridge.com.java_app.streams;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import org.springframework.stereotype.Component;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import echobridge.com.java_app.core.services.EnhancedSpeechRecognitionService;
import echobridge.com.java_app.core.services.TranslationService;
import echobridge.com.java_app.domain.data_structure.TranscriptionResult;
import echobridge.com.java_app.domain.source.MicrophoneSource;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class AkkaStreamsOrchestrator {

    private final ActorSystem actorSystem;
    private final EnhancedSpeechRecognitionService speechRecognition;
    private final TranslationService translationService;
    private final MicrophoneSource microphoneSource;
    private final Map<String, LinkedBlockingQueue<String>> sessionSources = new ConcurrentHashMap<>();
    private final Map<String, CompletionStage<?>> sessionFutures = new ConcurrentHashMap<>();

    public AkkaStreamsOrchestrator(ActorSystem actorSystem,
            TranslationService translationService,
            EnhancedSpeechRecognitionService speechRecognition,
            MicrophoneSource microphoneSource) {
        this.actorSystem = actorSystem;
        this.translationService = translationService;
        this.speechRecognition = speechRecognition;
        this.microphoneSource = microphoneSource;
    }

    /**
     * Creates a real-time audio processing pipeline that accepts external audio
     * input
     */
    public void startParallelPipeline(String sessionId, String sourceLanguage, String targetLanguage,
            WebSocketMessageSender messageSender) {
        log.info("🚀 Starting REAL-TIME Akka Streams pipeline for session: {}", sessionId);

        // Create a queue-based source for real microphone audio input with capacity for
        // smooth buffering
        LinkedBlockingQueue<String> audioQueue = new LinkedBlockingQueue<>(
                1000);
        sessionSources.put(sessionId, audioQueue);

        // Use MicrophoneSource directly for real audio capture
        Source<short[], NotUsed> microphoneAudioSource = microphoneSource.createSource()
                .map(audioChunk -> audioChunk.samples());

        // Node 1: PARALLEL transcription nodes - distribute audio across multiple
        // workers
        Flow<short[], TranscriptionResult, NotUsed> transcriptionNode = Flow.of(short[].class)
                .mapAsync(64, audioSamples -> { // Reduced to 64 parallel workers for lower latency
                    // Process individual audio chunk immediately

                    try {
                        // Validate minimum audio size - reduced for better responsiveness
                        if (audioSamples.length < 8000) { // Reduced from 16000 to 8000 samples
                            log.debug("Audio chunk too small: {} samples (minimum 8000)", audioSamples.length);
                            return CompletableFuture.completedFuture(null);
                        }

                        return speechRecognition.transcribe(audioSamples).thenApply(transcription -> {
                            if (transcription != null && !transcription.trim().isEmpty()) {
                                TranscriptionResult result = new TranscriptionResult();
                                result.setFinalText(transcription);
                                result.setTranslatedText("");
                                result.setConfidence(0.95);
                                return result;
                            } else {
                                return null;
                            }
                        }).exceptionally(throwable -> {
                            log.error("❌ Transcription failed: {}", throwable.getMessage());
                            return null;
                        });
                    } catch (Exception e) {
                        log.error("Error processing audio chunk: {}", e.getMessage());
                        return CompletableFuture.completedFuture(null);
                    }
                })
                .filter(result -> result != null); // Remove null/empty results

        // Node 2: PARALLEL translation nodes - optimized for real-time
        Flow<TranscriptionResult, TranscriptionResult, NotUsed> translationNode = Flow.of(TranscriptionResult.class)
                .mapAsync(64, result -> { // Reduced to 64 parallel translation workers
                    String text = result.getFinalText();

                    return translationService.translate(text, sourceLanguage, targetLanguage)
                            .thenApply(translatedText -> {
                                result.setTranslatedText(translatedText);
                                return result;
                            })
                            .exceptionally(throwable -> {
                                log.error("❌ Translation failed: {}", throwable.getMessage());
                                result.setTranslatedText("[Translation failed]");
                                return result;
                            });
                })
                .filter(result -> result.getTranslatedText() != null &&
                        !result.getTranslatedText().trim().isEmpty());

        // WebSocket sink with parallel processing and 3ms interval
        Sink<TranscriptionResult, CompletionStage<Done>> webSocketSink = Sink
                .foreach(result -> { // Process each result immediately
                    messageSender.broadcastToSession(sessionId, Map.of(
                            "type", "transcription_result",
                            "finalText", result.getFinalText(),
                            "translatedText", result.getTranslatedText(),
                            "confidence", result.getConfidence(),
                            "processingMode", "parallel-akka-streams",
                            "timestamp", System.currentTimeMillis()));
                });

        // Build and run real-time pipeline that uses MicrophoneSource directly
       CompletionStage<?> pipelineFuture = microphoneAudioSource
                .via(transcriptionNode)
                .via(translationNode)
                .toMat(webSocketSink, Keep.right())
                .run(actorSystem);
        
        // Store the future for later cancellation
        sessionFutures.put(sessionId, pipelineFuture);

        log.debug("✅ REALTIME Akka Streams pipeline started");
    }

    /**
     * Feed real microphone audio chunk into the Akka Streams pipeline for a
     * specific session
     */
    public void feedAudioChunk(String sessionId, String audioChunk) {
        java.util.concurrent.BlockingQueue<String> sessionQueue = sessionSources.get(sessionId);
        if (sessionQueue != null) {
            try {
                sessionQueue.offer(audioChunk);
                log.debug("🎤 Fed audio chunk to Akka Streams for session: {}", sessionId);
            } catch (Exception e) {
                log.error("Error feeding audio chunk to Akka Streams", e);
            }
        } else {
            log.warn("No Akka Streams queue found for session: {}", sessionId);
        }
    }

    /**
     * Stop the Akka Streams pipeline for a specific session
     */
    public void stopPipeline(String sessionId) {
        java.util.concurrent.BlockingQueue<String> sessionSource = sessionSources.remove(sessionId);
        java.util.concurrent.CompletionStage<?> pipelineFuture = sessionFutures.remove(sessionId);
        
        if (sessionSource != null) {
            log.info("🛑 Stopping Akka Streams pipeline for session: {}", sessionId);
            sessionSource.offer("STOP"); // Signal stop to the queue
        }
        
        if (pipelineFuture != null) {
            try {
                // Cancel the Akka Streams pipeline
                pipelineFuture.toCompletableFuture().cancel(true);
                log.info("🛑 Akka Streams pipeline cancelled for session: {}", sessionId);
            } catch (Exception e) {
                log.error("Failed to cancel Akka Streams pipeline: {}", e.getMessage());
            }
        }
        
        log.info("🛑 Akka Streams pipeline stopped for session: {}", sessionId);
    }

    /**
     * Interface to break circular dependency
     */
    public interface WebSocketMessageSender {
        void broadcastToSession(String sessionId, Map<String, Object> message);
    }
}
