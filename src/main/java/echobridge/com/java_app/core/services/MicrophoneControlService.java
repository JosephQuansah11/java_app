package echobridge.com.java_app.core.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;

import akka.stream.KillSwitches;
import akka.stream.Materializer;
import akka.stream.UniqueKillSwitch;
import akka.stream.javadsl.Keep;
import echobridge.com.java_app.domain.data_structure.Transcription;
import echobridge.com.java_app.domain.data_structure.Translation;
import echobridge.com.java_app.domain.flow.SpeechProcessingFlow;
import echobridge.com.java_app.domain.sink.SpeakerSink;
import echobridge.com.java_app.domain.source.MicrophoneSource;
import lombok.extern.slf4j.Slf4j;

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
    
    // Store real-time transcriptions
    private final List<String> transcriptionHistory = new ArrayList<>();
    private final Object transcriptionLock = new Object();
    
    // Current sentence being built from partial results
    private final StringBuilder currentSentence = new StringBuilder();
    private String lastPartialText = "";
    private long lastTranscriptionTime = 0;
    
    // Constants for text processing
    private static final long SENTENCE_TIMEOUT_MS = 2000; // 2 seconds of silence ends sentence
    private static final double SIMILARITY_THRESHOLD = 0.8;
    
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
            log.info("Starting microphone with transcription capture");
            
            // Clear previous transcriptions when starting new session
            synchronized (transcriptionLock) {
                transcriptionHistory.clear();
            }
            
            this.killSwitch = microphone.createSource()
                .via(flows.transcriptionFlow())  // Only transcribe, don't translate yet
                .mapAsync(1, transcription -> {
                    // Process partial results and accumulate them
                    String transcribedText = transcription.text();
                    if (transcribedText != null && !transcribedText.trim().isEmpty()) {
                        processPartialTranscription(transcribedText);
                    }
                    return CompletableFuture.completedFuture(transcription);
                })
                .via(flows.translationFlow("es"))  // Then translate
                .via(flows.textToSpeechFlow())      // Then TTS
                .viaMat(KillSwitches.single(), Keep.right())
                .toMat(speaker.createSink(), Keep.left())
                .run(materializer);
                
            isMicrophoneActive.set(true);
            log.info("Microphone started with transcription capture");
            
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
            
            // Finalize any remaining sentence
            finalizeCurrentSentence();
            
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
    
    private void processPartialTranscription(String transcribedText) {
        synchronized (transcriptionLock) {
            long currentTime = System.currentTimeMillis();
            
            // Filter out noise and very short results
            if (transcribedText.length() < 2 || 
                transcribedText.equals("[unk]") || 
                transcribedText.equals("[spn]")) {
                return;
            }
            
            // Check if this is a significant change from the last partial
            double similarity = calculateSimilarity(lastPartialText, transcribedText);
            
            if (similarity < SIMILARITY_THRESHOLD) {
                // Significant change detected - update current sentence
                String newText = transcribedText.trim();
                
                // If it's been a while since last transcription, start a new sentence
                if (currentTime - lastTranscriptionTime > SENTENCE_TIMEOUT_MS) {
                    finalizeCurrentSentence();
                    currentSentence.setLength(0); // Clear for new sentence
                }
                
                // Append new text to current sentence
                if (currentSentence.length() > 0) {
                    currentSentence.append(" ");
                }
                currentSentence.append(newText);
                
                // Update the latest transcription in history (real-time update)
                String fullSentence = currentSentence.toString().trim();
                if (!fullSentence.isEmpty()) {
                    updateLatestTranscription(fullSentence);
                }
                
                lastPartialText = newText;
                lastTranscriptionTime = currentTime;
                
                log.debug("Partial transcription: {}", fullSentence);
            }
        }
    }
    
    private void finalizeCurrentSentence() {
        String sentence = currentSentence.toString().trim();
        if (!sentence.isEmpty() && sentence.length() > 5) {
            addTranscription(sentence);
            log.debug("Finalized sentence: {}", sentence);
        }
    }
    
    private void updateLatestTranscription(String text) {
        // Update the most recent transcription for real-time display
        if (transcriptionHistory.isEmpty()) {
            transcriptionHistory.add(text);
        } else {
            // Replace the last item with updated text
            transcriptionHistory.set(transcriptionHistory.size() - 1, text);
        }
        
        // Update session data for API access
        sessionData.put("transcriptions", new ArrayList<>(transcriptionHistory));
    }
    
    private void addTranscription(String transcribedText) {
        // Keep only last 50 transcriptions to prevent memory issues
        if (transcriptionHistory.size() > 50) {
            transcriptionHistory.remove(0);
        }
        synchronized (transcriptionLock) {
            // Create a proper transcription object with metadata
            Map<String, Object> transcription = Map.of(
                "text", transcribedText,
                "language", "en",
                "confidence", 0.95,
                "timestamp", System.currentTimeMillis()
            );
            
            transcriptionHistory.add(transcribedText);
            sessionData.put("transcriptions", new ArrayList<>(transcriptionHistory));
            
            log.debug("Added transcription #{}: {}", transcriptionHistory.size(), transcribedText);
        }
    }
    
    private double calculateSimilarity(String s1, String s2) {
        // Simple similarity calculation based on common characters
        int common = 0;
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;
        
        for (int i = 0; i < Math.min(s1.length(), s2.length()); i++) {
            if (s1.charAt(i) == s2.charAt(i)) {
                common++;
            }
        }
        
        return (double) common / maxLen;
    }
    
    public List<String> getTranscriptionHistory() {
        synchronized (transcriptionLock) {
            return new ArrayList<>(transcriptionHistory);
        }
    }
    
    public String getCurrentSentence() {
        synchronized (transcriptionLock) {
            return currentSentence.toString().trim();
        }
    }
    
    public Map<String, Object> getTranscriptionStatus() {
        synchronized (transcriptionLock) {
            return Map.of(
                "active", isMicrophoneActive.get(),
                "currentSentence", currentSentence.toString().trim(),
                "transcriptions", new ArrayList<>(transcriptionHistory),
                "sentenceCount", transcriptionHistory.size(),
                "lastPartialText", lastPartialText,
                "lastTranscriptionTime", lastTranscriptionTime
            );
        }
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
