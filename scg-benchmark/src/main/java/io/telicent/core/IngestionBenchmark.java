package io.telicent.core;

import io.telicent.jena.abac.AttributeValueSet;
import io.telicent.jena.abac.core.AttributesStoreLocal;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.labels.Label;
import io.telicent.jena.abac.labels.Labels;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Micro-benchmark for ingesting a batch of quads through RDFChangesApplyWithLabels.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 10)
@Fork(1)
@State(Scope.Benchmark)
public class IngestionBenchmark {

    @Param({"100", "1000", "10000"})
    private int batchSize;

    private Node graphNode;
    private Node[] subjects;
    private Node[] predicates;
    private Node[] objects;
    private Label eventSecurityLabel;

    private final Random random = new Random(654321);

    @Setup(Level.Trial)
    public void setup() {
        graphNode = NodeFactory.createURI("urn:graph:benchmark");
        eventSecurityLabel = Label.fromText("SEC:A & SEC:B & SEC:C");

        subjects = new Node[batchSize];
        predicates = new Node[batchSize];
        objects = new Node[batchSize];

        for (int i = 0; i < batchSize; i++) {
            subjects[i] = NodeFactory.createURI("urn:s:" + random.nextInt(batchSize * 10));
            predicates[i] = NodeFactory.createURI("urn:p:" + random.nextInt(64));
            objects[i] = NodeFactory.createLiteralString("val-" + i);
        }
    }

    @Benchmark
    public void benchmarkIngestWithLabels(Blackhole bh) {
        DatasetGraphABAC dataset = createDatasetGraphABAC();

        RDFChangesApplyWithLabels changes = new RDFChangesApplyWithLabels(dataset, eventSecurityLabel);

        changes.txnBegin();
        for (int i = 0; i < batchSize; i++) {
            changes.add(graphNode, subjects[i], predicates[i], objects[i]);
        }
        changes.txnCommit();
        changes.finish();

        bh.consume(dataset.size());
    }

    private DatasetGraphABAC createDatasetGraphABAC() {
        AttributesStoreLocal store = new AttributesStoreLocal();
        store.put("user", AttributeValueSet.of("SEC:A", "SEC:B", "SEC:C"));
        return new DatasetGraphABAC(
                DatasetGraphFactory.createTxnMem(),
                "attributes",
                Labels.createLabelsStoreMem(),
                Label.fromText("DEFAULT"),
                store
        );
    }
}
