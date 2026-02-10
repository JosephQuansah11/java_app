package echobridge.com.java_app.akkasharding.speech_processing;

import akka.actor.typed.ActorRef;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SpeechProcessingCommand implements CborSerializable {
    String sessionId;
    String audioData;
    String targetLanguage;
    ActorRef<SpeechReply> replyTo;
}
