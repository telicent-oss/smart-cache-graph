window.BENCHMARK_DATA = {
  "lastUpdate": 1765400287097,
  "repoUrl": "https://github.com/telicent-oss/smart-cache-graph",
  "entries": {
    "ABAC Benchmark": [
      {
        "commit": {
          "author": {
            "name": "Paul Gallagher",
            "username": "TelicentPaul",
            "email": "132362215+TelicentPaul@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "87ca5ceaade7239ffca81d2c500cf4dc130c1948",
          "message": "Merge pull request #333 from telicent-oss/minor/benchmark_fixes\n\n[Minor] Fixing Benchmark bugs",
          "timestamp": "2025-12-10T17:40:11Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/87ca5ceaade7239ffca81d2c500cf4dc130c1948"
        },
        "date": 1765389195576,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"simple\",\"tripleCount\":\"1000\"} )",
            "value": 0.11399251286513415,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"simple\",\"tripleCount\":\"10000\"} )",
            "value": 0.11346920100156661,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"medium\",\"tripleCount\":\"1000\"} )",
            "value": 0.11431846722524952,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"medium\",\"tripleCount\":\"10000\"} )",
            "value": 0.11351786568723417,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"complex\",\"tripleCount\":\"1000\"} )",
            "value": 0.11414506849177637,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"complex\",\"tripleCount\":\"10000\"} )",
            "value": 0.11446613334799723,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Paul Gallagher",
            "username": "TelicentPaul",
            "email": "132362215+TelicentPaul@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "0dd06a25cfd3204949579688776d9d6a2f15c25f",
          "message": "Merge pull request #334 from telicent-oss/minor/fixing_benchmark_v2\n\n[Minor] Fixing Benchmark bugs",
          "timestamp": "2025-12-10T20:43:43Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/0dd06a25cfd3204949579688776d9d6a2f15c25f"
        },
        "date": 1765400018782,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"simple\",\"tripleCount\":\"1000\"} )",
            "value": 0.11397484099082882,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"simple\",\"tripleCount\":\"10000\"} )",
            "value": 0.11541705318042594,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"medium\",\"tripleCount\":\"1000\"} )",
            "value": 0.11374929705097352,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"medium\",\"tripleCount\":\"10000\"} )",
            "value": 0.1146813814482432,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"complex\",\"tripleCount\":\"1000\"} )",
            "value": 0.11337252837152612,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"complex\",\"tripleCount\":\"10000\"} )",
            "value": 0.11369580958307532,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      }
    ],
    "CQRS Benchmark": [
      {
        "commit": {
          "author": {
            "name": "Paul Gallagher",
            "username": "TelicentPaul",
            "email": "132362215+TelicentPaul@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "87ca5ceaade7239ffca81d2c500cf4dc130c1948",
          "message": "Merge pull request #333 from telicent-oss/minor/benchmark_fixes\n\n[Minor] Fixing Benchmark bugs",
          "timestamp": "2025-12-10T17:40:11Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/87ca5ceaade7239ffca81d2c500cf4dc130c1948"
        },
        "date": 1765389264323,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.CQRSUpdateBenchmark.benchmarkCqrsUpdate",
            "value": 0.11215345183997369,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Paul Gallagher",
            "username": "TelicentPaul",
            "email": "132362215+TelicentPaul@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "0dd06a25cfd3204949579688776d9d6a2f15c25f",
          "message": "Merge pull request #334 from telicent-oss/minor/fixing_benchmark_v2\n\n[Minor] Fixing Benchmark bugs",
          "timestamp": "2025-12-10T20:43:43Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/0dd06a25cfd3204949579688776d9d6a2f15c25f"
        },
        "date": 1765400087455,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.CQRSUpdateBenchmark.benchmarkCqrsUpdate",
            "value": 0.10630420126005738,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      }
    ],
    "Ingest Benchmark": [
      {
        "commit": {
          "author": {
            "name": "Paul Gallagher",
            "username": "TelicentPaul",
            "email": "132362215+TelicentPaul@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "0dd06a25cfd3204949579688776d9d6a2f15c25f",
          "message": "Merge pull request #334 from telicent-oss/minor/fixing_benchmark_v2\n\n[Minor] Fixing Benchmark bugs",
          "timestamp": "2025-12-10T20:43:43Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/0dd06a25cfd3204949579688776d9d6a2f15c25f"
        },
        "date": 1765400286877,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"100\"} )",
            "value": 57.43084088417729,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"1000\"} )",
            "value": 23.57229193207516,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"10000\"} )",
            "value": 23.763579107387276,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      }
    ]
  }
}