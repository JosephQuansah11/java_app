package echobridge.com.java_app.domain.flow;

import org.springframework.stereotype.Component;

import akka.NotUsed;
import akka.stream.javadsl.Flow;
import echobridge.com.java_app.core.services.EnhancedSpeechRecognitionService;
import echobridge.com.java_app.core.services.EnhancedTextToSpeechService;
import echobridge.com.java_app.core.services.TranslationService;
import echobridge.com.java_app.domain.data_structure.AudioChunk;
import echobridge.com.java_app.domain.data_structure.AudioOutput;
import echobridge.com.java_app.domain.data_structure.Transcription;
import echobridge.com.java_app.domain.data_structure.Translation;
import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class SpeechProcessingFlow {
    private final EnhancedSpeechRecognitionService asr;
    private final TranslationService translator;
    private final EnhancedTextToSpeechService tts;

    // Flow 1: Audio → Text
    public Flow<AudioChunk, Transcription, NotUsed> transcriptionFlow() {
        return Flow.of(AudioChunk.class)
            .mapAsync(4, chunk -> 
                asr.transcribe(chunk.samples())
                    .thenApply(text -> new Transcription(
                        text, 
                        detectLanguage(text), 
                        0.95 // confidence
                    ))
            )
            .filter(t -> !t.text().isBlank());
    }

    // NEW: Accepts raw short[] for convenience
    public Flow<short[], Transcription, NotUsed> transcriptionFlowRaw() {
        return Flow.of(short[].class)
            .map(samples -> new AudioChunk(samples, 16000))
            .via(transcriptionFlow());
    }
    
    // Or make the original more flexible
    public Flow<short[], Transcription, NotUsed> transcriptionFlow(int sampleRate) {
        return Flow.of(short[].class)
            .map(samples -> new AudioChunk(samples, sampleRate))
            .via(transcriptionFlow());
    }

    private String detectLanguage(String text) {
        return "en";
    }

    // Flow 2: Text → Translated Text  
    public Flow<Transcription, Translation, NotUsed> translationFlow(String targetLang) {
        return Flow.of(Transcription.class)
            .mapAsync(4, trans -> 
                translator.translate(trans.text(), trans.language(), targetLang)
                    .thenApply(translated -> new Translation(
                        trans.text(),
                        translated,
                        trans.language(),
                        targetLang
                    ))
            );
    }

    // Flow 3: Text → Audio
    public Flow<Translation, AudioOutput, NotUsed> textToSpeechFlow() {
        return Flow.of(Translation.class)
            .mapAsync(2, trans -> 
                tts.synthesize(trans.translatedText(), trans.toLanguage())
                    .thenApply(pcm -> new AudioOutput(pcm, "pcm_s16le"))
            );
    }

    // Composite: Chain them together
    public Flow<AudioChunk, AudioOutput, NotUsed> fullPipeline(String targetLang) {
        return transcriptionFlow()
            .via(translationFlow(targetLang))
            .via(textToSpeechFlow());
    }
}
