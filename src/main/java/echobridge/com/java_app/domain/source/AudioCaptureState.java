package echobridge.com.java_app.domain.source;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

public class AudioCaptureState {
    private final TargetDataLine microphone;
    private final int bytesPerChunk;

    public AudioCaptureState(TargetDataLine microphone, AudioFormat format, int chunkSize){
        this.microphone = microphone;
        this.bytesPerChunk = chunkSize * 2; // 2 bytes per sample for 16-bit audio
    }

    public boolean isOpen(){
        return this.microphone.isOpen();
    }

    public void open() throws LineUnavailableException{
        this.microphone.open(this.microphone.getFormat(), bytesPerChunk * 2);
    }

    public void start() {
        this.microphone.start();
    }

    public void close() {
        try (this.microphone) {
            this.microphone.stop();
        }
    }

    public int read(byte[] buffer) {
        return this.microphone.read(buffer, 0, buffer.length);
    }
    
    public int getBytesPerChunk() {
        return bytesPerChunk;
    }
    
}