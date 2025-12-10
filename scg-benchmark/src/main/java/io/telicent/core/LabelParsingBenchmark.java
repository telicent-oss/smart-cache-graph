package io.telicent.core;

import io.telicent.jena.abac.AE;
import io.telicent.jena.abac.attributes.AttributeExpr;
import io.telicent.jena.abac.labels.Label;
import org.openjdk.jmh.annotations.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class LabelParsingBenchmark {

    @Param({"1", "10", "100", "1000"})
    private int numberOfLabels;

    private String securityLabelsListString;

    @Setup(Level.Trial)
    public void setup() {
        // Generate a comma-separated string of 'numberOfLabels' (e.g., "label0,label1,label2")
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < numberOfLabels; i++) {
            labels.add("label" + i);
        }
        securityLabelsListString = String.join(",", labels);
    }

    /**
     * Benchmarks the old method of parsing security labels into a List<String>.
     * This directly simulates the logic found in the original FKProcessorSCG.java.
     *
     * @return A List of strings representing the parsed labels.
     */
    @Benchmark
    public List<String> benchmarkOldParseAttributeList() {
        // Original method returned null if input was null, replicating that behavior
        if (securityLabelsListString == null || securityLabelsListString.isEmpty()) {
            return null;
        }
        List<AttributeExpr> x = AE.parseExprList(securityLabelsListString);
        return AE.asStrings(x);
    }

    /**
     * Benchmarks the new method of parsing security labels into a List<Label>.
     * This directly simulates the logic found in the modified FKProcessorSCG.java.
     *
     * @return A List of Label objects representing the parsed labels.
     */
    @Benchmark
    public List<Label> benchmarkNewParseAttributeListToLabels() {
        List<AttributeExpr> exprList = AE.parseExprList(securityLabelsListString);
        List<Label> labels = new ArrayList<>(exprList.size());
        for (AttributeExpr expr : exprList) {
            labels.add(Label.fromText(expr.str(), StandardCharsets.UTF_8));
        }
        return labels;
    }
}