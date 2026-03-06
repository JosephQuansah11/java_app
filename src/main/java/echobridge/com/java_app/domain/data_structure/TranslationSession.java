package echobridge.com.java_app.domain.data_structure;

import lombok.Data;

@Data
public class TranslationSession {
    private String sessionId;
    private String sourceLanguage;
    private String targetLanguage;
    private boolean enableTTS;
    private boolean active;
    private String lastPartialText;
    private String lastFinalText;
    private String ttsVoice;
    private Double ttsSpeed;
    private String translationProvider;
}
