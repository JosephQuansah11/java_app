package echobridge.com.java_app.configuration;
import org.springframework.stereotype.Component;

import akka.actor.ActorSystem;
import akka.stream.Materializer;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.Sink;
import akka.NotUsed;
import akka.Done;
import java.util.concurrent.CompletionStage;
import akka.stream.javadsl.Flow;
import java.util.List;


import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class Lesson1_Basics {
    private final ActorSystem actorSystem;
    private final Materializer materializer;

    // Testing a basic stream with source and sink
    // @PostConstruct
    // public void runFirstStream(){
    //     System.out.println(" === Lesson 1: Basic Stream === ");

    //     Source<Integer, NotUsed> source = Source.range(1, 3);

    //     Sink<Integer, CompletionStage<Done>> sink = Sink.foreach(
    //         num -> System.out.println("Received: " + num)
    //     );

    //     CompletionStage<Done> result = source.runWith(sink, materializer);

    //     result.toCompletableFuture().join();

    //     System.out.println("Stream completed!\n ");
    // }


    // // Testing a flow integrated to source and sink
    // @PostConstruct
    // public void runSecondStream(){
    //     System.out.println(" === Lesson 2: Adding a flow === ");

    //     Source<Integer, NotUsed> source = Source.range(1, 3);

    //     Flow<Integer, Integer, NotUsed> doubler = Flow.of(Integer.class).map(value -> {
    //         System.out.println(" Doubling the values: " + value);
    //         return value * 2;
    //     });

    //     Sink<Integer, CompletionStage<Done>> sink = Sink.foreach(number -> {
    //         System.out.println("Received: " + number);
    //     });

    //     source.via(doubler).runWith(sink, materializer);

    //     try {
    //         Thread.sleep(1000);
    //     } catch (InterruptedException e) {}

    //     System.out.println("Stream completed!\n ");
    // }




    // // Testing multiple flows integrated to source and sink
    // @PostConstruct
    // public void runThirdStreamWithChainedFlows(){
    //     System.out.println(" === Lesson 2.1: Chained flows === ");

    //     Source<Integer, NotUsed> numbers =  Source.range(1, 100);

    //     Flow<Integer, Integer, NotUsed> filterEvenNumbers = Flow.of(Integer.class).filter(number -> number % 2 == 0);

    //     Flow<Integer, Integer, NotUsed> multiplyBy10 = Flow.of(Integer.class).map(number -> number * 10);

    //     Flow<Integer, String, NotUsed> convertToStringWithPrefix = Flow.of(Integer.class).map(number -> {
    //         System.out.println("Converting to string: " + number);
    //         return "Number: " + number;
    //     });

    //     Sink<String, CompletionStage<Done>> sink = Sink.foreach(number -> System.out.println("Received: " + number));

    //     numbers.via(filterEvenNumbers).via(multiplyBy10).via(convertToStringWithPrefix).runWith(sink, materializer);
    // }





    // Testing a splitted stream, example audio with frequency
    // @PostConstruct
    // public void runFourthStreamWithSplit(){
    //     System.out.println(" === Lesson 2.2: Splitting a stream === ");

    //     Source<Integer, NotUsed> source = Source.range(1, 5);

    //     Flow<Integer, String, NotUsed> squarePathA = Flow.of(Integer.class).map(value -> {
    //         return "Square of "+ value + " = " + (value * value);
    //     });

    //     Flow<Integer, String, NotUsed> cubePathB = Flow.of(Integer.class).map(value->{
    //         return "Cube of "+ value + " = " + (value * value * value);
    //     });

    //     Sink<String, CompletionStage<Done>> sink = Sink.foreach(number -> System.out.println("Received: " + number));

    //     source.mapConcat(n-> List.of("Processing "+ n, "Square of "+ n + " = " + (n * n), "Cube of "+ n + " = " + (n * n * n)))
    //     .runWith(sink, materializer);
        
    //     try {
    //         Thread.sleep(1000);
    //     } catch (InterruptedException e) {}

    //     System.out.println("Stream completed!\n ");
    // }
}