package echobridge.com.java_app.core.services;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import echobridge.com.java_app.domain.data_structure.Translation;
import echobridge.com.java_app.domain.data_structure.Transcription;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DistributedProcessingService {
    
    private final ExecutorService processingPool;
    private final ConcurrentHashMap<String, ProcessingNode> processingNodes;
    private final TranslationService translationService;
    private final SpeechRecognitionService speechRecognitionService;
    private final TextToSpeechService textToSpeechService;
    
    public DistributedProcessingService(
            @Qualifier("enhancedTranslationService") TranslationService translationService,
            @Qualifier("enhancedSpeechRecognitionService") SpeechRecognitionService speechRecognitionService,
            @Qualifier("enhancedTextToSpeechService") TextToSpeechService textToSpeechService) {
        this.translationService = translationService;
        this.speechRecognitionService = speechRecognitionService;
        this.textToSpeechService = textToSpeechService;
        this.processingPool = Executors.newFixedThreadPool(4);
        this.processingNodes = new ConcurrentHashMap<>();
        initializeNodes();
    }
    
    private void initializeNodes() {
        for (int i = 0; i < 4; i++) {
            String nodeId = "node-" + i;
            processingNodes.put(nodeId, new ProcessingNode(nodeId, processingPool));
        }
        log.info("Initialized {} processing nodes", processingNodes.size());
    }
    
    public CompletableFuture<ProcessingResult> processAudioChunk(String sessionId, short[] audioData, String targetLanguage) {
        String nodeId = selectNode(sessionId);
        ProcessingNode node = processingNodes.get(nodeId);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Processing audio chunk on node: {}", nodeId);
                
                String transcription = speechRecognitionService.transcribe(audioData).toCompletableFuture().get();
                String translatedText = translationService.translate(transcription, "en", targetLanguage).toCompletableFuture().get();
                byte[] audioOutput = textToSpeechService.synthesize(translatedText, targetLanguage).toCompletableFuture().get();
                
                ProcessingResult result = new ProcessingResult(
                    sessionId,
                    transcription,
                    translatedText,
                    audioOutput,
                    nodeId,
                    System.currentTimeMillis()
                );
                
                node.recordProcessing(result);
                return result;
                
            } catch (Exception e) {
                log.error("Processing failed on node: {}", nodeId, e);
                throw new RuntimeException("Processing failed", e);
            }
        }, processingPool);
    }
    
    public CompletableFuture<Translation> processTranslation(String text, String targetLanguage) {
        String nodeId = selectNode("translation-" + text.hashCode());
        ProcessingNode node = processingNodes.get(nodeId);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Processing translation on node: {}", nodeId);
                String translated = translationService.translate(text, "en", targetLanguage).toCompletableFuture().get();
                return new Translation(text, translated, "en", targetLanguage);
            } catch (Exception e) {
                log.error("Translation failed on node: {}", nodeId, e);
                throw new RuntimeException("Translation failed", e);
            }
        }, processingPool);
    }
    
    private String selectNode(String sessionId) {
        int nodeIndex = Math.abs(sessionId.hashCode()) % processingNodes.size();
        return "node-" + nodeIndex;
    }
    
    public List<NodeStatus> getNodeStatus() {
        List<NodeStatus> status = new ArrayList<>();
        for (ProcessingNode node : processingNodes.values()) {
            status.add(node.getStatus());
        }
        return status;
    }
    
    public List<ProcessingResult> getProcessingHistory(String sessionId) {
        return processingNodes.values().stream()
            .flatMap(node -> node.getProcessingHistory().stream())
            .filter(result -> result.sessionId().equals(sessionId))
            .sorted((a, b) -> Long.compare(b.timestamp(), a.timestamp()))
            .limit(50)
            .toList();
    }
    
    private static class ProcessingNode {
        private final String nodeId;
        private final ExecutorService executor;
        private final List<ProcessingResult> processingHistory;
        private volatile long processedCount = 0;
        
        public ProcessingNode(String nodeId, ExecutorService executor) {
            this.nodeId = nodeId;
            this.executor = executor;
            this.processingHistory = new ArrayList<>();
        }
        
        public void recordProcessing(ProcessingResult result) {
            synchronized (processingHistory) {
                processingHistory.add(result);
                if (processingHistory.size() > 100) {
                    processingHistory.remove(0);
                }
                processedCount++;
            }
        }
        
        public List<ProcessingResult> getProcessingHistory() {
            synchronized (processingHistory) {
                return new ArrayList<>(processingHistory);
            }
        }
        
        public NodeStatus getStatus() {
            return new NodeStatus(nodeId, processedCount, processingHistory.size());
        }
    }
    
    public record ProcessingResult(
        String sessionId,
        String transcription,
        String translation,
        byte[] audioOutput,
        String nodeId,
        long timestamp
    ) {}
    
    public record NodeStatus(
        String nodeId,
        long processedCount,
        int currentQueueSize
    ) {}
}
