package io.telicent.core;

import io.telicent.jena.abac.labels.Label;
import io.telicent.jena.abac.labels.Labels;
import io.telicent.jena.abac.labels.LabelsStore;
import io.telicent.labels.TripleLabels;
import io.telicent.labels.services.LabelsQueryService;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 10)
@Fork(1)
@State(Scope.Benchmark)
public class LabelsQueryServiceBenchmark {

    @Param({"1000", "10000"})
    private int tripleCount;

    private LabelsQueryService labelsQueryService;
    private Triple sampleTriple;

    @Setup(Level.Trial)
    public void setup() {
        DatasetGraph datasetGraph = DatasetGraphFactory.createTxnMem();
        LabelsStore labelsStore = Labels.createLabelsStoreMem();
        Label label = Label.fromText("A");

        for (int i = 0; i < tripleCount; i++) {
            Triple triple = Triple.create(
                    NodeFactory.createURI("urn:s:" + i),
                    NodeFactory.createURI("urn:p:" + (i % 19)),
                    NodeFactory.createLiteralString("o-" + i)
            );
            datasetGraph.getDefaultGraph().add(triple);
            labelsStore.add(triple.getSubject(), triple.getPredicate(), triple.getObject(), label);
            if (i == 0) {
                sampleTriple = triple;
            }
        }

        labelsQueryService = new LabelsQueryService(labelsStore, datasetGraph, "bench");
    }

    @Benchmark
    public void benchmarkQueryOnlyLabelStore(Blackhole bh) {
        List<TripleLabels> results = labelsQueryService.queryOnlyLabelStore(sampleTriple);
        bh.consume(results.size());
    }

    @Benchmark
    public void benchmarkQueryDSGAndLabelStore(Blackhole bh) {
        List<TripleLabels> results = labelsQueryService.queryDSGAndLabelStore(Triple.ANY);
        bh.consume(results.size());
    }
}
