package echobridge.com.java_app.configuration;

import akka.Done;
import akka.NotUsed;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import akka.stream.javadsl.SourceQueueWithComplete;
import akka.stream.javadsl.Keep;
import akka.japi.Pair;
import akka.stream.OverflowStrategy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

@Component
@AllArgsConstructor
public class Lesson2_MaterializedValues {
    
    private final Materializer materializer;

    @PostConstruct
    public void runMaterializedDemo() throws Exception {
        System.out.println(" === Lesson 3: Materialized Values === ");
        
        exercise1_count();
        exercise2_sum();
        exercise3_both();
        hybridApproach();
        exercise4_keepBoth();
    }
    
    // Exercise 1: Simple count
    private void exercise1_count() throws Exception {
        System.out.println("\n--- Exercise 3.1: Count Elements ---");
        
        Source<Integer, NotUsed> numbers = Source.range(1, 100);
        
        // Sink.fold returns a CompletionStage<T> with the folded result
        // fold(startValue, (accumulator, element) -> newAccumulator)
        Sink<Integer, CompletionStage<Long>> countSink = 
            Sink.fold(0L, (count, element) -> count + 1);
        
        // Run and get the materialized value (the count)
        CompletionStage<Long> countFuture = numbers.runWith(countSink, materializer);
        
        // Wait and print result
        Long totalCount = countFuture.toCompletableFuture().get();
        System.out.println("Total elements counted: " + totalCount);
    }
    


    // Exercise 2: Sum all elements
    private void exercise2_sum() throws Exception {
        System.out.println("\n--- Exercise 3.2: Sum Elements ---");
        
        Source<Integer, NotUsed> numbers = Source.range(1, 10); // 1+2+...+10 = 55
        
        Sink<Integer, CompletionStage<Integer>> sumSink = 
            Sink.fold(0, (sum, element) -> sum + element);
        
        CompletionStage<Integer> sumFuture = numbers.runWith(sumSink, materializer);
        
        Integer totalSum = sumFuture.toCompletableFuture().get();
        System.out.println("Sum of 1 to 10: " + totalSum);
    }



    
    // Exercise 3: Both count AND sum (Tuple)
    private void exercise3_both() throws Exception {
        System.out.println("\n--- Exercise 3: Count AND Sum ---");
        
        Source<Integer, NotUsed> numbers = Source.range(1, 5);
        
        // Create a custom accumulator class
        class Stats {
            long count = 0;
            long sum = 0;
        }
        
        Sink<Integer, CompletionStage<Stats>> statsSink = 
            Sink.fold(new Stats(), (stats, element) -> {
                stats.count++;
                stats.sum += element;
                return stats;
            });
        
        CompletionStage<Stats> statsFuture = numbers.runWith(statsSink, materializer);
        
        Stats result = statsFuture.toCompletableFuture().get();
        System.out.println("Count: " + result.count + ", Sum: " + result.sum + 
                          ", Average: " + (result.sum / (double) result.count));
    }

    // Use Flow to annotate, Sink to aggregate
    private void hybridApproach() throws Exception {
        System.out.println("\n--- Hybrid: Flow for transform, Sink for result ---");
        
        Source<Integer, NotUsed> numbers = Source.range(1, 10);
        
        // FLOW: Transform each number (doubles it)
        Flow<Integer, Integer, NotUsed> doubler = 
            Flow.of(Integer.class).map(n -> n * 2);
        
        // SINK: Count the transformed elements
        Sink<Integer, CompletionStage<Long>> counter = 
            Sink.fold(0L, (c, e) -> c + 1);
        
        // Chain: Source → Flow → Sink
        // Result is the SINK's materialized value (the count)
        CompletionStage<Long> result = numbers
            .via(doubler)   // Flow transforms
            .runWith(counter, materializer);

            System.out.println("Count of doubled numbers with Sink.seq(): " + 
            numbers.via(doubler).runWith(Sink.seq(), materializer).toCompletableFuture().get());

        System.out.println("Count of doubled numbers with completableFuture get(): " + 
            result.toCompletableFuture().get());
    }

    private void exercise4_keepBoth() throws Exception {
        System.out.println("\n--- Exercise 4: Keep Both Values ---");
        
        // Source.range has materialized value NotUsed (boring)
        // Let's use Source.maybe - it returns a CompletionStage that you can complete externally
        
        Source<Integer, CompletionStage<Done>> source = 
            Source.range(1, 3)
                .concat(Source.maybe())  // Keeps stream open until explicitly closed
                .mapMaterializedValue(notUsed -> 
                    CompletableFuture.completedFuture(Done.getInstance())
                );
        
        // Actually, let's use a simpler example: Source.queue
        Source<Integer, SourceQueueWithComplete<Integer>> queueSource = Source.queue(10, OverflowStrategy.backpressure());
        
        Sink<Integer, CompletionStage<Long>> countSink = Sink.fold(0L, (c, e) -> c + 1);
        
        // toMat allows choosing which materialized value to keep
        // Keep right (sink), left (source), or both (tuple)
        
        Pair<SourceQueueWithComplete<Integer>, CompletionStage<Long>> result = queueSource.toMat(countSink, Keep.both()).run(materializer);
        
        SourceQueueWithComplete<Integer> queue = result.first();
        CompletionStage<Long> countFuture = result.second();
        
        // Push elements manually
        queue.offer(10);
        queue.offer(20);
        queue.offer(30);
        queue.complete();  // Close the stream
        
        Long count = countFuture.toCompletableFuture().get();
        System.out.println("Elements counted from queue: " + count);
    }

    // cleanest approach to use Pairs with flows

    // Use scan - like fold but emits intermediate results
/* Flow<Integer, Stats, NotUsed> runningStats = 
    Flow.of(Integer.class)
        .scan(
            new Stats(0, 0),  // Initial value
            (stats, element) -> new Stats(
                stats.count() + 1, 
                stats.sum() + element
            )
        )
        .map(stats -> stats.sum() / (double) stats.count());  // To average */
}