window.BENCHMARK_DATA = {
  "lastUpdate": 1767601049735,
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
      },
      {
        "commit": {
          "author": {
            "name": "Rob Walpole",
            "username": "robwtelicent",
            "email": "183595007+robwtelicent@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "4f4fde006558288e9b27090d3d224e37db062c7c",
          "message": "Merge pull request #335 from telicent-oss/release/0.92.5\n\nComplete Release 0.92.5",
          "timestamp": "2025-12-11T18:00:28Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/4f4fde006558288e9b27090d3d224e37db062c7c"
        },
        "date": 1765786386679,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"simple\",\"tripleCount\":\"1000\"} )",
            "value": 0.1128846141133926,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"simple\",\"tripleCount\":\"10000\"} )",
            "value": 0.1130973195323102,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"medium\",\"tripleCount\":\"1000\"} )",
            "value": 0.11216203570684322,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"medium\",\"tripleCount\":\"10000\"} )",
            "value": 0.11302620455172815,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"complex\",\"tripleCount\":\"1000\"} )",
            "value": 0.11333535460102004,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"complex\",\"tripleCount\":\"10000\"} )",
            "value": 0.11548235390974285,
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
          "id": "9d0b040f0ff8ea6955b0ce0bfdafbe4c20a2cd73",
          "message": "Merge pull request #337 from telicent-oss/dependabot/docker/scg-docker/telicent/telicent-java21-1.2.35\n\nBump telicent/telicent-java21 from 1.2.34 to 1.2.35 in /scg-docker",
          "timestamp": "2025-12-17T07:48:15Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/9d0b040f0ff8ea6955b0ce0bfdafbe4c20a2cd73"
        },
        "date": 1766391180754,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"simple\",\"tripleCount\":\"1000\"} )",
            "value": 0.11251592440170136,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"simple\",\"tripleCount\":\"10000\"} )",
            "value": 0.11337307988720871,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"medium\",\"tripleCount\":\"1000\"} )",
            "value": 0.11193825602717866,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"medium\",\"tripleCount\":\"10000\"} )",
            "value": 0.11362858910205131,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"complex\",\"tripleCount\":\"1000\"} )",
            "value": 0.11274014009298972,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"complex\",\"tripleCount\":\"10000\"} )",
            "value": 0.11319726958704648,
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
          "id": "9d0b040f0ff8ea6955b0ce0bfdafbe4c20a2cd73",
          "message": "Merge pull request #337 from telicent-oss/dependabot/docker/scg-docker/telicent/telicent-java21-1.2.35\n\nBump telicent/telicent-java21 from 1.2.34 to 1.2.35 in /scg-docker",
          "timestamp": "2025-12-17T07:48:15Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/9d0b040f0ff8ea6955b0ce0bfdafbe4c20a2cd73"
        },
        "date": 1766995971930,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"simple\",\"tripleCount\":\"1000\"} )",
            "value": 0.11271624185853475,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"simple\",\"tripleCount\":\"10000\"} )",
            "value": 0.1132831203520365,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"medium\",\"tripleCount\":\"1000\"} )",
            "value": 0.11339526687359273,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"medium\",\"tripleCount\":\"10000\"} )",
            "value": 0.11457532914436833,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"complex\",\"tripleCount\":\"1000\"} )",
            "value": 0.11458087642896657,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"complex\",\"tripleCount\":\"10000\"} )",
            "value": 0.11392087863549458,
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
          "id": "58f365afa16336e18a1fc17a89c8f44b7d6a5f21",
          "message": "Merge pull request #338 from telicent-oss/dependabot/maven/maven-patch-group-fbe9cc6aa5\n\nBump the maven-patch-group group with 6 updates",
          "timestamp": "2025-12-31T09:57:38Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/58f365afa16336e18a1fc17a89c8f44b7d6a5f21"
        },
        "date": 1767600781149,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"simple\",\"tripleCount\":\"1000\"} )",
            "value": 0.11096338569527722,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"simple\",\"tripleCount\":\"10000\"} )",
            "value": 0.11193373186231445,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"medium\",\"tripleCount\":\"1000\"} )",
            "value": 0.11192111933119109,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"medium\",\"tripleCount\":\"10000\"} )",
            "value": 0.11201629813418845,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"complex\",\"tripleCount\":\"1000\"} )",
            "value": 0.1127842697962355,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"complex\",\"tripleCount\":\"10000\"} )",
            "value": 0.11344490910173118,
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
      },
      {
        "commit": {
          "author": {
            "name": "Rob Walpole",
            "username": "robwtelicent",
            "email": "183595007+robwtelicent@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "4f4fde006558288e9b27090d3d224e37db062c7c",
          "message": "Merge pull request #335 from telicent-oss/release/0.92.5\n\nComplete Release 0.92.5",
          "timestamp": "2025-12-11T18:00:28Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/4f4fde006558288e9b27090d3d224e37db062c7c"
        },
        "date": 1765786456794,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.CQRSUpdateBenchmark.benchmarkCqrsUpdate",
            "value": 0.11148065380642798,
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
          "id": "9d0b040f0ff8ea6955b0ce0bfdafbe4c20a2cd73",
          "message": "Merge pull request #337 from telicent-oss/dependabot/docker/scg-docker/telicent/telicent-java21-1.2.35\n\nBump telicent/telicent-java21 from 1.2.34 to 1.2.35 in /scg-docker",
          "timestamp": "2025-12-17T07:48:15Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/9d0b040f0ff8ea6955b0ce0bfdafbe4c20a2cd73"
        },
        "date": 1766391249770,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.CQRSUpdateBenchmark.benchmarkCqrsUpdate",
            "value": 0.11097125672204045,
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
          "id": "9d0b040f0ff8ea6955b0ce0bfdafbe4c20a2cd73",
          "message": "Merge pull request #337 from telicent-oss/dependabot/docker/scg-docker/telicent/telicent-java21-1.2.35\n\nBump telicent/telicent-java21 from 1.2.34 to 1.2.35 in /scg-docker",
          "timestamp": "2025-12-17T07:48:15Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/9d0b040f0ff8ea6955b0ce0bfdafbe4c20a2cd73"
        },
        "date": 1766996040732,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.CQRSUpdateBenchmark.benchmarkCqrsUpdate",
            "value": 0.10830901345822566,
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
          "id": "58f365afa16336e18a1fc17a89c8f44b7d6a5f21",
          "message": "Merge pull request #338 from telicent-oss/dependabot/maven/maven-patch-group-fbe9cc6aa5\n\nBump the maven-patch-group group with 6 updates",
          "timestamp": "2025-12-31T09:57:38Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/58f365afa16336e18a1fc17a89c8f44b7d6a5f21"
        },
        "date": 1767600849823,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.CQRSUpdateBenchmark.benchmarkCqrsUpdate",
            "value": 0.11222553146669827,
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
      },
      {
        "commit": {
          "author": {
            "name": "Rob Walpole",
            "username": "robwtelicent",
            "email": "183595007+robwtelicent@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "4f4fde006558288e9b27090d3d224e37db062c7c",
          "message": "Merge pull request #335 from telicent-oss/release/0.92.5\n\nComplete Release 0.92.5",
          "timestamp": "2025-12-11T18:00:28Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/4f4fde006558288e9b27090d3d224e37db062c7c"
        },
        "date": 1765786657143,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"100\"} )",
            "value": 57.16473138673541,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"1000\"} )",
            "value": 34.9110335801769,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"10000\"} )",
            "value": 23.22166565131419,
            "unit": "ops/s",
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
          "id": "9d0b040f0ff8ea6955b0ce0bfdafbe4c20a2cd73",
          "message": "Merge pull request #337 from telicent-oss/dependabot/docker/scg-docker/telicent/telicent-java21-1.2.35\n\nBump telicent/telicent-java21 from 1.2.34 to 1.2.35 in /scg-docker",
          "timestamp": "2025-12-17T07:48:15Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/9d0b040f0ff8ea6955b0ce0bfdafbe4c20a2cd73"
        },
        "date": 1766391449123,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"100\"} )",
            "value": 61.88944785043507,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"1000\"} )",
            "value": 139.78844235857633,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"10000\"} )",
            "value": 22.036134576335677,
            "unit": "ops/s",
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
          "id": "9d0b040f0ff8ea6955b0ce0bfdafbe4c20a2cd73",
          "message": "Merge pull request #337 from telicent-oss/dependabot/docker/scg-docker/telicent/telicent-java21-1.2.35\n\nBump telicent/telicent-java21 from 1.2.34 to 1.2.35 in /scg-docker",
          "timestamp": "2025-12-17T07:48:15Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/9d0b040f0ff8ea6955b0ce0bfdafbe4c20a2cd73"
        },
        "date": 1766996240418,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"100\"} )",
            "value": 62.90572915464908,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"1000\"} )",
            "value": 86.81904699268412,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"10000\"} )",
            "value": 20.51399836689251,
            "unit": "ops/s",
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
          "id": "58f365afa16336e18a1fc17a89c8f44b7d6a5f21",
          "message": "Merge pull request #338 from telicent-oss/dependabot/maven/maven-patch-group-fbe9cc6aa5\n\nBump the maven-patch-group group with 6 updates",
          "timestamp": "2025-12-31T09:57:38Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/58f365afa16336e18a1fc17a89c8f44b7d6a5f21"
        },
        "date": 1767601049493,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"100\"} )",
            "value": 63.816493881281886,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"1000\"} )",
            "value": 87.2944150617449,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"10000\"} )",
            "value": 20.037034441618257,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      }
    ],
    "Label Benchmark": [
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
        "date": 1765404298747,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"1\"} )",
            "value": 0.25062918927967626,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"10\"} )",
            "value": 2.4302223595818893,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"100\"} )",
            "value": 26.023291116395036,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"1000\"} )",
            "value": 277.0522734974106,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"1\"} )",
            "value": 0.2614877936268173,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"10\"} )",
            "value": 2.305320475894213,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"100\"} )",
            "value": 24.602351119308974,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"1000\"} )",
            "value": 262.9266944847594,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Rob Walpole",
            "username": "robwtelicent",
            "email": "183595007+robwtelicent@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "4f4fde006558288e9b27090d3d224e37db062c7c",
          "message": "Merge pull request #335 from telicent-oss/release/0.92.5\n\nComplete Release 0.92.5",
          "timestamp": "2025-12-11T18:00:28Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/4f4fde006558288e9b27090d3d224e37db062c7c"
        },
        "date": 1765790670154,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"1\"} )",
            "value": 0.25129466400352046,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"10\"} )",
            "value": 2.4583596311964597,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"100\"} )",
            "value": 26.114680832692315,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"1000\"} )",
            "value": 277.6042811012781,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"1\"} )",
            "value": 0.2580993775548929,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"10\"} )",
            "value": 2.334964124028464,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"100\"} )",
            "value": 24.695958699210067,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"1000\"} )",
            "value": 263.1966580923447,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          }
        ]
      }
    ],
    "SCG Benchmark": [
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
        "date": 1765406884609,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"10\"} )",
            "value": 0.266799069101775,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"100\"} )",
            "value": 0.30136191545913904,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"1000\"} )",
            "value": 0.5888779901481901,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"10000\"} )",
            "value": 3.9195186073098176,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"1000000\"} )",
            "value": 519.3240660690175,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Rob Walpole",
            "username": "robwtelicent",
            "email": "183595007+robwtelicent@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "4f4fde006558288e9b27090d3d224e37db062c7c",
          "message": "Merge pull request #335 from telicent-oss/release/0.92.5\n\nComplete Release 0.92.5",
          "timestamp": "2025-12-11T18:00:28Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/4f4fde006558288e9b27090d3d224e37db062c7c"
        },
        "date": 1765793259701,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"10\"} )",
            "value": 0.27299793949026685,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"100\"} )",
            "value": 0.313346341540952,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"1000\"} )",
            "value": 0.5960808453305444,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"10000\"} )",
            "value": 4.417797722963419,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"1000000\"} )",
            "value": 531.2619170956024,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          }
        ]
      }
    ],
    "SCG Graph Scenario Benchmark": [
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
        "date": 1765407081334,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.SCGraphScenarioBenchmark.benchmarkGraphQL",
            "value": 0.6012825976947891,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGraphScenarioBenchmark.benchmarkSparqlSelect",
            "value": 0.787101181475899,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Rob Walpole",
            "username": "robwtelicent",
            "email": "183595007+robwtelicent@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "4f4fde006558288e9b27090d3d224e37db062c7c",
          "message": "Merge pull request #335 from telicent-oss/release/0.92.5\n\nComplete Release 0.92.5",
          "timestamp": "2025-12-11T18:00:28Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/4f4fde006558288e9b27090d3d224e37db062c7c"
        },
        "date": 1765793457211,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.SCGraphScenarioBenchmark.benchmarkGraphQL",
            "value": 0.6794118776952446,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGraphScenarioBenchmark.benchmarkSparqlSelect",
            "value": 0.7288294776439954,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      }
    ]
  }
}