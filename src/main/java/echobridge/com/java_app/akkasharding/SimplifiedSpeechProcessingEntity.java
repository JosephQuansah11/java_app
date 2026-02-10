package echobridge.com.java_app.akkasharding;

import java.time.LocalDateTime;

import echobridge.com.java_app.akkasharding.speech_processing.GetProcessingHistory;
import echobridge.com.java_app.akkasharding.speech_processing.ProcessingHistory;
import echobridge.com.java_app.akkasharding.speech_processing.SpeechProcessingCommand;
import echobridge.com.java_app.akkasharding.speech_processing.SpeechProcessingResponse;
import echobridge.com.java_app.akkasharding.speech_processing.SpeechProcessingState;


public class SimplifiedSpeechProcessingEntity {
    
    private final String entityId;
    private SpeechProcessingState state;
    
    public SimplifiedSpeechProcessingEntity(String entityId) {
        this.entityId = entityId;
        this.state = new SpeechProcessingState(entityId);
    }
    
    public SpeechProcessingResponse processCommand(SpeechProcessingCommand command) {
        try {
            String originalText = extractTextFromAudio(command.getAudioData());
            String translatedText = translateText(originalText, command.getTargetLanguage());
            String audioOutput = generateSpeechFromText(translatedText);
            
            SpeechProcessingResponse response = new SpeechProcessingResponse(
                originalText, translatedText, audioOutput, LocalDateTime.now(), true, null
            );
            
            state = state.addResponse(response);
            return response;
                
        } catch (Exception e) {
            SpeechProcessingResponse response = new SpeechProcessingResponse(
                "", "", "", LocalDateTime.now(), false, e.getMessage()
            );
            
            state = state.addResponse(response);
            return response;
        }
    }
    
    public ProcessingHistory getHistory(GetProcessingHistory command) {
        return new ProcessingHistory(state.sessionId, state.getHistory());
    }
    
    private String extractTextFromAudio(String audioData) {
        return "Extracted text from audio";
    }
    
    private String translateText(String text, String targetLanguage) {
        return "Translated: " + text + " -> " + targetLanguage;
    }
    
    private String generateSpeechFromText(String text) {
        return "Generated audio from: " + text;
    }
}
