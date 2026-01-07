# Benchmarking

## Introduction
The scg-benchmarking module is to allow us to carry out performance testing on specific sub-modules within our code.

Note: this should be only used as a guide. Local runs can be impacted by any number of things - how many other apps are running on the device etc...; so we cannot put too much faith in it.

## Build the Benchmarking jar
This will build the benchmarks jar
```bash
mvn package
```
## Run Benchmarking jar
It's recommended to store the output to file.

*Note:* this can take a significant time to run.
```bash
java -jar target/benchmarks.jar 2>/dev/null | tee run_output.txt
```

## Parsing Results
This is a simple python script that reduces the output down to more minimal, useful information.
```bash
python3 parse_output.py run_output.txt
```

## Weekly Benchmark Results
A weekly GitHub Action runs these Benchmarking tests and publishes results to the repo's documentation site:
https://telicent-oss.github.io/smart-cache-graph/dev/bench/

The file can be found [here](../.github/workflows/weekly-benchmark.yml)

## Profiling

In theory, JMH can be made to work with certain profilers. In reality, due to limitations with permissions and OS, they are not able to be used.

### GC
This profile tracks and assesses the Garbage Collection.
```bash
java -jar target/benchmarks.jar -prof gc -f 1 -wi 5 -i 5
```
### Stack
As the name suggests this provides analysis on the performance of the stack.

```bash
java -jar target/benchmarks.jar -prof stack -f 1 -wi 5 -i 5
```
### Java Flight Recorder
This leverages the JFR analysis, creating a profile.jfr file for each benchmark.
```bash
java -jar target/benchmarks.jar -prof stack -f 1 -wi 5 -i 5
```

## Benchmarking classes

### ABAC Label Evaluation Benchmark
Evaluates ABAC label checks by running a SPARQL select over labeled data with varying label complexity.
Options: `labelComplexity` (simple, medium, complex), `tripleCount` (1000, 10000).

### Access Query Service Benchmark
Measures access query service performance for fetching triples and counting visible triples.
Options: `tripleCount` (1000, 10000).

### Backup/Restore Benchmark
Benchmarks backup and restore operations over ABAC datasets backed by TDB2 and RocksDB labels.
Options: `tripleCount` (1000, 10000).

### CQRS Update Benchmark
Measures CQRS update performance for SPARQL update requests with a mock Kafka producer.
Options: none.

### Ingestion Benchmark
Benchmarks throughput for ingesting batches of labeled quads.
Options: `batchSize` (100, 1000, 10000).

### Label Parsing Benchmark
Compares label parsing approaches for attribute list strings.
Options: `numberOfLabels` (1, 10, 100, 1000).

### Labels Query Service Benchmark
Measures label query service performance across label-store-only and DSG+label-store paths.
Options: `tripleCount` (1000, 10000).

### SCG Benchmark
Populates an ABAC DatasetGraph with random triples and executes a select-all query.
Options: `arraySize` (10, 100, 1000, 10000, 1000000).

### SC Graph Scenario Benchmark
Runs end-to-end SPARQL and GraphQL requests against an in-process Smart Cache Graph with sample data loaded.
Options: none.

### SC Graph ABAC Persistent Scenario Benchmark
Runs end-to-end SPARQL against a persistent ABAC-configured Smart Cache Graph with labeled sample data.
Options: none.
