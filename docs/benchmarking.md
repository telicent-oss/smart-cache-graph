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

### SCG Benchmarking
This populates an ABAC DatasetGraph with a range of random triples (10, 100, 1000, 10000, 100000, 100000) and then executes a select-all query against Graph.
