package echobridge.com.java_app.akkasharding.speech_processing;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProcessingHistory implements CborSerializable {
    String sessionId;
    List<SpeechProcessingResponse> history;
}
