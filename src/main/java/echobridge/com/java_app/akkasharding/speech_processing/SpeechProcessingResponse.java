package echobridge.com.java_app.akkasharding.speech_processing;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SpeechProcessingResponse implements CborSerializable {
    String originalText;
    String translatedText;
    String audioOutput;
    LocalDateTime timestamp;
    boolean success;
    String errorMessage;
}
