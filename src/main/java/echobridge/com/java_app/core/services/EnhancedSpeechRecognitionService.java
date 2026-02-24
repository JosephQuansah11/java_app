package echobridge.com.java_app.core.services;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.vosk.Model;
import org.vosk.Recognizer;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EnhancedSpeechRecognitionService implements SpeechRecognitionService {
    
    @Value("${vosk.model.path:src/main/resources/vosk-model-small-en-us-0.15}")
    private String voskModelPath;
    
    @Value("${whisper.api.url:http://localhost:9000/asr}")
    private String whisperApiUrl;
    
    @Value("${speech.recognition.provider:vosk}")
    private String defaultProvider;
    
    private final RestTemplate restTemplate;
    
    public EnhancedSpeechRecognitionService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    @Override
    public CompletionStage<String> transcribe(short[] audioSamples) {
        return switch (defaultProvider.toLowerCase()) {
            case "whisper" -> transcribeWithWhisper(audioSamples);
            case "vosk" -> transcribeWithVosk(audioSamples);
            default -> transcribeWithVosk(audioSamples);
        };
    }
    
    public CompletionStage<String> transcribeWithProvider(short[] audioSamples, String provider) {
        return switch (provider.toLowerCase()) {
            case "whisper" -> transcribeWithWhisper(audioSamples);
            case "vosk" -> transcribeWithVosk(audioSamples);
            default -> transcribeWithVosk(audioSamples);
        };
    }
    
    private CompletionStage<String> transcribeWithVosk(short[] audioSamples) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                java.io.File modelFile = new java.io.File(voskModelPath);
                if (!modelFile.exists()) {
                    log.error("Vosk model not found at path: {}", voskModelPath);
                    return "";
                }
                
                Model model = new Model(voskModelPath);
                try (Recognizer recognizer = new Recognizer(model, 16000)) {
                    byte[] bytes = shortsToBytes(audioSamples);
                    
                    if (recognizer.acceptWaveForm(bytes, bytes.length)) {
                        String result = recognizer.getResult();
                        return extractText(result);
                    }
                    
                    return recognizer.getPartialResult();
                }
            } catch (Exception e) {
                log.error("Vosk transcription error", e);
                return "";
            }
        });
    }
    
    private CompletionStage<String> transcribeWithWhisper(short[] audioSamples) {
        try {
            byte[] audioBytes = shortsToBytes(audioSamples);
            String base64Audio = java.util.Base64.getEncoder().encodeToString(audioBytes);
            
            WhisperRequest request = new WhisperRequest(
                base64Audio,
                "audio/wav",
                16000,
                "en"
            );
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<WhisperRequest> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<WhisperResponse> response = restTemplate.postForEntity(
                whisperApiUrl, entity, WhisperResponse.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return CompletableFuture.completedFuture(response.getBody().text());
            } else {
                log.error("Whisper API error: {}", response.getStatusCode());
                return CompletableFuture.completedFuture("");
            }
            
        } catch (Exception e) {
            log.error("Whisper transcription error", e);
            return CompletableFuture.completedFuture("");
        }
    }
    
    private byte[] shortsToBytes(short[] shorts) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(shorts.length * 2);
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buffer.asShortBuffer().put(shorts);
        return buffer.array();
    }
    
    private String extractText(String json) {
        if (json == null || json.isEmpty()) return "";
        
        int start = json.indexOf("\"text\" : \"") + 10;
        if (start < 10) start = json.indexOf("\"text\":\"") + 8;
        
        if (start < 8) return "";
        
        int end = json.indexOf("\"", start);
        if (end == -1) return "";
        
        return json.substring(start, end).trim();
    }
    
    public record WhisperRequest(
        String audio,
        String format,
        int sampleRate,
        String language
    ) {}
    
    public record WhisperResponse(
        String text,
        double confidence,
        String language
    ) {}
}
