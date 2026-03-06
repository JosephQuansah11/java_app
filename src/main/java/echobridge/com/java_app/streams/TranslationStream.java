package echobridge.com.java_app.streams;

import akka.actor.ActorSystem;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Source;
import akka.NotUsed;

import echobridge.com.java_app.core.services.TranslationService;
import echobridge.com.java_app.domain.data_structure.TranscriptionResult;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TranslationStream {
    
    private final ActorSystem actorSystem;
    private final TranslationService translationService;
    
    public TranslationStream(ActorSystem actorSystem, 
                           TranslationService translationService) {
        this.actorSystem = actorSystem;
        this.translationService = translationService;
    }
    
    /**
     * Creates a flow that translates transcription results
     */
    public Flow<TranscriptionResult, TranscriptionResult, NotUsed> createTranslationFlow(
            String sourceLanguage, String targetLanguage) {
        return Flow.of(TranscriptionResult.class)
            .mapAsync(4, transcriptionResult -> {
                // Process translation in parallel
                String text = transcriptionResult.getFinalText();
                log.info("🔄 Translating: '{}' from {} to {}", text, sourceLanguage, targetLanguage);
                
                return translationService.translate(text, sourceLanguage, targetLanguage)
                    .thenApply(translatedText -> {
                        transcriptionResult.setTranslatedText(translatedText);
                        log.info("✅ Translation completed: '{}' -> '{}'", text, translatedText);
                        return transcriptionResult;
                    });
            })
            .filter(result -> result.getTranslatedText() != null && !result.getTranslatedText().trim().isEmpty());
    }
}
