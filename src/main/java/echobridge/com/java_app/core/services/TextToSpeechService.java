package echobridge.com.java_app.core.services;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ArrayBlockingQueue;
import java.io.OutputStream;
import java.io.InputStream;
import jakarta.annotation.PreDestroy;

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
        
        // Validate language parameter and make it effectively final
        final String validatedLanguage = (language == null || !modelPaths.containsKey(language)) ? "en" : language;
        if (!validatedLanguage.equals(language)) {
            log.warn("Unsupported language: {}, falling back to English", language);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            Process process = null;
            try {
                String model = modelPaths.get(validatedLanguage);
                
                // Run Piper TTS
                ProcessBuilder pb = new ProcessBuilder(
                    piperPath,
                    "--model", model,
                    "--output_file", "-", // Output to stdout
                    "--json-input" // Expect JSON for better control
                );
                
                process = pb.start();
                
                // Send text as JSON
                String jsonInput = String.format("{\"text\": \"%s\"}", 
                    text.replace("\"", "\\\""));
                
                // Use try-with-resources for proper stream cleanup
                try (OutputStream outputStream = process.getOutputStream()) {
                    outputStream.write(jsonInput.getBytes());
                    outputStream.flush();
                }
                
                // Read PCM audio from stdout with timeout
                byte[] audio;
                try (InputStream inputStream = process.getInputStream()) {
                    audio = inputStream.readAllBytes();
                }
                
                // Wait for process completion with timeout (30 seconds)
                if (!process.waitFor(30, TimeUnit.SECONDS)) {
                    log.error("TTS process timed out for text: {}", text);
                    process.destroyForcibly();
                    return new byte[0];
                }
                
                if (process.exitValue() != 0) {
                    log.error("TTS process failed with exit code: {} for text: {}", process.exitValue(), text);
                    return new byte[0];
                }
                
                log.debug("TTS generated {} bytes for: {}", audio.length, text);
                return audio;
                
            } catch (IOException | InterruptedException e) {
                log.error("TTS failed for: {}", text, e);
                if (process != null) {
                    process.destroyForcibly();
                }
                return new byte[0];
            }
        }, executor);
    }
    
    @PreDestroy
    public void shutdown() {
        if (executor instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor threadPool = (ThreadPoolExecutor) executor;
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}