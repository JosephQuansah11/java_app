package echobridge.com.java_app.domain.source;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

import org.springframework.stereotype.Component;

import akka.NotUsed;
import akka.japi.Pair;
import akka.stream.javadsl.Source;
import echobridge.com.java_app.domain.data_structure.AudioChunk;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
@Component
public class MicrophoneSource {

    // Standard 16kHz, 16-bit, mono - optimal for most speech recognition
    public static final float SAMPLE_RATE = 16000;
    public static final int SAMPLE_SIZE_BITS = 16;
    public static final int CHANNELS = 1;
    public static final boolean SIGNED = true;
    public static final boolean BIG_ENDIAN = false;

    // 1-second chunks for processing
    public static final int CHUNK_DURATION_MS = 2000;
    public static final int CHUNK_SIZE = (int) (SAMPLE_RATE * CHUNK_DURATION_MS / 1000);

    private final TargetDataLine microphone;
    private final AudioFormat format;
    private final AudioCaptureState audioCaptureState;
    


    public MicrophoneSource() throws LineUnavailableException {
        this.format = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE_BITS, CHANNELS, SIGNED, BIG_ENDIAN);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        this.microphone = (TargetDataLine) AudioSystem.getLine(info);
        this.microphone.open(format);
        this.audioCaptureState = new AudioCaptureState(microphone, format, CHUNK_SIZE);
    }



    public Source<AudioChunk, NotUsed> createSource() {
        return Source.unfoldAsync(this.audioCaptureState,
                state -> captureNextChunk(state))
                .map(samples -> new AudioChunk(samples, (int) SAMPLE_RATE))
                .mapMaterializedValue(ignored -> NotUsed.getInstance());
    }



    private CompletableFuture<Optional<Pair<AudioCaptureState, short[]>>> captureNextChunk(AudioCaptureState state) {
        return CompletableFuture.supplyAsync(() -> {
            try {

                if (state.isOpen()) {
                    log.info("Opening microphone........");
                    state.open();
                    state.start();
                }

                byte[] buffer = new byte[state.getBytesPerChunk()];
                int bytesRead = state.read(buffer);

                if (bytesRead <= 0) {
                    log.info("No audio recorded Closing microphone........");
                    state.close();
                    return Optional.<Pair<AudioCaptureState, short[]>>empty();
                }

                short[] samples = bytesToShorts(buffer, bytesRead);
                return Optional.of(Pair.create(state, samples));

            } catch (LineUnavailableException e) {
                log.error("Error capturing audio", e);
                state.close();
                return Optional.empty();
            }
        });
    }


    
    private short[] bytesToShorts(byte[] bytes, int length) {
        ShortBuffer shortBuffer = ByteBuffer.wrap(bytes, 0, length)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer();
        short[] samples = new short[shortBuffer.remaining()];
        shortBuffer.get(samples);
        return samples;
    }

}
