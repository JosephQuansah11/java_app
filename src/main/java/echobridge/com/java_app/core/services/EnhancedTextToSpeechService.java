package echobridge.com.java_app.core.services;

import java.net.URLEncoder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EnhancedTextToSpeechService extends TextToSpeechService {
    
    @Value("${coqui.tts.url:http://localhost:5002}")
    private String coquiTtsUrl;
    

    @Value("${piper.tts.url}")
    private String piperTtsUrl;
    
    @Value("${tts.provider:coqui}")
    private String defaultProvider;
    
    private final RestTemplate restTemplate;
    
    public EnhancedTextToSpeechService(RestTemplate restTemplate,
                                     @Value("${piper.path:/usr/local/bin/piper}") String piperPath,
                                     @Value("${executor.corePoolSize:4}") int corePoolSize,
                                     @Value("${executor.maxPoolSize:8}") int maxPoolSize,
                                     @Value("${executor.queueCapacity:100}") int queueCapacity) {
        super(piperPath, corePoolSize, maxPoolSize, queueCapacity);
        this.restTemplate = restTemplate;
    }
    
    @Override
    public CompletionStage<byte[]> synthesize(String text, String language) {
        return synthesizeWithProvider(text, language, defaultProvider);
    }
    
    public CompletionStage<byte[]> synthesizeWithProvider(String text, String language, String provider) {
        return switch (provider.toLowerCase()) {
            case "piper" -> synthesizeWithPiper(text, language);
            case "coqui", "default" -> synthesizeWithCoqui(text, language);
            default -> synthesizeWithCoqui(text, language);
        };
    }
    
    private CompletionStage<byte[]> synthesizeWithCoqui(String text, String language) {
        try {
            CoquiTtsRequest request = new CoquiTtsRequest(text, language, "default_speaker");
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<CoquiTtsRequest> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<byte[]> response = restTemplate.postForEntity(
                coquiTtsUrl + "/api/tts", entity, byte[].class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return CompletableFuture.completedFuture(response.getBody());
            } else {
                log.error("Coqui TTS error: {}", response.getStatusCode());
                return CompletableFuture.completedFuture(new byte[0]);
            }
            
        } catch (RestClientException e) {
            log.error("Coqui TTS error", e);
            return CompletableFuture.completedFuture(new byte[0]);
        }
    }
    
    private CompletionStage<byte[]> synthesizeWithPiper(String text, String language) {
        try {
            // Clean the text - remove any JSON residue
            final String cleanText = text.replaceAll("\\{\"partial\"\\s*:\\s*\"[^\"]*\"\\}", "").trim();
            final String finalText = cleanText.isEmpty() ? text : cleanText;
            
            // Try different Piper endpoints
            String[] endpoints = {"/synthesize", "/api/tts", "/tts", "/"};
            
            return CompletableFuture.supplyAsync(() -> {
                for (String endpoint : endpoints) {
                    try {
                        String url = piperTtsUrl + endpoint;
                        
                        // Try different request formats
                        PiperTtsRequest request = new PiperTtsRequest(finalText, language, "medium");
                        
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        HttpEntity<PiperTtsRequest> entity = new HttpEntity<>(request, headers);
                        
                        log.debug("Trying Piper endpoint: {} with text: {}", url, finalText);
                        ResponseEntity<byte[]> response = restTemplate.postForEntity(url, entity, byte[].class);
                        
                        if (response.getStatusCode().is2xxSuccessful()) {
                            log.info("Successfully used Piper endpoint: {}", endpoint);
                            return response.getBody();
                        }
                    } catch (RestClientException e) {
                        log.debug("Piper endpoint {} failed: {}", endpoint, e.getMessage());
                    }
                }
                
                // If all endpoints fail, try GET request as fallback
                try {
                    String getUrl = piperTtsUrl + "/synthesize?text=" + 
                                   URLEncoder.encode(finalText, "UTF-8") + 
                                   "&voice=" + language + "-medium";
                    ResponseEntity<byte[]> getResponse = restTemplate.getForEntity(getUrl, byte[].class);
                    
                    if (getResponse.getStatusCode().is2xxSuccessful()) {
                        log.info("Successfully used Piper GET endpoint");
                        return getResponse.getBody();
                    }
                } catch (Exception e) {
                    log.debug("Piper GET endpoint failed: {}", e.getMessage());
                }
                
                // Final fallback: try our custom Piper server format
                try {
                    String customUrl = piperTtsUrl + "/synthesize";
                    PiperTtsRequest customRequest = new PiperTtsRequest(finalText, language, "medium");
                    
                    HttpHeaders customHeaders = new HttpHeaders();
                    customHeaders.setContentType(MediaType.APPLICATION_JSON);
                    HttpEntity<PiperTtsRequest> customEntity = new HttpEntity<>(customRequest, customHeaders);
                    
                    ResponseEntity<byte[]> customResponse = restTemplate.postForEntity(customUrl, customEntity, byte[].class);
                    
                    if (customResponse.getStatusCode().is2xxSuccessful()) {
                        log.info("Successfully used custom Piper server");
                        return customResponse.getBody();
                    }
                } catch (RestClientException e) {
                    log.debug("Custom Piper server failed: {}", e.getMessage());
                }
                
                throw new RuntimeException("All Piper endpoints failed");
            }).exceptionally(throwable -> {
                log.error("Piper TTS error - all endpoints failed, falling back to silent TTS", throwable);
                return new byte[0]; // Return empty audio as fallback
            });
        } catch (Exception e) {
            log.error("Piper TTS setup error", e);
            return CompletableFuture.completedFuture(new byte[0]);
        }
    }
            
    
    public record CoquiTtsRequest(String text, String language, String speaker_id) {}
    public record PiperTtsRequest(String text, String language, String voice_model) {}
}
