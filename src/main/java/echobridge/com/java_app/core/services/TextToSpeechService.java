package echobridge.com.java_app.core.services;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ArrayBlockingQueue;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TextToSpeechService {
    
    private final Executor executor;
    private final String piperPath; // Path to piper executable
    private final Map<String, String> modelPaths; // Language → model file
    
    public TextToSpeechService(@Value("${piper.path:/usr/local/bin/piper}") String piperPath,
                               @Value("${executor.corePoolSize:4}") int corePoolSize,
                               @Value("${executor.maxPoolSize:8}") int maxPoolSize,
                               @Value("${executor.queueCapacity:100}") int queueCapacity) {
        this.piperPath = piperPath;
        this.executor = new ThreadPoolExecutor(corePoolSize, maxPoolSize, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(queueCapacity));
        this.modelPaths = Map.of(
            "en", "en_US-lessac-medium.onnx",
            "es", "es_ES-carlfm-x_low.onnx", // Download from Piper releases
            "fr", "fr_FR-siwis-medium.onnx",
            "de", "de_DE-thorsten-medium.onnx"
        );
    }
    
    public CompletionStage<byte[]> synthesize(String text, String language) {
        if (text == null || text.isBlank()) {
            return CompletableFuture.completedFuture(new byte[0]);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String model = modelPaths.getOrDefault(language, modelPaths.get("en"));
                
                // Run Piper TTS
                ProcessBuilder pb = new ProcessBuilder(
                    piperPath,
                    "--model", model,
                    "--output_file", "-", // Output to stdout
                    "--json-input" // Expect JSON for better control
                );
                
                Process process = pb.start();
                
                // Send text as JSON
                String jsonInput = String.format("{\"text\": \"%s\"}", 
                    text.replace("\"", "\\\""));
                process.getOutputStream().write(jsonInput.getBytes());
                process.getOutputStream().close();
                
                // Read PCM audio from stdout
                byte[] audio = process.getInputStream().readAllBytes();
                process.waitFor();
                
                log.debug("TTS generated {} bytes for: {}", audio.length, text);
                return audio;
                
            } catch (IOException | InterruptedException e) {
                log.error("TTS failed for: {}", text, e);
                return new byte[0];
            }
        }, executor);
    }
}