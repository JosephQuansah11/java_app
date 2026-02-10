package echobridge.com.java_app.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import echobridge.com.java_app.adapters.SpeechProcessingAdapter;
import echobridge.com.java_app.domain.data_structure.Translation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/echo")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class EchoBridgeController {
    
    private final SpeechProcessingAdapter speechProcessingAdapter;
    
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
        
        try {
            Translation result = speechProcessingAdapter.translateWithDistribution(text, targetLanguage).get();
            return ResponseEntity.ok(Map.of(
                "originalText", text,
                "translatedText", result.translatedText(),
                "fromLanguage", result.fromLanguage(),
                "toLanguage", result.toLanguage(),
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.error("Translation failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Translation failed: " + e.getMessage()
            ));
        }
    }
    
    @GetMapping("/results/latest")
    public ResponseEntity<Map<String, Object>> getLatestResults() {
        return ResponseEntity.ok(Map.of(
            "transcriptions", sessionData.getOrDefault("transcriptions", new java.util.ArrayList<>()),
            "translations", sessionData.getOrDefault("translations", new java.util.ArrayList<>()),
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
