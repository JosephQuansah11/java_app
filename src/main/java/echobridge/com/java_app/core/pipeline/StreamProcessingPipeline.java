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
import echobridge.com.java_app.core.services.SpeechRecognitionService;
import echobridge.com.java_app.domain.source.MicrophoneSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@AllArgsConstructor

public class StreamProcessingPipeline {

    private final MicrophoneSource microphoneSource;
    private final SpeechRecognitionService speechRecognitionService;

    // FIXED: Use KillSwitch instead of Cancellable
    private Optional<UniqueKillSwitch> killSwitch = Optional.empty();
    private final Materializer materializer;

    @PostConstruct
    public void start() {
        log.info("Starting stream processing pipeline");
        this.killSwitch = Optional.of(createPipeline()
        .viaMat(KillSwitches.single(), Keep.right()).toMat(Sink.foreach(this::handleTranscription), Keep.left())
                .run(materializer));
    }



    @PreDestroy
    public void stop() {
        log.info("Stopping stream processing pipeline");
        killSwitch.ifPresent(KillSwitch::shutdown);
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
        return microphoneSource.createSource().buffer(3, OverflowStrategy.dropHead()) // OverflowStrategy.dropHead()
                .mapAsync(4, samples -> {
                    log.debug("Sending {} samples to speech recognition: ", samples);
                    return speechRecognitionService.transcribe(samples.samples()).exceptionally(throwable -> {
                        log.error("Error transcribing audio: ", throwable);
                        return "";
                    });
                }) // Filter out empty results (silence/noise)
                .filter(text -> text != null && !text.trim().isEmpty())

                // Optional: Add deduplication or smoothing here
                .map(this::postProcessTranscription)

                // Log for monitoring
                .wireTap(text -> log.info("Transcribed: {}", text));
    }



    private String postProcessTranscription(String text) {
        // Clean up the transcription
        return text.trim()
                .replaceAll("\\s+", " ") // normalize whitespace
                .replaceAll("[.,]$", ""); // remove trailing punctuation
    }


    
    private void handleTranscription(String text) {
        // Your business logic here - send via WebSocket, save to DB, etc.
        log.info("Final output: {}", text);
        
        // Example: Could integrate with Spring's RestClient here
        // restClient.post().uri("/api/transcriptions").body(text).retrieve();
    }
}
