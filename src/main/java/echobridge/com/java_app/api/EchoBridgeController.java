package echobridge.com.java_app.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import echobridge.com.java_app.adapters.SpeechProcessingAdapter;
import echobridge.com.java_app.core.services.MicrophoneControlService;
import echobridge.com.java_app.domain.data_structure.Translation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/echo")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class EchoBridgeController {
    
    private final SpeechProcessingAdapter speechProcessingAdapter;
    private final MicrophoneControlService microphoneControl;
    private final Map<String, Object> sessionData = new ConcurrentHashMap<>();
    
    @PostMapping("/microphone/start")
    public ResponseEntity<Map<String, String>> startMicrophone() {
        try {
            speechProcessingAdapter.startMicrophoneProcessing().get();
            sessionData.put("microphoneActive", true);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Microphone started"
            ));
        } catch (Exception e) {
            log.error("Failed to start microphone", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to start microphone: " + e.getMessage()
            ));
        }
    }
    
    @PostMapping("/microphone/stop")
    public ResponseEntity<Map<String, String>> stopMicrophone() {
        try {
            speechProcessingAdapter.stopMicrophoneProcessing().get();
            sessionData.put("microphoneActive", false);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Microphone stopped"
            ));
        } catch (Exception e) {
            log.error("Failed to stop microphone", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to stop microphone: " + e.getMessage()
            ));
        }
    }
    
    @GetMapping("/microphone/status")
    public ResponseEntity<Map<String, Object>> getMicrophoneStatus() {
        return ResponseEntity.ok(Map.of(
            "active", speechProcessingAdapter.isMicrophoneActive(),
            "timestamp", System.currentTimeMillis()
        ));
    }
    
    @PostMapping("/translate")
    public ResponseEntity<Map<String, Object>> translateText(
            @RequestBody Map<String, String> request) {
        String text = request.get("text");
        String targetLanguage = request.getOrDefault("targetLanguage", "es");
        String sourceLanguage = request.getOrDefault("sourceLanguage", "auto");
        String format = request.getOrDefault("format", "text");
        Integer alternatives = request.get("alternatives") != null ? 
            Integer.parseInt(request.get("alternatives")) : 3;
        String apiKey = request.getOrDefault("api_key", "");
        
        try {
            Translation result = speechProcessingAdapter.translateWithDistribution(text, targetLanguage).get();
            
            // Return full response matching frontend expectations
            return ResponseEntity.ok(Map.of(
                "originalText", text,
                "translatedText", result.translatedText(),
                "fromLanguage", result.fromLanguage() != null ? result.fromLanguage() : sourceLanguage,
                "toLanguage", result.toLanguage(),
                "timestamp", System.currentTimeMillis(),
                "detected_language", result.fromLanguage() != null ? result.fromLanguage() : sourceLanguage,
                "alternatives", alternatives,
                "api_key_used", !apiKey.isEmpty()
            ));
        } catch (Exception e) {
            log.error("Translation failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Translation failed: " + e.getMessage(),
                "originalText", text,
                "fromLanguage", sourceLanguage,
                "toLanguage", targetLanguage,
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
    
    @GetMapping("/results/latest")
    public ResponseEntity<Map<String, Object>> getLatestResults() {
        // Get real-time transcriptions from microphone service
        List<String> transcriptions = microphoneControl.getTranscriptionHistory();
        
        return ResponseEntity.ok(Map.of(
            "transcriptions", transcriptions,
            "translations", sessionData.getOrDefault("translations", new java.util.ArrayList<>()),
            "timestamp", System.currentTimeMillis()
        ));
    }
    
    @GetMapping("/transcriptions/stream")
    public ResponseEntity<Map<String, Object>> getTranscriptionStream() {
        // Get comprehensive transcription status
        Map<String, Object> status = microphoneControl.getTranscriptionStatus();
        
        // Convert transcription history to proper objects for frontend
        @SuppressWarnings("unchecked")
        List<String> transcriptionTexts = (List<String>) status.get("transcriptions");
        List<Map<String, Object>> transcriptionObjects = new java.util.ArrayList<>();
        
        for (String text : transcriptionTexts) {
            transcriptionObjects.add(Map.of(
                "text", text,
                "language", "en",
                "confidence", 0.95,
                "timestamp", System.currentTimeMillis()
            ));
        }
        
        return ResponseEntity.ok(Map.of(
            "transcriptions", transcriptionObjects,
            "currentSentence", status.get("currentSentence"),
            "active", status.get("active"),
            "sentenceCount", status.get("sentenceCount"),
            "lastPartialText", status.get("lastPartialText"),
            "timestamp", System.currentTimeMillis()
        ));
    }
    
    @GetMapping("/transcriptions/current")
    public ResponseEntity<Map<String, Object>> getCurrentTranscription() {
        // Get only the current sentence being built
        String currentSentence = microphoneControl.getCurrentSentence();
        
        return ResponseEntity.ok(Map.of(
            "currentSentence", currentSentence,
            "active", microphoneControl.isMicrophoneActive(),
            "timestamp", System.currentTimeMillis()
        ));
    }
    
    @PostMapping("/session/clear")
    public ResponseEntity<Map<String, String>> clearSession() {
        sessionData.clear();
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Session cleared"
        ));
    }
}
