import com.hazelcast.jet.core.DAG;
import com.hazelcast.jet.core.SlidingWindowPolicy;
import com.hazelcast.jet.core.TimestampKind;
import com.hazelcast.jet.core.Vertex;
import com.hazelcast.jet.core.processor.Processors;
import com.hazelcast.jet.core.processor.SinkProcessors;
import com.hazelcast.jet.core.processor.SourceProcessors;
import com.hazelcast.jet.datamodel.TimestampedEntry;
import com.hazelcast.jet.function.DistributedFunction;
import com.hazelcast.jet.function.DistributedToLongFunction;
import com.hazelcast.jet.pipeline.ContextFactory;
import com.hazelcast.jet.pipeline.JournalInitialPosition;
import com.hazelcast.map.journal.EventJournalMapEvent;
import datamodel.Trade;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static com.hazelcast.jet.aggregate.AggregateOperations.counting;
import static com.hazelcast.jet.core.Edge.between;
import static com.hazelcast.jet.core.Partitioner.HASH_CODE;
import static com.hazelcast.jet.core.SlidingWindowPolicy.slidingWinPolicy;
import static com.hazelcast.jet.core.WatermarkEmissionPolicy.emitByFrame;
import static com.hazelcast.jet.core.WatermarkGenerationParams.wmGenParams;
import static com.hazelcast.jet.core.WatermarkPolicies.limitingLag;
import static com.hazelcast.jet.core.processor.Processors.mapUsingContextP;
import static com.hazelcast.jet.function.DistributedFunctions.entryKey;
import static com.hazelcast.jet.function.DistributedPredicate.alwaysTrue;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;

public class StockExchangeCoreApi {

    private static final String TRADES_MAP_NAME = "trades";
    private static final String OUTPUT_DIR_NAME = "stock-exchange";
    private static final int SLIDING_WINDOW_LENGTH_MILLIS = 1000;
    private static final int SLIDE_STEP_MILLIS = 10;
    private static final int TRADES_PER_SECOND = 4_000;
    private static final int JOB_DURATION = 10;

    static DAG buildDag() {
//tag::s1[]
        DistributedToLongFunction<? super Trade> timestampFn = Trade::timestamp;
        DistributedFunction<? super Trade, ?> keyFn = Trade::productId;
        SlidingWindowPolicy winPolicy = slidingWinPolicy(
                SLIDING_WINDOW_LENGTH_MILLIS, SLIDE_STEP_MILLIS);

        DAG dag = new DAG();
        Vertex tradeSource = dag.newVertex("trade-source",
                SourceProcessors.<Trade, Long, Trade>streamMapP(
                        TRADES_MAP_NAME,
                        alwaysTrue(),                              // <1>
                        EventJournalMapEvent::getNewValue,         // <1>
                        JournalInitialPosition.START_FROM_OLDEST,  // <2>
                        wmGenParams(
                                timestampFn,                       // <3>
                                limitingLag(SECONDS.toMillis(3)),  // <4>
                                emitByFrame(winPolicy),            // <5>
                                SECONDS.toMillis(3)                // <6>
                        )));
        Vertex slidingStage1 = dag.newVertex("sliding-stage-1",
                Processors.accumulateByFrameP(
                        singletonList(keyFn),
                        singletonList(timestampFn),
                        TimestampKind.EVENT,
                        winPolicy, counting()
                ));
        Vertex slidingStage2 = dag.newVertex("sliding-stage-2",
            Processors.combineToSlidingWindowP(winPolicy, counting(),
                    TimestampedEntry::fromWindowResult));
        Vertex formatOutput = dag.newVertex("format-output", mapUsingContextP(    // <7>
            ContextFactory.withCreateFn(x -> DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
            (DateTimeFormatter timeFormat, TimestampedEntry<String, Long> tse) ->
                String.format("%s %5s %4d",
                    timeFormat.format(Instant.ofEpochMilli(tse.getTimestamp())
                                             .atZone(ZoneId.systemDefault())),
                    tse.getKey(), tse.getValue())
        ));
        Vertex sink = dag.newVertex("sink", SinkProcessors.writeFileP(
                OUTPUT_DIR_NAME, Object::toString, UTF_8, false));

        tradeSource.localParallelism(1);

        return dag
                .edge(between(tradeSource, slidingStage1)
                        .partitioned(keyFn, HASH_CODE))
                .edge(between(slidingStage1, slidingStage2)
                        .partitioned(entryKey(), HASH_CODE)
                        .distributed())
                .edge(between(slidingStage2, formatOutput)
                        .isolated())
                .edge(between(formatOutput, sink));
//end::s1[]
    }
}
