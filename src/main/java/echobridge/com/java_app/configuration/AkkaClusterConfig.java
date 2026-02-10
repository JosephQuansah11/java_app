package echobridge.com.java_app.configuration;

import org.springframework.context.annotation.Configuration;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Configuration
public class AkkaClusterConfig {

    private ActorSystem<Void> system;

    @PostConstruct
    public void init() {
        Config config = ConfigFactory.parseString("""
            akka {
              actor {
                provider = "cluster"
                serialization-bindings {
                  "echobridge.com.java_app.akkasharding.speech_processing.CborSerializable" = jackson-cbor
                }
              }
              
              remote {
                artery {
                  canonical.hostname = "127.0.0.1"
                  canonical.port = 25520
                }
              }
              
              cluster {
                seed-nodes = ["akka://EchoBridgeCluster@127.0.0.1:25520"]
                downing-provider-class = "akka.cluster.sbr.SplitBrainResolverProvider"
                split-brain-resolver {
                  active-strategy = "keep-majority"
                }
              }
              
              persistence {
                journal.plugin = "akka.persistence.journal.jdbc"
                snapshot-store.plugin = "akka.persistence.snapshot-store.jdbc"
                jdbc {
                  connection-url = "jdbc:h2:mem:echo-bridge;DB_CLOSE_DELAY=-1"
                  driver-class-name = "org.h2.Driver"
                  username = "sa"
                  password = ""
                }
              }
            }
            """);

        system = ActorSystem.create(EchoBridgeClusterGuardian.create(), "EchoBridgeCluster", config);
    }

    @PreDestroy
    public void shutdown() {
        if (system != null) {
            system.terminate();
        }
    }

    public ActorSystem<Void> getSystem() {
        return system;
    }

    static class EchoBridgeClusterGuardian {
        static Behavior<Void> create() {
            return Behaviors.setup(context -> {
                // Simple actor system setup without clustering for now
                return Behaviors.receive(Void.class).build();
            });
        }
    }
}
