package echobridge.com.java_app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import echobridge.com.java_app.api.ProcessedTranscriptionWebSocketHandler;
import echobridge.com.java_app.api.WebSocketController;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketController webSocketController;
    private final ProcessedTranscriptionWebSocketHandler processedTranscriptionHandler;

    public WebSocketConfig(WebSocketController webSocketController, 
                          ProcessedTranscriptionWebSocketHandler processedTranscriptionHandler) {
        this.webSocketController = webSocketController;
        this.processedTranscriptionHandler = processedTranscriptionHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Raw audio input endpoint
        registry.addHandler(webSocketController, "/ws-raw")
                .setAllowedOrigins("*");
        
        // Processed transcription results endpoint
        registry.addHandler(processedTranscriptionHandler, "/ws-processed")
                .setAllowedOrigins("*");
    }
}
