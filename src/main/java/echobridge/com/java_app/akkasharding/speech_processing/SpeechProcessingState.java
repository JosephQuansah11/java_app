package echobridge.com.java_app.akkasharding.speech_processing;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class SpeechProcessingState implements CborSerializable {
    public final String sessionId;
    public final List<SpeechProcessingResponse> processingHistory;
    
    public SpeechProcessingState(String sessionId) {
        this.sessionId = sessionId;
        this.processingHistory = new ArrayList<>();
    }
    
    public SpeechProcessingState(String sessionId, List<SpeechProcessingResponse> processingHistory) {
        this.sessionId = sessionId;
        this.processingHistory = new ArrayList<>(processingHistory);
    }
    
    public SpeechProcessingState addResponse(SpeechProcessingResponse response) {
        List<SpeechProcessingResponse> newHistory = new ArrayList<>(processingHistory);
        newHistory.add(response);
        return new SpeechProcessingState(sessionId, newHistory);
    }
    
    public List<SpeechProcessingResponse> getHistory() {
        return new ArrayList<>(processingHistory);
    }
}
