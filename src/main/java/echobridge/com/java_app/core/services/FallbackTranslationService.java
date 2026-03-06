package echobridge.com.java_app.core.services;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.Map;
import java.util.HashMap;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FallbackTranslationService {
    
    private final Map<String, Map<String, String>> simpleTranslations = new HashMap<>();
    
    public FallbackTranslationService() {
        initializeSimpleTranslations();
    }
    
    private void initializeSimpleTranslations() {
        // English to Spanish
        Map<String, String> enToEs = new HashMap<>();
        enToEs.put("hello", "hola");
        enToEs.put("goodbye", "adiós");
        enToEs.put("thank you", "gracias");
        enToEs.put("please", "por favor");
        enToEs.put("yes", "sí");
        enToEs.put("no", "no");
        enToEs.put("how are you", "cómo estás");
        enToEs.put("my name is", "me llamo");
        enToEs.put("what is your name", "cómo te llamas");
        enToEs.put("nice to meet you", "gusto en conocerte");
        simpleTranslations.put("en-es", enToEs);
        
        // English to French
        Map<String, String> enToFr = new HashMap<>();
        enToFr.put("hello", "bonjour");
        enToFr.put("goodbye", "au revoir");
        enToFr.put("thank you", "merci");
        enToFr.put("please", "s'il vous plaît");
        enToFr.put("yes", "oui");
        enToFr.put("no", "non");
        enToFr.put("how are you", "comment allez-vous");
        enToFr.put("my name is", "je m'appelle");
        enToFr.put("what is your name", "comment vous appelez-vous");
        enToFr.put("nice to meet you", "enchanté");
        simpleTranslations.put("en-fr", enToFr);
        
        // English to German
        Map<String, String> enToDe = new HashMap<>();
        enToDe.put("hello", "hallo");
        enToDe.put("goodbye", "auf wiedersehen");
        enToDe.put("thank you", "danke");
        enToDe.put("please", "bitte");
        enToDe.put("yes", "ja");
        enToDe.put("no", "nein");
        enToDe.put("how are you", "wie geht es dir");
        enToDe.put("my name is", "ich heiße");
        enToDe.put("what is your name", "wie ist dein name");
        enToDe.put("nice to meet you", "schön dich zu treffen");
        simpleTranslations.put("en-de", enToDe);
    }
    
    public CompletionStage<String> translate(String text, String fromLanguage, String toLanguage) {
        return CompletableFuture.supplyAsync(() -> {
            String key = fromLanguage.toLowerCase() + "-" + toLanguage.toLowerCase();
            String normalizedText = text.toLowerCase().trim();
            
            Map<String, String> translations = simpleTranslations.get(key);
            if (translations != null) {
                String translated = translations.get(normalizedText);
                if (translated != null) {
                    log.debug("Found translation for '{}' -> '{}'", text, translated);
                    return translated;
                }
            }
            
            // If no translation found, return a fallback message
            log.debug("No translation found for '{}' from {} to {}, using fallback", text, fromLanguage, toLanguage);
            return "[" + fromLanguage.toUpperCase() + "→" + toLanguage.toUpperCase() + "] " + text;
        });
    }
}
