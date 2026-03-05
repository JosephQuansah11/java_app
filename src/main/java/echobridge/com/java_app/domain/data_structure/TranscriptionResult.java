package echobridge.com.java_app.domain.data_structure;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptionResult {
    private String partialText;
    private String finalText;
    private String translatedText;
    private double confidence;
    
    public boolean hasPartialText() {
        return partialText != null && !partialText.trim().isEmpty();
    }
    
    public boolean hasFinalText() {
        return finalText != null && !finalText.trim().isEmpty();
    }
    
    public boolean hasTranslatedText() {
        return translatedText != null && !translatedText.trim().isEmpty();
    }
    
    public String getDisplayText() {
        if (hasFinalText()) {
            return finalText;
        } else if (hasPartialText()) {
            return partialText;
        } else {
            return "";
        }
    }
}
