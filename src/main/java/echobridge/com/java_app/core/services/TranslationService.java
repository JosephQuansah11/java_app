package echobridge.com.java_app.core.services;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
        // Fast path: skip logging overhead for common cases
        if (text == null || text.trim().isEmpty()) {
            return CompletableFuture.completedFuture(text);
        }
        
        // Handle auto-detection
        if ("auto".equals(sourceLanguage)) {
            sourceLanguage = detectLanguageFast(text);
        }
        
        // Direct fast translation with immediate response
        return translateFast(text, sourceLanguage, targetLanguage);
    }
    
    private CompletionStage<String> translateFast(String text, String sourceLanguage, String targetLanguage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Quick API call attempt
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
                    if (translatedText != null && !translatedText.trim().isEmpty()) {
                        // Log translation to terminal for user visibility
                        System.out.println("🌐 TRANSLATED: '" + text + "' -> '" + translatedText + "'");
                        return translatedText;
                    }
                }
            } catch (Exception e) {
                log.debug("Translation API unavailable: {}", e.getMessage());
            }
            
            // Immediate fallback with language tag
            String fallbackText = "[" + targetLanguage.toUpperCase() + "] " + text;
            System.out.println("🌐 TRANSLATED (fallback): '" + text + "' -> '" + fallbackText + "'");
            return fallbackText;
        });
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
                    if (translatedText != null && !translatedText.trim().isEmpty()) {
                        log.info("LibreTranslate successful: {}", translatedText);
                        return translatedText;
                    }
                }
                
            } catch (Exception e) {
                log.warn("LibreTranslate failed: {}", e.getMessage());
            }
            
            // Fallback to simple mock translation
            return "[" + targetLanguage.toUpperCase() + "] " + text;
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
            enTranslations.put("es", "El tiempo es agradable hoy");
            enTranslations.put("fr", "Le temps est agréable aujourd'hui");
            enTranslations.put("de", "Das Wetter ist heute schön");
            enTranslations.put("zh", "今天天气很好");
            enTranslations.put("ja", "今日は天気が良いです");
            mockTranslations.put("en", enTranslations);
            
            // Spanish to other languages
            Map<String, String> esTranslations = new HashMap<>();
            esTranslations.put("en", "The weather is nice today");
            esTranslations.put("fr", "Le temps est agréable aujourd'hui");
            esTranslations.put("de", "Das Wetter ist heute schön");
            mockTranslations.put("es", esTranslations);
            
            // French to other languages
            Map<String, String> frTranslations = new HashMap<>();
            frTranslations.put("en", "The weather is nice today");
            frTranslations.put("es", "El tiempo es agradable hoy");
            frTranslations.put("de", "Das Wetter ist heute schön");
            mockTranslations.put("fr", frTranslations);
            
            Map<String, String> sourceTranslations = mockTranslations.get(sourceLanguage);
            if (sourceTranslations != null) {
                String translated = sourceTranslations.get(targetLanguage);
                if (translated != null) {
                    return translated;
                }
            }
            
            // Default fallback
            return text;
        });
    }
    
    private String detectLanguageFast(String text) {
        // Fast language detection based on common words
        String lowerText = text.toLowerCase();
        if (lowerText.contains(" the ") || lowerText.contains(" and ") || lowerText.contains(" is ")) {
            return "en";
        } else if (lowerText.contains(" el ") || lowerText.contains(" la ") || lowerText.contains(" es ")) {
            return "es";
        } else if (lowerText.contains(" le ") || lowerText.contains(" et ") || lowerText.contains(" est ")) {
            return "fr";
        } else if (lowerText.contains(" der ") || lowerText.contains(" die ") || lowerText.contains(" und ")) {
            return "de";
        }
        return "en"; // Default to English
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
