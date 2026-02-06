package echobridge.com.java_app.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import akka.actor.ActorSystem;
import akka.stream.Materializer;

@Configuration
public class AkkaConfig {

    @Bean(destroyMethod = "terminate")
    public ActorSystem actorSystem() {
        return ActorSystem.create("JavaAppSystem");
    }

    @Bean
    public Materializer materializer(ActorSystem actorSystem) {
        return Materializer.createMaterializer(actorSystem);
    }

    // Add this
    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }
}
