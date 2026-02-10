package echobridge.com.java_app.akkasharding.speech_processing;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SpeechReply implements CborSerializable {
    SpeechProcessingResponse response;  // null if history query
    ProcessingHistory history;          // null if processing command
    
    public static SpeechReply fromResponse(SpeechProcessingResponse r) {
        return new SpeechReply(r, null);
    }
    
    public static SpeechReply fromHistory(ProcessingHistory h) {
        return new SpeechReply(null, h);
    }
}

