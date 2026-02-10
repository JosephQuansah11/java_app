package echobridge.com.java_app.akkasharding;

import java.time.LocalDateTime;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;
import akka.persistence.typed.javadsl.ReplyEffect;
import echobridge.com.java_app.akkasharding.speech_processing.CborSerializable;
import echobridge.com.java_app.akkasharding.speech_processing.GetProcessingHistory;
import echobridge.com.java_app.akkasharding.speech_processing.ProcessingFailed;
import echobridge.com.java_app.akkasharding.speech_processing.ProcessingHistory;
import echobridge.com.java_app.akkasharding.speech_processing.SpeechProcessed;
import echobridge.com.java_app.akkasharding.speech_processing.SpeechProcessingCommand;
import echobridge.com.java_app.akkasharding.speech_processing.SpeechProcessingEvent;
import echobridge.com.java_app.akkasharding.speech_processing.SpeechProcessingResponse;
import echobridge.com.java_app.akkasharding.speech_processing.SpeechProcessingState;
import echobridge.com.java_app.akkasharding.speech_processing.SpeechReply;
import lombok.extern.slf4j.Slf4j;

// public interface CborSerializable {}

// @Data
// @AllArgsConstructor
// class SpeechProcessingCommand implements CborSerializable {
//     String sessionId;
//     String audioData;
//     String targetLanguage;
//     ActorRef<SpeechProcessingResponse> replyTo;
// }

// @Data
// @AllArgsConstructor
// class GetProcessingHistory implements CborSerializable {
//     String sessionId;
//     ActorRef<ProcessingHistory> replyTo;
// }

// @Data
// @AllArgsConstructor
// class SpeechProcessingResponse implements CborSerializable {
//     String originalText;
//     String translatedText;
//     String audioOutput;
//     LocalDateTime timestamp;
//     boolean success;
//     String errorMessage;
// }

// @Data
// @AllArgsConstructor
// class ProcessingHistory implements CborSerializable {
//     String sessionId;
//     List<SpeechProcessingResponse> history;
// }

// sealed interface SpeechProcessingEvent permits SpeechProcessed, ProcessingFailed {}

// @Data
// @AllArgsConstructor
// final class SpeechProcessed implements SpeechProcessingEvent {
//     String originalText;
//     String translatedText;
//     String audioOutput;
//     LocalDateTime timestamp;
// }

// @Data
// @AllArgsConstructor
// final class ProcessingFailed implements SpeechProcessingEvent {
//     String errorMessage;
//     LocalDateTime timestamp;
// }

// @Data
// class SpeechProcessingState implements CborSerializable {
//     final String sessionId;
//     private final List<SpeechProcessingResponse> processingHistory;

//     public SpeechProcessingState(String sessionId) {
//         this.sessionId = sessionId;
//         this.processingHistory = new ArrayList<>();
//     }

//     public SpeechProcessingState(String sessionId, List<SpeechProcessingResponse> processingHistory) {
//         this.sessionId = sessionId;
//         this.processingHistory = new ArrayList<>(processingHistory);
//     }

//     public SpeechProcessingState addResponse(SpeechProcessingResponse response) {
//         List<SpeechProcessingResponse> newHistory = new ArrayList<>(processingHistory);
//         newHistory.add(response);
//         return new SpeechProcessingState(sessionId, newHistory);
//     }

//     public List<SpeechProcessingResponse> getHistory() {
//         return new ArrayList<>(processingHistory);
//     }
// }
@Slf4j
public class SpeechProcessingEntity
        extends EventSourcedBehavior<CborSerializable, SpeechProcessingEvent, SpeechProcessingState> {

    public static final EntityTypeKey<CborSerializable> ENTITY_TYPE_KEY = EntityTypeKey
            .create(CborSerializable.class, "SpeechProcessingEntity");

    private final String entityId;

    private SpeechProcessingEntity(String entityId) {
        super(PersistenceId.of(ENTITY_TYPE_KEY.name(), entityId));
        this.entityId = entityId;
    }

    public static Behavior<CborSerializable> create(String entityId) {
        return Behaviors.setup(context -> new SpeechProcessingEntity(entityId));
    }

    @Override
    public SpeechProcessingState emptyState() {
        return new SpeechProcessingState(entityId);
    }

    @Override
    public CommandHandler<CborSerializable, SpeechProcessingEvent, SpeechProcessingState> commandHandler() {
        return newCommandHandlerBuilder()
                .forAnyState()
                .onCommand(SpeechProcessingCommand.class, this::processSpeech)
                .onCommand(GetProcessingHistory.class, this::getHistory)
                .build();
    }

    @Override
    public EventHandler<SpeechProcessingState, SpeechProcessingEvent> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(SpeechProcessed.class, (state, event) -> {
                    SpeechProcessingResponse response = new SpeechProcessingResponse(
                            event.getOriginalText(),
                            event.getTranslatedText(),
                            event.getAudioOutput(),
                            event.getTimestamp(),
                            true,
                            null);
                    return state.addResponse(response);
                })
                .onEvent(ProcessingFailed.class, (state, event) -> {
                    SpeechProcessingResponse response = new SpeechProcessingResponse(
                            "",
                            "",
                            "",
                            event.getTimestamp(),
                            false,
                            event.getErrorMessage());
                    return state.addResponse(response);
                })
                .build();
    }

    private ReplyEffect<SpeechProcessingEvent, SpeechProcessingState> processSpeech(
            SpeechProcessingState state, SpeechProcessingCommand command) {

        try {
            String originalText = extractTextFromAudio(command.getAudioData());
            String translatedText = translateText(originalText, command.getTargetLanguage());
            String audioOutput = generateSpeechFromText(translatedText);

            SpeechProcessed event = new SpeechProcessed(
                    originalText, translatedText, audioOutput, LocalDateTime.now());

            SpeechProcessingResponse response = new SpeechProcessingResponse(
                    originalText, translatedText, audioOutput, LocalDateTime.now(), true, null);

            return Effect()
                    .persist(event)
                    .thenReply(command.getReplyTo(), updatedState -> SpeechReply.fromResponse(response));

        } catch (Exception e) {
            ProcessingFailed event = new ProcessingFailed(e.getMessage(), LocalDateTime.now());
            SpeechProcessingResponse response = new SpeechProcessingResponse(
                    "", "", "", LocalDateTime.now(), false, e.getMessage());

            return Effect()
                    .persist(event)
                    .thenReply(command.getReplyTo(), updatedState -> SpeechReply.fromResponse(response));
        }
    }

    private ReplyEffect<SpeechProcessingEvent, SpeechProcessingState> 
    getHistory(SpeechProcessingState state, GetProcessingHistory command) {

        ProcessingHistory history = new ProcessingHistory(
                state.getSessionId(),
                state.getHistory());

        return Effect().reply(command.getReplyTo(), SpeechReply.fromHistory(history));
    }

    private String extractTextFromAudio(String audioData) {
        return "Extracted text from audio";
    }

    private String translateText(String text, String targetLanguage) {
        return "Translated: " + text + " -> " + targetLanguage;
    }

    private String generateSpeechFromText(String text) {
        return "Generated audio from: " + text;
    }
}
