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

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EnhancedTranslationService extends TranslationService {
    
    @Value("${libretranslate.url:http://localhost:5000}")
    private String libreTranslateUrl;
    
    @Value("${argos.translate.url:http://localhost:5001}")
    private String argosTranslateUrl;
    
    @Value("${translation.provider:libre}")
    private String defaultProvider;
    
    private final RestTemplate restTemplate;
    
    public EnhancedTranslationService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    @Override
    public CompletionStage<String> translate(String text, String fromLanguage, String toLanguage) {
        return translateWithProvider(text, fromLanguage, toLanguage, defaultProvider);
    }
    
    public CompletionStage<String> translateWithProvider(String text, String fromLang, String toLang, String provider) {
        return switch (provider.toLowerCase()) {
            case "argos" -> translateWithArgos(text, fromLang, toLang);
            case "libre", "libretranslate" -> translateWithLibre(text, fromLang, toLang);
            default -> translateWithLibre(text, fromLang, toLang);
        };
    }
    
    private CompletionStage<String> translateWithLibre(String text, String fromLang, String toLang) {
        try {
            LibreTranslateRequest request = new LibreTranslateRequest(
                text, fromLang, toLang, "text"
            );
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<LibreTranslateRequest> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<LibreTranslateResponse> response = restTemplate.postForEntity(
                libreTranslateUrl + "/translate", entity, LibreTranslateResponse.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return CompletableFuture.completedFuture(response.getBody().translatedText());
            } else {
                log.error("LibreTranslate error: {}", response.getStatusCode());
                return fallbackTranslate(text, fromLang, toLang, "LibreTranslate");
            }
            
        } catch (Exception e) {
            log.error("LibreTranslate error", e);
            return fallbackTranslate(text, fromLang, toLang, "LibreTranslate");
        }
    }
    
    private CompletionStage<String> translateWithArgos(String text, String fromLang, String toLang) {
        try {
            ArgosTranslateRequest request = new ArgosTranslateRequest(text, fromLang, toLang);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<ArgosTranslateRequest> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<ArgosTranslateResponse> response = restTemplate.postForEntity(
                argosTranslateUrl + "/translate", entity, ArgosTranslateResponse.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return CompletableFuture.completedFuture(response.getBody().translatedText());
            } else {
                log.error("ArgosTranslate error: {}", response.getStatusCode());
                return fallbackTranslate(text, fromLang, toLang, "ArgosTranslate");
            }
            
        } catch (Exception e) {
            log.error("ArgosTranslate error", e);
            return fallbackTranslate(text, fromLang, toLang, "ArgosTranslate");
        }
    }
    
    private CompletionStage<String> fallbackTranslate(String text, String fromLang, String toLang, String serviceName) {
        log.warn("Using fallback translation due to {} unavailability", serviceName);
        
        // Simple fallback: return with language indicators
        String fallback = "[" + fromLang.toUpperCase() + "→" + toLang.toUpperCase() + "] " + text;
        return CompletableFuture.completedFuture(fallback);
    }
    
    public record LibreTranslateRequest(String q, String source, String target, String format) {}
    public record LibreTranslateResponse(String translatedText, String sourceLanguage, String targetLanguage) {}
    public record ArgosTranslateRequest(String text, String from, String to) {}
    public record ArgosTranslateResponse(String translatedText, String fromLanguage, String toLanguage) {}
}
