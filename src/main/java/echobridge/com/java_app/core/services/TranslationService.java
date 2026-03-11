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
    
    // FIX: Remove trailing space from default URL
    @Value("${ghananlp.api.url:https://translation-api.ghananlp.org}")
    private String ghananlpApiUrl;
    
    @Value("${ghananlp.api.key:}")
    private String ghananlpApiKey;

    public TranslationService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public CompletionStage<String> translate(String text, String sourceLanguage, String targetLanguage) {
        if (text == null || text.trim().isEmpty()) {
            return CompletableFuture.completedFuture(text);
        }
        
        if ("auto".equals(sourceLanguage)) {
            sourceLanguage = detectLanguageFast(text);
        }else if (shouldUseGhanaNLP(sourceLanguage, targetLanguage)) {
            return translateWithGhanaNLP(text, sourceLanguage, targetLanguage);
        }
        
        return translateFast(text, sourceLanguage, targetLanguage);
    }

    private CompletionStage<String> translateFast(String text, String sourceLanguage, String targetLanguage) {
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
                        System.out.println("🌐 TRANSLATED: '" + text + "' -> '" + translatedText + "'");
                        return translatedText;
                    }
                }
            } catch (Exception e) {
                log.debug("Translation API unavailable: {}", e.getMessage());
            }
            
            String fallbackText = "[" + targetLanguage.toUpperCase() + "] " + text;
            System.out.println("🌐 TRANSLATED (fallback): '" + text + "' -> '" + fallbackText + "'");
            return fallbackText;
        });
    }

    private CompletionStage<String> translateWithGhanaNLP(String text, String sourceLanguage, String targetLanguage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // FIX: Use /v1/translate endpoint (not /translate)
                String url = ghananlpApiUrl.trim() + "/v1/translate";  // Also trim the URL
                
                String languagePair = sourceLanguage + "-" + targetLanguage;
                
                Map<String, Object> request = new HashMap<>();
                request.put("in", text);
                request.put("lang", languagePair);
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Ocp-Apim-Subscription-Key", ghananlpApiKey);
                
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
                
                // FIX: GhanaNLP returns plain string, not JSON object
                ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    String translatedText = response.getBody();
                    // Handle potential JSON string wrapping (if API returns quoted string)
                    if (translatedText.startsWith("\"") && translatedText.endsWith("\"")) {
                        translatedText = translatedText.substring(1, translatedText.length() - 1);
                    }
                    
                    if (!translatedText.trim().isEmpty()) {
                        log.info("Ghana NLP translation successful: '{}' -> '{}'", text, translatedText);
                        return translatedText;
                    }
                }
                
            } catch (Exception e) {
                log.warn("Ghana NLP translation failed for '{}': {}", text, e.getMessage());
            }
            
            // FIX: Wrap fallback in try-catch to prevent unhandled exceptions
            try {
                return translateFast(text, sourceLanguage, targetLanguage).toCompletableFuture().get();
            } catch (Exception fallbackEx) {
                log.error("Both Ghana NLP and LibreTranslate failed for '{}': {}", text, fallbackEx.getMessage());
                return "[" + targetLanguage.toUpperCase() + "] " + text;
            }
        });
    }

    private boolean shouldUseGhanaNLP(String sourceLanguage, String targetLanguage) {
        // GhanaNLP supports: Twi (tw), Ga (gaa), Dagbani (dag), Ewe (ee), Yoruba (yo), etc.
        return ("tw".equals(sourceLanguage) || "tw".equals(targetLanguage)) ||
               ("ak".equals(sourceLanguage) || "ak".equals(targetLanguage)) ||
               ("gaa".equals(sourceLanguage) || "gaa".equals(targetLanguage)) ||
               ("ee".equals(sourceLanguage) || "ee".equals(targetLanguage)) ||
               ("dag".equals(sourceLanguage) || "dag".equals(targetLanguage)) ||
               ("yo".equals(sourceLanguage) || "yo".equals(targetLanguage));
    }

    private String mapLanguageCode(String language, String provider) {
        // ... same as before ...
        Map<String, String> languageMap = new HashMap<>();
        
        if ("libre".equals(provider)) {
            languageMap.put("en", "en");
            languageMap.put("es", "es");
            languageMap.put("fr", "fr");
            languageMap.put("de", "de");
            languageMap.put("zh", "zh");
            languageMap.put("ja", "ja");
            languageMap.put("tw", "tw");
            languageMap.put("ak", "ak");
            languageMap.put("gaa", "gaa");
            languageMap.put("ee", "ee");
            languageMap.put("dag", "dag");
            languageMap.put("yo", "yo");
            languageMap.put("auto", "auto");
        } else if ("argos".equals(provider)) {
            languageMap.put("en", "en");
            languageMap.put("es", "es");
            languageMap.put("fr", "fr");
            languageMap.put("de", "de");
            languageMap.put("zh", "zh");
            languageMap.put("ja", "ja");
        } else if ("ghananlp".equals(provider)) {
            languageMap.put("en", "en");
            languageMap.put("tw", "tw");
            languageMap.put("ak", "ak");
            languageMap.put("gaa", "gaa");
            languageMap.put("ee", "ee");
            languageMap.put("dag", "dag");
            languageMap.put("yo", "yo");
            languageMap.put("auto", "auto");
        }
        
        return languageMap.getOrDefault(language, language);
    }

    private String detectLanguageFast(String text) {
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
        return "en";
    }
}