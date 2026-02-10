package echobridge.com.java_app.akkasharding.speech_processing;

public sealed interface SpeechProcessingEvent extends CborSerializable permits SpeechProcessed, ProcessingFailed {}