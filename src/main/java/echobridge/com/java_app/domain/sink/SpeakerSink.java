package echobridge.com.java_app.domain.sink;

import org.springframework.stereotype.Component;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import akka.Done;
import akka.NotUsed;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import echobridge.com.java_app.domain.data_structure.AudioOutput;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class SpeakerSink {
    
    private final AudioFormat format;
    
    public SpeakerSink() {
        this.format = new AudioFormat(22050, 16, 1, true, false); // Piper outputs 22050Hz
    }
    
    public Sink<AudioOutput, CompletionStage<Done>> createSink() {
        return Sink.<AudioOutput>foreach(audio -> {
            if (audio.pcmData().length == 0) return;
            
            try {
                playAudio(audio.pcmData());
            } catch (Exception e) {
                log.error("Audio playback failed", e);
            }
        }).mapMaterializedValue(fut -> fut.thenApply(d -> Done.done()));
    }
    
    private void playAudio(byte[] pcmData) throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
        
        line.open(format);
        line.start();
        line.write(pcmData, 0, pcmData.length);
        line.drain();
        line.close();
    }
    
    // Alternative: Non-blocking playback with queue
    public Sink<AudioOutput, NotUsed> createAsyncSink() {
        // Queue audio chunks and play sequentially without blocking stream
        return Flow.of(AudioOutput.class)
            .mapAsync(1, audio -> CompletableFuture.runAsync(() -> {
                try {
                    playAudio(audio.pcmData());
                } catch (Exception e) {
                    log.error("Playback error", e);
                }
            }))
            .to(Sink.ignore());
    }
}
