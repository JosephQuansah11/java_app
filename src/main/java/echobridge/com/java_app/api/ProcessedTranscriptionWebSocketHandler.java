package echobridge.com.java_app.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ProcessedTranscriptionWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public ProcessedTranscriptionWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        log.info("📡 Processed transcription WebSocket connection established: {}", session.getId());
        
        // Send initial connection message
        sendMessage(session, Map.of(
            "type", "connection",
            "status", "connected",
            "sessionId", session.getId(),
            "endpoint", "processed-transcription"
        ));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        log.info("📡 Processed transcription WebSocket connection CLOSED: {} - Status: {}", 
            session.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // This endpoint is primarily for sending processed results to frontend
        // But we can handle control messages if needed
        try {
            String payloadStr = message.getPayload();
            Map<String, Object> payload = objectMapper.readValue(payloadStr, Map.class);
            String type = (String) payload.get("type");
            
            log.debug("📡 Received message on processed endpoint: {} from {}", type, session.getId());
            
            switch (type) {
                case "ping":
                    sendMessage(session, Map.of("type", "pong"));
                    break;
                case "subscribe":
                    // Handle subscription to specific session results
                    String targetSessionId = (String) payload.get("sessionId");
                    sendMessage(session, Map.of(
                        "type", "subscribed",
                        "targetSessionId", targetSessionId
                    ));
                    break;
                default:
                    log.warn("Unknown message type on processed endpoint: {}", type);
            }
        } catch (Exception e) {
            log.error("Error handling message on processed endpoint", e);
            sendMessage(session, Map.of(
                "type", "error",
                "message", "Failed to process message: " + e.getMessage()
            ));
        }
    }

    /**
     * Send processed transcription result to a specific session
     */
    public void sendTranscriptionResult(String sessionId, Map<String, Object> result) {
        // Find the session that corresponds to this transcription session
        WebSocketSession targetSession = findSessionByTranscriptionId(sessionId);
        if (targetSession != null && targetSession.isOpen()) {
            sendMessage(targetSession, Map.of(
                "type", "transcription_result",
                "sessionId", sessionId,
                "timestamp", System.currentTimeMillis(),
                "data", result
            ));
        } else {
            log.debug("No active WebSocket session found for transcription results: {}", sessionId);
        }
    }

    /**
     * Broadcast transcription result to all connected sessions
     */
    public void broadcastTranscriptionResult(Map<String, Object> result) {
        log.info("📡 Broadcasting transcription result to {} sessions", sessions.size());
        
        sessions.values().parallelStream().forEach(session -> {
            if (session.isOpen()) {
                sendMessage(session, Map.of(
                    "type", "transcription_result",
                    "timestamp", System.currentTimeMillis(),
                    "data", result
                ));
            }
        });
    }

    /**
     * Send real-time partial transcription updates
     */
    public void sendPartialTranscription(String sessionId, String partialText, double confidence) {
        WebSocketSession targetSession = findSessionByTranscriptionId(sessionId);
        if (targetSession != null && targetSession.isOpen()) {
            sendMessage(targetSession, Map.of(
                "type", "partial_transcription",
                "sessionId", sessionId,
                "timestamp", System.currentTimeMillis(),
                "partialText", partialText,
                "confidence", confidence
            ));
        }
    }

    /**
     * Send final transcription with translation
     */
    public void sendFinalTranscription(String sessionId, String finalText, String translatedText, double confidence) {
        WebSocketSession targetSession = findSessionByTranscriptionId(sessionId);
        if (targetSession != null && targetSession.isOpen()) {
            sendMessage(targetSession, Map.of(
                "type", "final_transcription",
                "sessionId", sessionId,
                "timestamp", System.currentTimeMillis(),
                "finalText", finalText,
                "translatedText", translatedText,
                "confidence", confidence
            ));
        }
    }

    /**
     * Send processing status updates
     */
    public void sendProcessingStatus(String sessionId, String status, String message) {
        WebSocketSession targetSession = findSessionByTranscriptionId(sessionId);
        if (targetSession != null && targetSession.isOpen()) {
            sendMessage(targetSession, Map.of(
                "type", "processing_status",
                "sessionId", sessionId,
                "timestamp", System.currentTimeMillis(),
                "status", status,
                "message", message
            ));
        }
    }

    private WebSocketSession findSessionByTranscriptionId(String transcriptionSessionId) {
        // For now, we'll use a simple approach - find the first session
        // In a more sophisticated implementation, we might maintain a mapping
        // between transcription sessions and WebSocket sessions
        return sessions.values().stream()
                .filter(WebSocketSession::isOpen)
                .findFirst()
                .orElse(null);
    }

    private void sendMessage(WebSocketSession session, Map<String, Object> message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            log.debug("📡 SENDING processed result to {}: {}", session.getId(), jsonMessage);
            session.sendMessage(new TextMessage(jsonMessage));
        } catch (Exception e) {
            log.error("Error sending processed transcription message", e);
        }
    }

    public int getActiveSessionCount() {
        return (int) sessions.values().stream().filter(WebSocketSession::isOpen).count();
    }
}
