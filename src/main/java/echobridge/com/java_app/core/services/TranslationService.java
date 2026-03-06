package echobridge.com.java_app.core.services;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.HashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

@Service
@Primary
@Slf4j
public class TranslationService {

    private final RestTemplate restTemplate;
    
    @Value("${translation.provider:libre}")
    private String translationProvider;
    
    @Value("${libretranslate.url:http://localhost:5000}")
    private String libreTranslateUrl;
    
    @Value("${argos.translate.url:http://localhost:5001}")
    private String argosTranslateUrl;

    public TranslationService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public CompletionStage<String> translate(String text, String sourceLanguage, String targetLanguage) {
        log.info("🌐 Translation request: '{}' from {} to {}", text, sourceLanguage, targetLanguage);
        
        if (text == null || text.trim().isEmpty()) {
            return CompletableFuture.completedFuture(text);
        }
        
        // Handle auto-detection
        if ("auto".equals(sourceLanguage)) {
            sourceLanguage = detectLanguage(text);
        }
        
        // Try different translation providers
        switch (translationProvider.toLowerCase()) {
            case "libre":
                log.info("📚 Using LibreTranslate provider");
                return translateWithLibre(text, sourceLanguage, targetLanguage);
            case "argos":
                log.info("🗣️ Using Argos Translate provider");
                return translateWithArgos(text, sourceLanguage, targetLanguage);
            case "mock":
                log.info("🎭 Using Mock translation provider");
                return translateWithMock(text, sourceLanguage, targetLanguage);
            default:
                log.info("📚 Defaulting to LibreTranslate provider");
                return translateWithLibre(text, sourceLanguage, targetLanguage);
        }
    }
    
    private CompletionStage<String> translateWithLibre(String text, String sourceLanguage, String targetLanguage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = libreTranslateUrl + "/translate";
                
                Map<String, Object> request = new HashMap<>();
                request.put("q", text);
                request.put("source", mapLanguageCode(sourceLanguage, "libre"));
                request.put("target", mapLanguageCode(targetLanguage, "libre"));
                request.put("format", "text");
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
                
                ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    String translatedText = (String) response.getBody().get("translatedText");
                    log.info("LibreTranslate successful: {}", translatedText);
                    return translatedText;
                }
                
            } catch (Exception e) {
                log.warn("LibreTranslate failed: {}", e.getMessage());
            }
            
            // Fallback to mock translation
            return translateWithMock(text, sourceLanguage, targetLanguage).toCompletableFuture().join();
        });
    }
    
    private CompletionStage<String> translateWithArgos(String text, String sourceLanguage, String targetLanguage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = argosTranslateUrl + "/translate";
                
                Map<String, Object> request = new HashMap<>();
                request.put("text", text);
                request.put("from", mapLanguageCode(sourceLanguage, "argos"));
                request.put("to", mapLanguageCode(targetLanguage, "argos"));
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
                
                ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    String translatedText = (String) response.getBody().get("translatedText");
                    log.info("ArgosTranslate successful: {}", translatedText);
                    return translatedText;
                }
                
            } catch (Exception e) {
                log.warn("ArgosTranslate failed: {}", e.getMessage());
            }
            
            // Fallback to mock translation
            return translateWithMock(text, sourceLanguage, targetLanguage).toCompletableFuture().join();
        });
    }
    
    private CompletionStage<String> translateWithMock(String text, String sourceLanguage, String targetLanguage) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Using mock translation from {} to {}", sourceLanguage, targetLanguage);
            
            // Simple mock translations for demonstration
            Map<String, Map<String, String>> mockTranslations = new HashMap<>();
            
            // English to other languages
            Map<String, String> enTranslations = new HashMap<>();
            enTranslations.put("es", "[ES] " + text);
            enTranslations.put("fr", "[FR] " + text);
            enTranslations.put("de", "[DE] " + text);
            enTranslations.put("zh", "[ZH] " + text);
            enTranslations.put("ja", "[JA] " + text);
            mockTranslations.put("en", enTranslations);
            
            // Spanish to other languages
            Map<String, String> esTranslations = new HashMap<>();
            esTranslations.put("en", "[EN] " + text);
            esTranslations.put("fr", "[FR] " + text);
            esTranslations.put("de", "[DE] " + text);
            mockTranslations.put("es", esTranslations);
            
            // French to other languages
            Map<String, String> frTranslations = new HashMap<>();
            frTranslations.put("en", "[EN] " + text);
            frTranslations.put("es", "[ES] " + text);
            frTranslations.put("de", "[DE] " + text);
            mockTranslations.put("fr", frTranslations);
            
            Map<String, String> sourceTranslations = mockTranslations.get(sourceLanguage);
            if (sourceTranslations != null) {
                String translated = sourceTranslations.get(targetLanguage);
                if (translated != null) {
                    return translated;
                }
            }
            
            // Default fallback
            return "[" + targetLanguage.toUpperCase() + "] " + text;
        });
    }
    
    private String detectLanguage(String text) {
        // Simple language detection based on common words
        if (text.toLowerCase().matches(".*\\b(the|and|or|but|in|on|at|to|for|of|with|by)\\b.*")) {
            return "en";
        } else if (text.toLowerCase().matches(".*\\b(el|la|de|que|y|en|un|por|con|para|como)\\b.*")) {
            return "es";
        } else if (text.toLowerCase().matches(".*\\b(le|la|de|et|à|un|pour|dans|avec|sur|par)\\b.*")) {
            return "fr";
        } else if (text.toLowerCase().matches(".*\\b(der|die|das|und|oder|aber|in|an|zu|für|mit)\\b.*")) {
            return "de";
        }
        
        // Default to English if unsure
        return "en";
    }
    
    private String mapLanguageCode(String language, String provider) {
        Map<String, String> languageMap = new HashMap<>();
        
        if ("libre".equals(provider)) {
            // LibreTranslate language codes
            languageMap.put("en", "en");
            languageMap.put("es", "es");
            languageMap.put("fr", "fr");
            languageMap.put("de", "de");
            languageMap.put("zh", "zh");
            languageMap.put("ja", "ja");
            languageMap.put("auto", "auto");
        } else if ("argos".equals(provider)) {
            // Argos Translate language codes
            languageMap.put("en", "en");
            languageMap.put("es", "es");
            languageMap.put("fr", "fr");
            languageMap.put("de", "de");
            languageMap.put("zh", "zh");
            languageMap.put("ja", "ja");
        }
        
        return languageMap.getOrDefault(language, language);
    }
}
