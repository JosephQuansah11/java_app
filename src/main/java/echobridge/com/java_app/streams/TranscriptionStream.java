package echobridge.com.java_app.streams;

import java.util.concurrent.CompletionStage;

import akka.Done;
import akka.actor.ActorSystem;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.BroadcastHub;
import akka.stream.javadsl.Keep;
import akka.stream.OverflowStrategy;
import akka.NotUsed;

import com.fasterxml.jackson.databind.ObjectMapper;

import echobridge.com.java_app.core.services.EnhancedSpeechRecognitionService;
import echobridge.com.java_app.domain.data_structure.TranscriptionResult;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TranscriptionStream {
    
    private final ActorSystem actorSystem;
    private final EnhancedSpeechRecognitionService speechRecognition;
    private final ObjectMapper objectMapper;
    
    public TranscriptionStream(ActorSystem actorSystem, 
                             EnhancedSpeechRecognitionService speechRecognition,
                             ObjectMapper objectMapper) {
        this.actorSystem = actorSystem;
        this.speechRecognition = speechRecognition;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Creates a stream that processes audio chunks and emits transcription results
     */
    public Flow<String, TranscriptionResult, NotUsed> createTranscriptionFlow() {
        return Flow.of(String.class)
            .map(audioData -> {
                log.debug("🎤 Processing audio chunk for transcription");
                return audioData;
            })
            .mapAsync(4, audioData -> {
                // Process transcription in parallel
                return speechRecognition.transcribe(new short[256])
                    .thenApply(transcription -> {
                        TranscriptionResult result = new TranscriptionResult();
                        result.setFinalText(transcription);
                        result.setConfidence(0.95);
                        log.info("🎤 Transcribed: '{}'", transcription);
                        return result;
                    });
            })
            .filter(result -> !result.getFinalText().trim().isEmpty());
    }
    
    /**
     * Creates a broadcast source that can be shared with multiple consumers
     */
    public Source<TranscriptionResult, NotUsed> createTranscriptionSource() {
        return Source.repeat("audio-chunk")  // Simulate audio input
            .throttle(1, java.time.Duration.ofSeconds(2))  // Every 2 seconds
            .via(createTranscriptionFlow())
            .toMat(BroadcastHub.of(TranscriptionResult.class, 256), Keep.right())
            .run(actorSystem);
    }
}
