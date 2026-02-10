package echobridge.com.java_app.core.services;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.io.File;
import java.io.IOException;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.vosk.Model;
import org.vosk.Recognizer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class HttpSpeechRecognitionServiceImpl implements SpeechRecognitionService {

    @Value("${vosk.model.path:src/main/resources/vosk-model-small-en-us-0.15}")
    private String modelPath;
    

    // @Override
    // public CompletionStage<String> transcribe(short[] audioSamples) {
    // return CompletableFuture.supplyAsync(() -> {
    // // Convert shorts to bytes for transmission
    // ByteBuffer buffer = ByteBuffer.allocate(audioSamples.length * 2)
    // .order(ByteOrder.LITTLE_ENDIAN);
    // buffer.asShortBuffer().put(audioSamples);
    // String base64Audio = Base64.getEncoder().encodeToString(buffer.array());

    // // Call your speech-to-text API
    // Map<String, String> request = Map.of(
    // "audio", base64Audio,
    // "format", "pcm_s16le",
    // "sampleRate", "16000"
    // );

    // try {
    // return restClient.post()
    // .uri("http://localhost:8080/api/asr/transcribe")
    // .contentType(MediaType.APPLICATION_JSON)
    // .body(request)
    // .retrieve()
    // .body(new ParameterizedTypeReference<Map<String, String>>() {})
    // .getOrDefault("text", "");
    // } catch (Exception e) {
    // log.error("ASR API call failed", e);
    // return "";
    // }
    // }, asyncExecutor);
    // }

    @Override
    public CompletionStage<String> transcribe(short[] audioSamples) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validate model path exists
                File modelFile = new File(modelPath);
                if (!modelFile.exists()) {
                    log.error("Vosk model not found at path: {}", modelPath);
                    return "";
                }
                
                Model model = new Model(modelPath);
                try (Recognizer recognizer = new Recognizer(model, 16000)) {
                    // Convert shorts to bytes
                    byte[] bytes = shortsToBytes(audioSamples);

                    if (recognizer.acceptWaveForm(bytes, bytes.length)) {
                        String result = recognizer.getResult();
                        // Parse JSON: {"text": "hello world"}
                        return extractText(result);
                    }

                    return recognizer.getPartialResult();
                }
            } catch (IOException e) {
                log.error("Vosk error", e);
                return "";
            }
        });
    }

    private byte[] shortsToBytes(short[] shorts) {
        ByteBuffer buffer = ByteBuffer.allocate(shorts.length * 2);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.asShortBuffer().put(shorts);
        return buffer.array();
    }

    private String extractText(String json) {
        // Simple JSON parsing for {"text": "hello world"}
        if (json == null || json.isEmpty())
            return "";

        // Simple JSON parsing - you could use Jackson if preferred
        int start = json.indexOf("\"text\" : \"") + 10;
        if (start < 10)
            start = json.indexOf("\"text\":\"") + 8; // try without spaces

        if (start < 8)
            return "";

        int end = json.indexOf("\"", start);
        if (end == -1)
            return "";

        return json.substring(start, end).trim();
    }


    
    private String extractPartialText(String json) {
        // Partial results: {"partial": "hello wo"}
        if (json == null || json.isEmpty()) return "";
        
        int start = json.indexOf("\"partial\" : \"") + 13;
        if (start < 13) start = json.indexOf("\"partial\":\"") + 11;
        
        if (start < 11) return "";
        
        int end = json.indexOf("\"", start);
        if (end == -1) return "";
        
        return json.substring(start, end).trim();
    }
}
