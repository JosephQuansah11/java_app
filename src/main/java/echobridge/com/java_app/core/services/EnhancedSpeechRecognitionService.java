package echobridge.com.java_app.core.services;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

@Service
@Primary
@Slf4j
public class EnhancedSpeechRecognitionService implements SpeechRecognitionService {

    @Value("${whisper.api.url:http://localhost:9000}")
    private String whisperApiUrl;
    
    @Value("${whisper.api.key:}")
    private String whisperApiKey;
    
    @Value("${ghananlp.asr.url:https://translation-api.ghananlp.org/asr/v1/transcribe?language=ak}")
    private String ghananlpAsrUrl;
    
    @Value("${ghananlp.api.key:}")
    private String ghananlpApiKey;

    @Value("${speech.recognition.provider:whisper}")
    private String defaultProvider;

    private final RestTemplate restTemplate;

    public EnhancedSpeechRecognitionService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public CompletionStage<String> transcribe(short[] audioSamples) {
        // Fast-path: directly use whisper for real-time transcription
        // TODO: Add language detection to route Twi/Akan to Ghana NLP ASR
        return transcribeWithGhanaNLP(audioSamples, "ak");
    }
    
    private CompletionStage<String> transcribeWithGhanaNLP(short[] audioSamples, String language) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Create multipart form data for audio file
                byte[] wavFile = createWavFile(audioSamples);
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                headers.set("Ocp-Apim-Subscription-Key", ghananlpApiKey);
                
                // Create multipart request
                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                body.add("audio", new ByteArrayResource(wavFile) {
                    @Override
                    public String getFilename() {
                        return "audio.wav";
                    }
                });
                body.add("language", language);
                
                HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
                
                ResponseEntity<Map> response = restTemplate.postForEntity(ghananlpAsrUrl, entity, Map.class);
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    String transcription = (String) response.getBody().get("transcription");
                    if (transcription != null && !transcription.trim().isEmpty()) {
                        log.info("Ghana NLP ASR successful: {}", transcription);
                        return transcription;
                    }
                }
                
            } catch (Exception e) {
                log.warn("Ghana NLP ASR failed: {}", e.getMessage());
            }
            
            // Fallback to Whisper
            return transcribeWithWhisperOptimized(audioSamples).toCompletableFuture().join();
        });
    }

    private CompletionStage<String> transcribeWithWhisperOptimized(short[] audioSamples) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // OPTIMIZED: Process larger chunks more efficiently
                if (audioSamples.length < 16000) {
                    log.warn("Audio chunk too small: {} samples (minimum 16000)", audioSamples.length);
                    return "";
                }

                // Create WAV file in memory (no file I/O)
                byte[] wavFile = createWavFile(audioSamples);

                // Use multipart form data as required by OpenAI Whisper API
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);

                // Add API key if configured
                if (whisperApiKey != null && !whisperApiKey.isEmpty()) {
                    headers.set("Authorization", "Bearer " + whisperApiKey);
                }

                // Create multipart body with file and parameters
                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

                body.add("file", new ByteArrayResource(wavFile) {
                    @Override
                    public String getFilename() {
                        return "audio.wav";
                    }
                });
                body.add("model", "whisper-1");
                body.add("language", "en");
                body.add("response_format", "json");

                HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

                long startTime = System.nanoTime();

                // Get raw response as String to debug format
                ResponseEntity<String> rawResponse = restTemplate.postForEntity(
                        whisperApiUrl, entity, String.class);
                long endTime = System.nanoTime();

                log.info("RAW WHISPER RESPONSE ({}ms): {}",
                        (endTime - startTime) / 1_000_000.0, rawResponse.getBody());

                if (!rawResponse.getStatusCode().is2xxSuccessful() || rawResponse.getBody() == null) {
                    System.err.println("Whisper API failed with status: " + rawResponse.getStatusCode());
                    return "";
                }

                String responseBody = rawResponse.getBody().trim();
                String transcription = extractTranscription(responseBody);

                double durationMs = (endTime - startTime) / 1_000_000.0;
                System.out.printf("WHISPER (%.2fms): '%s'%n", durationMs, transcription);

                return transcription;

            } catch (Exception e) {
                System.err.println("Whisper API error: " + e.getMessage());
                e.printStackTrace();
                return "";
            }
        });
    }

    /**
     * Extracts transcription from various response formats.
     * Returns empty string if parsing fails.
     */
    private String extractTranscription(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return "";
        }

        try {
            // Try OpenAI format: {"text": "..."}
            if (responseBody.contains("\"text\"")) {
                int textStart = responseBody.indexOf("\"text\":\"") + 8;
                if (textStart > 7) { // Check if found
                    int textEnd = responseBody.indexOf("\"", textStart);
                    if (textEnd > textStart) {
                        return responseBody.substring(textStart, textEnd)
                                .replace("\\n", " ")
                                .replace("\\\"", "\"")
                                .trim();
                    }
                }
            }

            // Try alternative format: {"transcription": "..."}
            if (responseBody.contains("\"transcription\"")) {
                int textStart = responseBody.indexOf("\"transcription\":\"") + 16;
                if (textStart > 15) {
                    int textEnd = responseBody.indexOf("\"", textStart);
                    if (textEnd > textStart) {
                        return responseBody.substring(textStart, textEnd)
                                .replace("\\n", " ")
                                .replace("\\\"", "\"")
                                .trim();
                    }
                }
            }

            // Plain text response (not JSON)
            if (!responseBody.startsWith("{") && !responseBody.startsWith("[")) {
                return responseBody.trim();
            }

            // Try using Jackson ObjectMapper as fallback (recommended)
            // JsonNode root = objectMapper.readTree(responseBody);
            // if (root.has("text")) return root.get("text").asText();
            // if (root.has("transcription")) return root.get("transcription").asText();

        } catch (Exception e) {
            System.err.println("Failed to parse transcription: " + e.getMessage());
        }

        System.err.println("Could not parse transcription from response: " + responseBody);
        return "";
    }

    private byte[] shortsToBytesFast(short[] shorts) {
        byte[] bytes = new byte[shorts.length * 2];
        for (int i = 0; i < shorts.length; i++) {
            bytes[i * 2] = (byte) (shorts[i] & 0xFF);
            bytes[i * 2 + 1] = (byte) ((shorts[i] >> 8) & 0xFF);
        }
        return bytes;
    }

    private byte[] createWavFile(short[] audioSamples) {
        // Convert short array to byte array (PCM data)
        byte[] pcmData = shortsToBytesFast(audioSamples);

        // WAV header constants
        int sampleRate = 16000;
        short channels = 1;
        short bitsPerSample = 16;
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        short blockAlign = (short) (channels * bitsPerSample / 8);

        int headerSize = 44;
        int dataSize = pcmData.length;
        int fileSize = headerSize + dataSize;

        byte[] wavFile = new byte[fileSize];

        // RIFF chunk
        wavFile[0] = 'R';
        wavFile[1] = 'I';
        wavFile[2] = 'F';
        wavFile[3] = 'F';
        wavFile[4] = (byte) (fileSize - 8);
        wavFile[5] = (byte) ((fileSize - 8) >> 8);
        wavFile[6] = (byte) ((fileSize - 8) >> 16);
        wavFile[7] = (byte) ((fileSize - 8) >> 24);
        wavFile[8] = 'W';
        wavFile[9] = 'A';
        wavFile[10] = 'V';
        wavFile[11] = 'E';

        // fmt chunk
        wavFile[12] = 'f';
        wavFile[13] = 'm';
        wavFile[14] = 't';
        wavFile[15] = ' ';
        wavFile[16] = 16;
        wavFile[17] = 0;
        wavFile[18] = 0;
        wavFile[19] = 0; // Subchunk1Size
        wavFile[20] = 1;
        wavFile[21] = 0; // AudioFormat (PCM)
        wavFile[22] = (byte) channels;
        wavFile[23] = 0; // NumChannels
        wavFile[24] = (byte) sampleRate;
        wavFile[25] = (byte) (sampleRate >> 8);
        wavFile[26] = (byte) (sampleRate >> 16);
        wavFile[27] = (byte) (sampleRate >> 24);
        wavFile[28] = (byte) byteRate;
        wavFile[29] = (byte) (byteRate >> 8);
        wavFile[30] = (byte) (byteRate >> 16);
        wavFile[31] = (byte) (byteRate >> 24);
        wavFile[32] = (byte) blockAlign;
        wavFile[33] = 0; // BlockAlign
        wavFile[34] = (byte) bitsPerSample;
        wavFile[35] = 0; // BitsPerSample

        // data chunk
        wavFile[36] = 'd';
        wavFile[37] = 'a';
        wavFile[38] = 't';
        wavFile[39] = 'a';
        wavFile[40] = (byte) dataSize;
        wavFile[41] = (byte) (dataSize >> 8);
        wavFile[42] = (byte) (dataSize >> 16);
        wavFile[43] = (byte) (dataSize >> 24);

        // Copy PCM data
        System.arraycopy(pcmData, 0, wavFile, 44, pcmData.length);

        return wavFile;
    }

    public record WhisperRequest(
            String audio,
            String format,
            int sampleRate,
            String language) {
    }

    public record WhisperResponse(
            String text,
            double confidence,
            String language) {
    }

}
