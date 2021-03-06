import com.hazelcast.core.IMap;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.datamodel.TimestampedEntry;
import com.hazelcast.jet.datamodel.Tuple2;
import com.hazelcast.jet.pipeline.BatchStage;
import com.hazelcast.jet.pipeline.BatchStageWithKey;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.Sources;
import com.hazelcast.jet.pipeline.StreamSource;
import com.hazelcast.jet.pipeline.StreamStage;
import com.hazelcast.jet.pipeline.StreamStageWithKey;
import datamodel.PageVisit;
import datamodel.Payment;
import datamodel.StockInfo;
import datamodel.Trade;

import java.util.List;
import java.util.Map.Entry;

import static com.hazelcast.jet.Traversers.traverseArray;
import static com.hazelcast.jet.Util.mapEventNewValue;
import static com.hazelcast.jet.Util.mapPutEvents;
import static com.hazelcast.jet.aggregate.AggregateOperations.counting;
import static com.hazelcast.jet.aggregate.AggregateOperations.maxBy;
import static com.hazelcast.jet.aggregate.AggregateOperations.toList;
import static com.hazelcast.jet.function.DistributedComparator.comparing;
import static com.hazelcast.jet.function.DistributedFunctions.entryValue;
import static com.hazelcast.jet.function.DistributedFunctions.wholeItem;
import static com.hazelcast.jet.pipeline.JoinClause.joinMapEntries;
import static com.hazelcast.jet.pipeline.JournalInitialPosition.START_FROM_CURRENT;
import static com.hazelcast.jet.pipeline.JournalInitialPosition.START_FROM_OLDEST;
import static com.hazelcast.jet.pipeline.Sources.list;
import static com.hazelcast.jet.pipeline.WindowDefinition.sliding;

public class CheatSheet {
    static Pipeline p;

    static void s1() {
        //tag::s1[]
        BatchStage<String> lines = p.drawFrom(list("lines"));
        BatchStage<String> lowercased = lines.map(String::toLowerCase);
        //end::s1[]
    }

    static void s2() {
        //tag::s2[]
        BatchStage<String> lines = p.drawFrom(list("lines"));
        BatchStage<String> nonEmpty = lines.filter(string -> !string.isEmpty());
        //end::s2[]
    }

    static void s3() {
        //tag::s3[]
        BatchStage<String> lines = p.drawFrom(list("lines"));
        BatchStage<String> words = lines.flatMap(
                line -> traverseArray(line.split("\\W+")));
        //end::s3[]
    }

    static void s4() {
        //tag::s4[]
        BatchStage<Trade> trades = p.drawFrom(list("trades"));
        BatchStage<Entry<String, StockInfo>> stockInfo =
                p.drawFrom(list("stockInfo"));
        BatchStage<Trade> joined = trades.hashJoin(stockInfo,
                joinMapEntries(Trade::ticker), Trade::setStockInfo);
        //end::s4[]
    }

    static void s4a() {
        JetInstance jet = Jet.newJetInstance();
        //tag::s4a[]
        StreamSource<Trade> tradesSource = Sources.mapJournal("trades",
                mapPutEvents(), mapEventNewValue(), START_FROM_CURRENT);
        IMap<String, StockInfo> stockMap = jet.getMap("stock-info");

        Pipeline p = Pipeline.create();
        p.drawFrom(tradesSource)
         .groupingKey(Trade::ticker)
         .mapUsingIMap(stockMap, Trade::setStockInfo)
         .drainTo(Sinks.list("result"));
        //end::s4a[]
    }

    static void s5() {
        //tag::s5[]
        BatchStage<String> lines = p.drawFrom(list("lines"));
        BatchStage<Long> count = lines.aggregate(counting());
        //end::s5[]
    }

    static void s6() {
        //tag::s6[]
        BatchStage<String> words = p.drawFrom(list("words"));
        BatchStage<Entry<String, Long>> wordsAndCounts =
                words.groupingKey(wholeItem())
                     .aggregate(counting());
        //end::s6[]
    }

    static void s7() {
        //tag::s7[]
        StreamStage<Entry<Long, String>> tweetWords = p.drawFrom(
                Sources.mapJournal("tweet-words", START_FROM_OLDEST));
        StreamStage<TimestampedEntry<String, Long>> wordFreqs =
                tweetWords.addTimestamps(e -> e.getKey(), 1000)
                          .window(sliding(1000, 10))
                          .groupingKey(entryValue())
                          .aggregate(counting());
        //end::s7[]
    }

    static void s8() {
        //tag::s8[]
        BatchStageWithKey<PageVisit, Integer> pageVisits =
                p.drawFrom(Sources.<PageVisit>list("pageVisit"))
                 .groupingKey(pageVisit -> pageVisit.userId());
        BatchStageWithKey<Payment, Integer> payments =
                p.drawFrom(Sources.<Payment>list("payment"))
                 .groupingKey(payment -> payment.userId());
        BatchStage<Entry<Integer, Tuple2<List<PageVisit>, List<Payment>>>>
            joined = pageVisits.aggregate2(toList(), payments, toList());
        //end::s8[]
    }

    static void s9() {
        //tag::s9[]
        StreamStageWithKey<PageVisit, Integer> pageVisits =
                p.<PageVisit>drawFrom(Sources.mapJournal("pageVisits",
                        mapPutEvents(), mapEventNewValue(), START_FROM_OLDEST))
                        .addTimestamps(PageVisit::timestamp, 1000)
                        .groupingKey(PageVisit::userId);
        StreamStageWithKey<Payment, Integer> payments =
                p.<Payment>drawFrom(Sources.mapJournal("payments",
                        mapPutEvents(), mapEventNewValue(), START_FROM_OLDEST))
                        .addTimestamps(Payment::timestamp, 1000)
                        .groupingKey(Payment::userId);
        StreamStage<TimestampedEntry<Integer,
                                    Tuple2<List<PageVisit>, List<Payment>>>>
            joined = pageVisits.window(sliding(60_000, 1000))
                               .aggregate2(toList(), payments, toList());
        //end::s9[]
    }

    static void s10() {
        //tag::s10[]
        Pipeline p = Pipeline.create();
        StreamSource<Trade> tradesSource = Sources.mapJournal("trades",
                mapPutEvents(), mapEventNewValue(), START_FROM_CURRENT);
        StreamStage<Trade> currLargestTrade =
                p.drawFrom(tradesSource)
                 .rollingAggregate(maxBy(comparing(Trade::worth)));
        //end::s10[]
    }

    static void s11() {
        //tag::s11[]
        BatchStage<String> strings = someStrings();
        BatchStage<String> distinctStrings = strings.distinct();
        BatchStage<String> distinctByPrefix =
                strings.groupingKey(s -> s.substring(0, 4)).distinct();
        //end::s11[]
    }

    private static BatchStage<String> someStrings() {
        throw new UnsupportedOperationException();
    }

    static void s12() {
        //tag::s12[]
        StreamStage<Trade> tradesNewYork = trades("new-york");
        StreamStage<Trade> tradesTokyo = trades("tokyo");
        StreamStage<Trade> tradesNyAndTokyo =
                tradesNewYork.merge(tradesTokyo);
        //end::s12[]
    }

    private static StreamStage<Trade> trades(String name) {
        throw new UnsupportedOperationException();
    }

    static void s13() {
        //tag::s13[]
        //end::s13[]
    }
    }
