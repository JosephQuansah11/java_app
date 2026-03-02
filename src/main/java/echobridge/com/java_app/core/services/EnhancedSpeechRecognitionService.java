package echobridge.com.java_app.core.services;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.vosk.Model;
import org.vosk.Recognizer;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EnhancedSpeechRecognitionService implements SpeechRecognitionService {
    
    @Value("${vosk.model.path:src/main/resources/vosk-model-small-en-us-0.15}")
    private String voskModelPath;
    
    @Value("${whisper.api.url:http://localhost:9000}")
    private String whisperApiUrl;
    
    @Value("${speech.recognition.provider:whisper}")
    private String defaultProvider;
    
    public EnhancedSpeechRecognitionService() {
        // No RestTemplate needed for Python approach
    }
    
    @Override
    public CompletionStage<String> transcribe(short[] audioSamples) {
        return switch (defaultProvider.toLowerCase()) {
            case "whisper" -> transcribeWithWhisper(audioSamples);
            case "vosk" -> transcribeWithVosk(audioSamples);
            default -> transcribeWithWhisper(audioSamples);
        };
    }
    
    public CompletionStage<String> transcribeWithProvider(short[] audioSamples, String provider) {
        return switch (provider.toLowerCase()) {
            case "whisper" -> transcribeWithWhisper(audioSamples);
            case "vosk" -> transcribeWithVosk(audioSamples);
            default -> transcribeWithWhisper(audioSamples);
        };
    }
    
    private CompletionStage<String> transcribeWithVosk(short[] audioSamples) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                java.io.File modelFile = new java.io.File(voskModelPath);
                if (!modelFile.exists()) {
                    log.error("Vosk model not found at path: {}", voskModelPath);
                    return "";
                }
                
                Model model = new Model(voskModelPath);
                try (Recognizer recognizer = new Recognizer(model, 16000)) {
                    byte[] bytes = shortsToBytes(audioSamples);
                    
                    if (recognizer.acceptWaveForm(bytes, bytes.length)) {
                        String result = recognizer.getResult();
                        String text = extractText(result);
                        log.debug("Vosk final result: {}", text);
                        return text;
                    } else {
                        // Get partial result for real-time transcription
                        String partialResult = recognizer.getPartialResult();
                        String partialText = extractPartialText(partialResult);
                        log.debug("Vosk partial result: {}", partialText);
                        return partialText;
                    }
                }
            } catch (Exception e) {
                log.error("Vosk transcription error", e);
                return "";
            }
        });
    }
    
    private CompletionStage<String> transcribeWithWhisper(short[] audioSamples) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] audioBytes = shortsToBytes(audioSamples);
                String base64Audio = java.util.Base64.getEncoder().encodeToString(audioBytes);
                
                log.debug("Sending {} bytes to Python Whisper service", audioBytes.length);
                
                // Call Python Gradio service instead of direct HTTP
                return callPythonWhisperService(base64Audio);
                
            } catch (Exception e) {
                log.error("Whisper transcription error", e);
                return "";
            }
        });
    }
    
    private String callPythonWhisperService(String base64Audio) {
        try {
            // Write base64 audio to temporary file to avoid command line length limits
            java.io.File tempFile = java.io.File.createTempFile("whisper_audio_", ".wav");
            tempFile.deleteOnExit(); // Clean up automatically
            
            try {
                // Create proper WAV file from base64 audio data
                byte[] audioData = java.util.Base64.getDecoder().decode(base64Audio);
                byte[] wavFile = createWavFile(audioData);
                java.nio.file.Files.write(tempFile.toPath(), wavFile);
                
                // Build Python command with file path instead of base64 string
                ProcessBuilder pb = new ProcessBuilder(
                    "python", 
                    "whisper_gradio_service.py", 
                    "transcribe_from_file", 
                    tempFile.getAbsolutePath(),
                    "Systran/faster-whisper-small",
                    "transcribe"
                );
                
                pb.directory(new java.io.File("."));
                pb.redirectErrorStream(true);
                
                Process process = pb.start();
                
                // Read output
                StringBuilder output = new StringBuilder();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line);
                    }
                }
                
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    log.error("Python Whisper service exited with code: {}", exitCode);
                    log.error("Python output: {}", output.toString());
                    return "";
                }
                
                // Parse JSON response
                String result = output.toString();
                if (result.contains("\"success\":true")) {
                    // Extract text from JSON response
                    int textStart = result.indexOf("\"text\":\"") + 8;
                    int textEnd = result.indexOf("\"", textStart);
                    if (textEnd > textStart) {
                        String transcription = result.substring(textStart, textEnd);
                        log.debug("Python Whisper transcribed: '{}'", transcription);
                        return transcription;
                    }
                }
                
                log.error("Python Whisper service returned error: {}", result);
                return "";
                
            } finally {
                // Clean up temp file
                try {
                    java.nio.file.Files.deleteIfExists(tempFile.toPath());
                } catch (Exception e) {
                    log.debug("Could not delete temp file: {}", tempFile.getAbsolutePath());
                }
            }
            
        } catch (Exception e) {
            log.error("Error calling Python Whisper service", e);
            return "";
        }
    }
    
    private byte[] createWavFile(byte[] audioData) {
        // Create proper WAV file with header for 16kHz, 16-bit, mono audio
        int dataLength = audioData.length;
        int headerSize = 44; // Standard WAV header is 44 bytes
        int fileLength = headerSize + dataLength - 8; // -8 because RIFF chunk doesn't include RIFF itself
        
        java.nio.ByteBuffer wavBuffer = java.nio.ByteBuffer.allocate(headerSize + dataLength);
        wavBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        
        // WAV header (44 bytes total)
        wavBuffer.put("RIFF".getBytes());           // ChunkID (4 bytes)
        wavBuffer.putInt(fileLength);               // ChunkSize (4 bytes)
        wavBuffer.put("WAVE".getBytes());           // Format (4 bytes)
        wavBuffer.put("fmt ".getBytes());           // Subchunk1ID (4 bytes)
        wavBuffer.putInt(16);                       // Subchunk1Size (4 bytes)
        wavBuffer.putShort((short) 1);              // AudioFormat (2 bytes)
        wavBuffer.putShort((short) 1);              // NumChannels (2 bytes)
        wavBuffer.putInt(16000);                    // SampleRate (4 bytes)
        wavBuffer.putInt(32000);                     // ByteRate (4 bytes)
        wavBuffer.putShort((short) 2);              // BlockAlign (2 bytes)
        wavBuffer.putShort((short) 16);             // BitsPerSample (2 bytes)
        wavBuffer.put("data".getBytes());           // Subchunk2ID (4 bytes)
        wavBuffer.putInt(dataLength);                // Subchunk2Size (4 bytes)
        
        // Audio data
        wavBuffer.put(audioData);
        
        return wavBuffer.array();
    }
    
    private byte[] shortsToBytes(short[] shorts) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(shorts.length * 2);
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buffer.asShortBuffer().put(shorts);
        return buffer.array();
    }
    
    private String extractText(String json) {
        if (json == null || json.isEmpty()) return "";
        
        int start = json.indexOf("\"text\" : \"") + 10;
        if (start < 10) start = json.indexOf("\"text\":\"") + 8;
        
        if (start < 8) return "";
        
        int end = json.indexOf("\"", start);
        if (end == -1) return "";
        
        return json.substring(start, end).trim();
    }
    
    private String extractPartialText(String json) {
        if (json == null || json.isEmpty()) return "";
        
        // Extract partial text from Vosk partial result
        int start = json.indexOf("\"partial\" : \"") + 12;
        if (start < 12) start = json.indexOf("\"partial\":\"") + 10;
        
        if (start < 10) return "";
        
        int end = json.indexOf("\"", start);
        if (end == -1) return "";
        
        String partialText = json.substring(start, end).trim();
        
        // Filter out empty partial results and common noise
        if (partialText.isEmpty() || 
            partialText.equals("[unk]") || 
            partialText.equals("[spn]") ||
            partialText.length() < 2) {
            return "";
        }
        
        return partialText;
    }
}
