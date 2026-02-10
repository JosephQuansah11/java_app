package echobridge.com.java_app.core.services;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import echobridge.com.java_app.domain.data_structure.Transcription;
import echobridge.com.java_app.domain.flow.SpeechProcessingFlow;
import echobridge.com.java_app.domain.source.MicrophoneSource;
import echobridge.com.java_app.domain.sink.SpeakerSink;
import akka.stream.Materializer;
import akka.stream.KillSwitches;
import akka.stream.UniqueKillSwitch;
import akka.stream.javadsl.Keep;
import java.util.List;
import java.util.ArrayList;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

import echobridge.com.java_app.domain.data_structure.Translation;

@Service
@Slf4j
public class MicrophoneControlService {
    
    private final SpeechProcessingFlow flows;
    private final MicrophoneSource microphone;
    private final SpeakerSink speaker;
    private final Materializer materializer;
    private final TranslationService translationService;
    
    private final AtomicBoolean isMicrophoneActive = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, Object> sessionData = new ConcurrentHashMap<>();
    private UniqueKillSwitch killSwitch;
    
    public MicrophoneControlService(
            SpeechProcessingFlow flows,
            MicrophoneSource microphone,
            SpeakerSink speaker,
            Materializer materializer,
            TranslationService translationService) {
        this.flows = flows;
        this.microphone = microphone;
        this.speaker = speaker;
        this.materializer = materializer;
        this.translationService = translationService;
    }
    
    public synchronized void startMicrophone() {
        if (isMicrophoneActive.get()) {
            log.warn("Microphone is already active");
            return;
        }
        
        try {
            log.info("Starting microphone with full pipeline");
            this.killSwitch = microphone.createSource()
                .via(flows.fullPipeline("es"))
                .viaMat(KillSwitches.single(), Keep.right())
                .toMat(speaker.createSink(), Keep.left())
                .run(materializer);
                
            isMicrophoneActive.set(true);
            log.info("Microphone started successfully");
            
        } catch (Exception e) {
            log.error("Failed to start microphone", e);
            throw new RuntimeException("Failed to start microphone", e);
        }
    }
    
    public synchronized void stopMicrophone() {
        if (!isMicrophoneActive.get()) {
            log.warn("Microphone is not active");
            return;
        }
        
        try {
            if (killSwitch != null) {
                killSwitch.shutdown();
                killSwitch = null;
            }
            isMicrophoneActive.set(false);
            log.info("Microphone stopped successfully");
            
        } catch (Exception e) {
            log.error("Failed to stop microphone", e);
            throw new RuntimeException("Failed to stop microphone", e);
        }
    }
    
    public boolean isMicrophoneActive() {
        return isMicrophoneActive.get();
    }
    
    public CompletableFuture<Translation> processTranslation(String text, String targetLanguage) {
        CompletableFuture<String> translationFuture = new CompletableFuture<>();
        translationService.translate(text, "en", targetLanguage)
            .thenAccept(translationFuture::complete)
            .exceptionally(throwable -> {
                translationFuture.completeExceptionally(throwable);
                return null;
            });
        
        return translationFuture.thenApply(translated -> new Translation(
            text,
            translated,
            "en",
            targetLanguage
        ));
    }
    
    private void storeTranscription(Transcription transcription) {
        sessionData.compute("transcriptions", (key, value) -> {
            List<Transcription> list = 
                (List<Transcription>) value;
            if (list == null) {
                list = new ArrayList<>();
            }
            list.add(transcription);
            if (list.size() > 100) {
                list.remove(0);
            }
            return list;
        });
    }
    
    private void storeTranslation(Translation translation) {
        sessionData.compute("translations", (key, value) -> {
            java.util.List<Translation> list = (java.util.List<Translation>) value;
            if (list == null) {
                list = new java.util.ArrayList<>();
            }
            list.add(translation);
            if (list.size() > 100) {
                list.remove(0);
            }
            return list;
        });
    }
    
    public List<Transcription> getTranscriptions() {
        return (List<Transcription>) 
            sessionData.getOrDefault("transcriptions", new ArrayList<>());
    }
    
    public List<Translation> getTranslations() {
        return (List<Translation>) 
            sessionData.getOrDefault("translations", new ArrayList<>());
    }
}
