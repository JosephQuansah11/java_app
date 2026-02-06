package echobridge.com.java_app.core.services;

import java.util.Optional;

import org.springframework.stereotype.Service;

import akka.stream.KillSwitch;
import akka.stream.KillSwitches;
import akka.stream.Materializer;
import akka.stream.UniqueKillSwitch;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import echobridge.com.java_app.domain.data_structure.Transcription;
import echobridge.com.java_app.domain.flow.SpeechProcessingFlow;
import echobridge.com.java_app.domain.sink.SpeakerSink;
import echobridge.com.java_app.domain.source.MicrophoneSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@AllArgsConstructor
public class RealtimeTranslator {
    
    private final SpeechProcessingFlow flows;
    private final MicrophoneSource microphone;
    private final SpeakerSink speaker;
    private final Materializer materializer;
    
    private Optional<UniqueKillSwitch> killSwitch = Optional.empty();
    
    @PostConstruct
    public void start() {
        // Option A: Full pipeline (Mic → Text → Translation → Speech)
        runFullPipeline();
        
        // Option B: Just transcribe to console (debugging)
        // runTranscriptionOnly();
        
        // Option C: Transcribe + translate, no TTS (testing translation)
        // runTranslationOnly();
    }
    
    public void runFullPipeline() {
        log.info("Starting full translation pipeline: EN → ES");
        
        this.killSwitch = Optional.of(
            microphone.createSource()
                .via(flows.fullPipeline("es")) // Spanish output
                .viaMat(KillSwitches.single(), Keep.right())
                .toMat(speaker.createSink(), Keep.left()) // Speaker plays audio
                .run(materializer)
        );
    }
    
    // public void runTranscriptionOnly() {
    //     // Easy to test just the ASR part
    //     this.killSwitch = Optional.of(
    //         microphone.createSource()
    //             .via(flows.transcriptionFlow())
    //             .map(Transcription::text)
    //             .toMat(Sink.foreach(System.out::println), Keep.left())
    //             .run(materializer)
    //     );
    // }
    
    @PreDestroy
    public void stop() {
        killSwitch.ifPresent(KillSwitch::shutdown);
    }
}
