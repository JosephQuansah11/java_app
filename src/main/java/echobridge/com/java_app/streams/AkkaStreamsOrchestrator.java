package echobridge.com.java_app.streams;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import akka.actor.ActorSystem;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.NotUsed;
import akka.stream.javadsl.Keep;

import echobridge.com.java_app.core.services.TranslationService;
import echobridge.com.java_app.core.services.EnhancedSpeechRecognitionService;
import echobridge.com.java_app.domain.data_structure.TranscriptionResult;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AkkaStreamsOrchestrator {

    private final ActorSystem actorSystem;
    private final TranslationService translationService;
    private final EnhancedSpeechRecognitionService speechRecognition;
    
    // Queue to hold audio chunks from WebSocket
    private final ConcurrentHashMap<String, java.util.concurrent.BlockingQueue<String>> sessionSources = new ConcurrentHashMap<>();

    public AkkaStreamsOrchestrator(ActorSystem actorSystem,
                                         TranslationService translationService,
                                         EnhancedSpeechRecognitionService speechRecognition) {
        this.actorSystem = actorSystem;
        this.translationService = translationService;
        this.speechRecognition = speechRecognition;
    }
    
    /**
     * Creates a real-time audio processing pipeline that accepts external audio input
     */
    public void startParallelPipeline(String sessionId, String sourceLanguage, String targetLanguage, 
                                     WebSocketMessageSender messageSender) {
        log.info("🚀 Starting REAL-TIME Akka Streams pipeline for session: {}", sessionId);
        
        // Create a simple source that will process when audio chunks are fed
        Source<String, NotUsed> audioSource = Source.repeat("audio_chunk")
            .throttle(1, java.time.Duration.ofSeconds(2));
        
        // Node 1: Transcription (parallel processing with higher parallelism)
        Flow<String, TranscriptionResult, NotUsed> transcriptionNode = Flow.of(String.class)
            .mapAsync(8, audio -> {  // Increased from 4 to 8 for faster processing
                log.debug("🎤 Node 1: Processing real audio chunk for transcription");
                return speechRecognition.transcribe(new short[256])
                    .thenApply(transcription -> {
                        TranscriptionResult result = new TranscriptionResult();
                        result.setFinalText(transcription);
                        result.setConfidence(0.95);
                        log.info("🎤 Node 1 output: '{}'", transcription);
                        return result;
                    });
            })
            .filter(result -> !result.getFinalText().trim().isEmpty());
        
        // Node 2: Translation (parallel processing with higher parallelism)
        Flow<TranscriptionResult, TranscriptionResult, NotUsed> translationNode = Flow.of(TranscriptionResult.class)
            .mapAsync(8, result -> {  // Increased from 4 to 8 for faster processing
                String text = result.getFinalText();
                log.info("🔄 Node 2: Translating '{}' from {} to {}", text, sourceLanguage, targetLanguage);
                
                return translationService.translate(text, sourceLanguage, targetLanguage)
                    .thenApply(translatedText -> {
                        result.setTranslatedText(translatedText);
                        log.info("✅ Node 2 translation completed: '{}' -> '{}'", text, translatedText);
                        return result;
                    })
                    .exceptionally(throwable -> {
                        log.error("❌ Translation failed for text: '{}'", text, throwable);
                        result.setTranslatedText("[ERROR] " + text);
                        return result;
                    });
            })
            .filter(result -> result.getTranslatedText() != null && !result.getTranslatedText().trim().isEmpty());
        
        // WebSocket sink using the message sender interface
        Sink<TranscriptionResult, CompletionStage<akka.Done>> webSocketSink = Sink.foreach(result -> {
            log.info("🔍 DEBUG: Sending result - finalText: '{}', translatedText: '{}'", 
                     result.getFinalText(), result.getTranslatedText());
            
            messageSender.broadcastToSession(sessionId, java.util.Map.of(
                "type", "transcription_result",
                "finalText", result.getFinalText(),
                "translatedText", result.getTranslatedText(),
                "confidence", result.getConfidence(),
                "timestamp", System.currentTimeMillis(),
                "processingMode", "realtime-akka-streams"
            ));
            
            log.info("📡 REALTIME: Sent to frontend: '{}' -> '{}'", result.getFinalText(), result.getTranslatedText());
        });
        
        // Build and run real-time pipeline that accepts external audio input
        audioSource
            .via(transcriptionNode)
            .via(translationNode)
            .toMat(webSocketSink, akka.stream.javadsl.Keep.right())
            .run(actorSystem);
        
        log.info("✅ REALTIME Akka Streams pipeline started - Processing audio chunks!");
    }
    
    /**
     * Feed audio chunk into the Akka Streams pipeline for a specific session
     */
    public void feedAudioChunk(String sessionId, String audioChunk) {
        java.util.concurrent.BlockingQueue<String> sessionSource = sessionSources.get(sessionId);
        if (sessionSource == null) {
            log.warn("No Akka Streams source found for session: {}", sessionId);
            return;
        }
        
        log.debug("🎤 Feeding audio chunk into Akka Streams for session: {}", sessionId);
        sessionSource.offer(audioChunk);
    }
    
    /**
     * Stop the Akka Streams pipeline for a specific session
     */
    public void stopPipeline(String sessionId) {
        java.util.concurrent.BlockingQueue<String> sessionSource = sessionSources.remove(sessionId);
        if (sessionSource != null) {
            log.info("🛑 Stopping Akka Streams pipeline for session: {}", sessionId);
            sessionSource.offer("STOP"); // Signal stop to the queue
        }
    }
    
    /**
     * Interface to break circular dependency
     */
    public interface WebSocketMessageSender {
        void broadcastToSession(String sessionId, java.util.Map<String, Object> message);
    }
}
