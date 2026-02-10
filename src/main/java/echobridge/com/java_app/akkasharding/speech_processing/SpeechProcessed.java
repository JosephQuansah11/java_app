package echobridge.com.java_app.akkasharding.speech_processing;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public final class SpeechProcessed implements SpeechProcessingEvent {
    String originalText;
    String translatedText;
    String audioOutput;
    LocalDateTime timestamp;
}
