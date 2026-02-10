package echobridge.com.java_app.akkasharding.speech_processing;

import akka.actor.typed.ActorRef;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GetProcessingHistory implements CborSerializable {
    String sessionId;
    ActorRef<SpeechReply> replyTo;
}
