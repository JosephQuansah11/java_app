package echobridge.com.java_app.core.services;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class BrowserTTSService {

    /**
     * Service for managing browser-based Text-to-Speech operations.
     * This service coordinates with the frontend to use the Web Speech API
     * for immediate audio feedback without server-side audio processing.
     */

    public CompletionStage<Void> speakText(String sessionId, String text, String voice, double speed, boolean isPartial) {
        return CompletableFuture.runAsync(() -> {
            // This service doesn't actually produce audio
            // Instead, it sends TTS requests to the client via WebSocket
            // The actual TTS is handled by the browser's Web Speech API
            
            log.debug("TTS request for session {}: '{}' (partial: {}, voice: {}, speed: {})", 
                    sessionId, text, isPartial, voice, speed);
            
            // The WebSocket controller will handle sending this to the client
            // This method exists for interface consistency and future extensions
        });
    }

    public CompletionStage<Map<String, Object>> getAvailableVoices() {
        return CompletableFuture.supplyAsync(() -> {
            // Return common browser voices that are widely supported
            return Map.of(
                "voices", Map.of(
                    "en-US", Map.of(
                        "default", "Google US English",
                        "female", "Google US English Female",
                        "male", "Google US English Male"
                    ),
                    "es-ES", Map.of(
                        "default", "Google Spanish",
                        "female", "Google Spanish Female"
                    ),
                    "fr-FR", Map.of(
                        "default", "Google French",
                        "female", "Google French Female"
                    ),
                    "de-DE", Map.of(
                        "default", "Google German",
                        "female", "Google German Female"
                    )
                )
            );
        });
    }

    public boolean isTTSAvailable() {
        // Browser TTS is generally available in modern browsers
        return true;
    }

    public Map<String, Object> getTTSConfiguration() {
        return Map.of(
            "type", "browser_native",
            "api", "Web Speech API",
            "supported", true,
            "features", Map.of(
                "partialPlayback", true,
                "voiceSelection", true,
                "speedControl", true,
                "pitchControl", true,
                "volumeControl", true
            )
        );
    }
}
