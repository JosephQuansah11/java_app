package echobridge.com.java_app.akkasharding.speech_processing;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public final class ProcessingFailed implements SpeechProcessingEvent {
    String errorMessage;
    LocalDateTime timestamp;
}