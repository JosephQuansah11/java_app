package echobridge.com.java_app.core.services;

import java.util.concurrent.CompletionStage;

public interface SpeechRecognitionService {

    /**
     * Transcribe audio samples to text
     * @param audioSamples 16-bit PCM samples at 16kHz
     * @return CompletionStage with transcribed text (may be empty if no speech detected)
     */
    CompletionStage<String> transcribe(short[] audioSamples);
}
