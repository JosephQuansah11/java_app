package echobridge.com.java_app.adapters;

import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Component;

import echobridge.com.java_app.core.services.DistributedProcessingService;
import echobridge.com.java_app.core.services.MicrophoneControlService;
import echobridge.com.java_app.domain.data_structure.Translation;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class SpeechProcessingAdapter {
    
    private final MicrophoneControlService microphoneControl;
    private final DistributedProcessingService distributedProcessing;
    
    public SpeechProcessingAdapter(
            MicrophoneControlService microphoneControl,
            DistributedProcessingService distributedProcessing) {
        this.microphoneControl = microphoneControl;
        this.distributedProcessing = distributedProcessing;
    }
    
    public CompletableFuture<Void> startMicrophoneProcessing() {
        return CompletableFuture.runAsync(() -> {
            try {
                microphoneControl.startMicrophone();
                log.info("Microphone processing started via adapter");
            } catch (Exception e) {
                log.error("Failed to start microphone processing", e);
                throw new RuntimeException("Failed to start microphone processing", e);
            }
        });
    }
    
    public CompletableFuture<Void> stopMicrophoneProcessing() {
        return CompletableFuture.runAsync(() -> {
            try {
                microphoneControl.stopMicrophone();
                log.info("Microphone processing stopped via adapter");
            } catch (Exception e) {
                log.error("Failed to stop microphone processing", e);
                throw new RuntimeException("Failed to stop microphone processing", e);
            }
        });
    }
    
    public CompletableFuture<Translation> translateWithDistribution(String text, String targetLanguage) {
        return distributedProcessing.processTranslation(text, targetLanguage)
            .thenApply(result -> {
                log.info("Translation completed via distributed processing: {} -> {}", text, result.translatedText());
                return result;
            })
            .exceptionally(throwable -> {
                log.error("Distributed translation failed, falling back to local processing", throwable);
                return microphoneControl.processTranslation(text, targetLanguage).join();
            });
    }
    
    public boolean isMicrophoneActive() {
        return microphoneControl.isMicrophoneActive();
    }
    
    public DistributedProcessingService.NodeStatus[] getProcessingNodeStatus() {
        return distributedProcessing.getNodeStatus().toArray(new DistributedProcessingService.NodeStatus[0]);
    }
}
