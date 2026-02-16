window.BENCHMARK_DATA = {
  "lastUpdate": 1771230298731,
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
          "id": "bfc9d78e5ef552c4a618a6344e4fa566db2b0895",
          "message": "Merge pull request #339 from telicent-oss/minor/benchmakring_follow_up\n\n[Minor] Benchmarking follow up - from before NY",
          "timestamp": "2026-01-07T15:20:39Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/bfc9d78e5ef552c4a618a6344e4fa566db2b0895"
        },
        "date": 1767805849675,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"simple\",\"tripleCount\":\"1000\"} )",
            "value": 0.1162447992152833,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"simple\",\"tripleCount\":\"10000\"} )",
            "value": 0.11455864962809884,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"medium\",\"tripleCount\":\"1000\"} )",
            "value": 0.11391206019352436,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"medium\",\"tripleCount\":\"10000\"} )",
            "value": 0.11565459998917857,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"complex\",\"tripleCount\":\"1000\"} )",
            "value": 0.11406574344696792,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"complex\",\"tripleCount\":\"10000\"} )",
            "value": 0.11549639297454632,
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
          "id": "de216a089334f623ad999422c34150a95470aa08",
          "message": "Merge pull request #340 from telicent-oss/dependabot/maven/maven-patch-group-f90304635c\n\nBump the maven-patch-group group with 6 updates",
          "timestamp": "2026-01-07T17:33:30Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/de216a089334f623ad999422c34150a95470aa08"
        },
        "date": 1768205561017,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"simple\",\"tripleCount\":\"1000\"} )",
            "value": 0.11326369387776056,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"simple\",\"tripleCount\":\"10000\"} )",
            "value": 0.11234570530427815,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"medium\",\"tripleCount\":\"1000\"} )",
            "value": 0.11392664778312409,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"medium\",\"tripleCount\":\"10000\"} )",
            "value": 0.11287505630600228,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"complex\",\"tripleCount\":\"1000\"} )",
            "value": 0.11295840379158315,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"complex\",\"tripleCount\":\"10000\"} )",
            "value": 0.1124552587047156,
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
          "id": "9c23109b008a8c00081239144766b25e5e098f8d",
          "message": "Merge pull request #341 from telicent-oss/dependabot/maven/maven-patch-group-6c1186fe36\n\nBump org.sonatype.central:central-publishing-maven-plugin from 0.9.0 to 0.10.0 in the maven-patch-group group",
          "timestamp": "2026-01-13T12:44:52Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/9c23109b008a8c00081239144766b25e5e098f8d"
        },
        "date": 1768810390652,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"simple\",\"tripleCount\":\"1000\"} )",
            "value": 0.1479372977798617,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"simple\",\"tripleCount\":\"10000\"} )",
            "value": 0.14893648647873398,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"medium\",\"tripleCount\":\"1000\"} )",
            "value": 0.14656057792020044,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"medium\",\"tripleCount\":\"10000\"} )",
            "value": 0.14798842973404788,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"complex\",\"tripleCount\":\"1000\"} )",
            "value": 0.14789467670107678,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"complex\",\"tripleCount\":\"10000\"} )",
            "value": 0.1468917413504616,
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
          "id": "c288b71f581140df97930ce6ae42210f3996e99f",
          "message": "Merge pull request #349 from telicent-oss/release/0.93.0\n\nComplete Release 0.93.0",
          "timestamp": "2026-01-23T12:41:20Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/c288b71f581140df97930ce6ae42210f3996e99f"
        },
        "date": 1769415156135,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"simple\",\"tripleCount\":\"1000\"} )",
            "value": 0.11308211834289383,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"simple\",\"tripleCount\":\"10000\"} )",
            "value": 0.11402833155182254,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"medium\",\"tripleCount\":\"1000\"} )",
            "value": 0.11337970702984482,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"medium\",\"tripleCount\":\"10000\"} )",
            "value": 0.11392806352690157,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"complex\",\"tripleCount\":\"1000\"} )",
            "value": 0.11452481372818327,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"complex\",\"tripleCount\":\"10000\"} )",
            "value": 0.11527679587335618,
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
          "id": "449e6f977e28e392659be6fd615ee9756c663b5d",
          "message": "Merge pull request #351 from telicent-oss/dependabot/docker/scg-docker/telicent/telicent-java21-1.2.40\n\nBump telicent/telicent-java21 from 1.2.39 to 1.2.40 in /scg-docker",
          "timestamp": "2026-01-29T07:49:07Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/449e6f977e28e392659be6fd615ee9756c663b5d"
        },
        "date": 1770020355618,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"simple\",\"tripleCount\":\"1000\"} )",
            "value": 0.1160633293548318,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"simple\",\"tripleCount\":\"10000\"} )",
            "value": 0.11606611917763057,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"medium\",\"tripleCount\":\"1000\"} )",
            "value": 0.11568396487012175,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"medium\",\"tripleCount\":\"10000\"} )",
            "value": 0.11702662605703999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"complex\",\"tripleCount\":\"1000\"} )",
            "value": 0.11558849226947054,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"complex\",\"tripleCount\":\"10000\"} )",
            "value": 0.11580520355543371,
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
          "id": "585d68f290598186cec7a9787bd3060c7c4de508",
          "message": "Merge pull request #352 from telicent-oss/dependabot/maven/maven-patch-group-640e564cb0\n\nBump the maven-patch-group group with 5 updates",
          "timestamp": "2026-02-03T12:02:26Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/585d68f290598186cec7a9787bd3060c7c4de508"
        },
        "date": 1770625304224,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"simple\",\"tripleCount\":\"1000\"} )",
            "value": 0.14984832601359266,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"simple\",\"tripleCount\":\"10000\"} )",
            "value": 0.15050110743352446,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"medium\",\"tripleCount\":\"1000\"} )",
            "value": 0.15039272473316398,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"medium\",\"tripleCount\":\"10000\"} )",
            "value": 0.14845467302210574,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"complex\",\"tripleCount\":\"1000\"} )",
            "value": 0.1506376864394167,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"complex\",\"tripleCount\":\"10000\"} )",
            "value": 0.15071003355790447,
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
          "id": "83d28ff3826e84062be600b36b2aef07b553cb58",
          "message": "Merge pull request #355 from telicent-oss/core_714_consolidate_graph_logging\n\n[CORE-714] Consolidate logging so approach is consistent, using SLF4 Logger over Jena FmtLog.",
          "timestamp": "2026-02-12T09:56:39Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/83d28ff3826e84062be600b36b2aef07b553cb58"
        },
        "date": 1771230029821,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"simple\",\"tripleCount\":\"1000\"} )",
            "value": 0.113085294119836,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"simple\",\"tripleCount\":\"10000\"} )",
            "value": 0.11341784161145033,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"medium\",\"tripleCount\":\"1000\"} )",
            "value": 0.11221991577185957,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"medium\",\"tripleCount\":\"10000\"} )",
            "value": 0.11415186081949809,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"complex\",\"tripleCount\":\"1000\"} )",
            "value": 0.11304147057031436,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.ABACLabelEvaluationBenchmark.benchmarkAbacQuery ( {\"labelComplexity\":\"complex\",\"tripleCount\":\"10000\"} )",
            "value": 0.11253622079421796,
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
          "id": "bfc9d78e5ef552c4a618a6344e4fa566db2b0895",
          "message": "Merge pull request #339 from telicent-oss/minor/benchmakring_follow_up\n\n[Minor] Benchmarking follow up - from before NY",
          "timestamp": "2026-01-07T15:20:39Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/bfc9d78e5ef552c4a618a6344e4fa566db2b0895"
        },
        "date": 1767805918872,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.CQRSUpdateBenchmark.benchmarkCqrsUpdate",
            "value": 0.8962386606554056,
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
          "id": "de216a089334f623ad999422c34150a95470aa08",
          "message": "Merge pull request #340 from telicent-oss/dependabot/maven/maven-patch-group-f90304635c\n\nBump the maven-patch-group group with 6 updates",
          "timestamp": "2026-01-07T17:33:30Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/de216a089334f623ad999422c34150a95470aa08"
        },
        "date": 1768205630525,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.CQRSUpdateBenchmark.benchmarkCqrsUpdate",
            "value": 0.8897241974990069,
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
          "id": "9c23109b008a8c00081239144766b25e5e098f8d",
          "message": "Merge pull request #341 from telicent-oss/dependabot/maven/maven-patch-group-6c1186fe36\n\nBump org.sonatype.central:central-publishing-maven-plugin from 0.9.0 to 0.10.0 in the maven-patch-group group",
          "timestamp": "2026-01-13T12:44:52Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/9c23109b008a8c00081239144766b25e5e098f8d"
        },
        "date": 1768810459416,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.CQRSUpdateBenchmark.benchmarkCqrsUpdate",
            "value": 0.7426153109135594,
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
          "id": "c288b71f581140df97930ce6ae42210f3996e99f",
          "message": "Merge pull request #349 from telicent-oss/release/0.93.0\n\nComplete Release 0.93.0",
          "timestamp": "2026-01-23T12:41:20Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/c288b71f581140df97930ce6ae42210f3996e99f"
        },
        "date": 1769415225803,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.CQRSUpdateBenchmark.benchmarkCqrsUpdate",
            "value": 0.04543418101374204,
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
          "id": "449e6f977e28e392659be6fd615ee9756c663b5d",
          "message": "Merge pull request #351 from telicent-oss/dependabot/docker/scg-docker/telicent/telicent-java21-1.2.40\n\nBump telicent/telicent-java21 from 1.2.39 to 1.2.40 in /scg-docker",
          "timestamp": "2026-01-29T07:49:07Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/449e6f977e28e392659be6fd615ee9756c663b5d"
        },
        "date": 1770020424496,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.CQRSUpdateBenchmark.benchmarkCqrsUpdate",
            "value": 0.041688610970715675,
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
          "id": "585d68f290598186cec7a9787bd3060c7c4de508",
          "message": "Merge pull request #352 from telicent-oss/dependabot/maven/maven-patch-group-640e564cb0\n\nBump the maven-patch-group group with 5 updates",
          "timestamp": "2026-02-03T12:02:26Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/585d68f290598186cec7a9787bd3060c7c4de508"
        },
        "date": 1770625378498,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.CQRSUpdateBenchmark.benchmarkCqrsUpdate",
            "value": 0.04530502406649987,
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
          "id": "83d28ff3826e84062be600b36b2aef07b553cb58",
          "message": "Merge pull request #355 from telicent-oss/core_714_consolidate_graph_logging\n\n[CORE-714] Consolidate logging so approach is consistent, using SLF4 Logger over Jena FmtLog.",
          "timestamp": "2026-02-12T09:56:39Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/83d28ff3826e84062be600b36b2aef07b553cb58"
        },
        "date": 1771230098837,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.CQRSUpdateBenchmark.benchmarkCqrsUpdate",
            "value": 0.04225059612949682,
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
          "id": "de216a089334f623ad999422c34150a95470aa08",
          "message": "Merge pull request #340 from telicent-oss/dependabot/maven/maven-patch-group-f90304635c\n\nBump the maven-patch-group group with 6 updates",
          "timestamp": "2026-01-07T17:33:30Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/de216a089334f623ad999422c34150a95470aa08"
        },
        "date": 1768205829860,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"100\"} )",
            "value": 53.24128034169704,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"1000\"} )",
            "value": 112.47763126040988,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"10000\"} )",
            "value": 23.79096323741677,
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
          "id": "9c23109b008a8c00081239144766b25e5e098f8d",
          "message": "Merge pull request #341 from telicent-oss/dependabot/maven/maven-patch-group-6c1186fe36\n\nBump org.sonatype.central:central-publishing-maven-plugin from 0.9.0 to 0.10.0 in the maven-patch-group group",
          "timestamp": "2026-01-13T12:44:52Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/9c23109b008a8c00081239144766b25e5e098f8d"
        },
        "date": 1768810658488,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"100\"} )",
            "value": 59.0037851844193,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"1000\"} )",
            "value": 126.71276523560009,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"10000\"} )",
            "value": 22.4129581326956,
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
          "id": "c288b71f581140df97930ce6ae42210f3996e99f",
          "message": "Merge pull request #349 from telicent-oss/release/0.93.0\n\nComplete Release 0.93.0",
          "timestamp": "2026-01-23T12:41:20Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/c288b71f581140df97930ce6ae42210f3996e99f"
        },
        "date": 1769415425837,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"100\"} )",
            "value": 56.71941764090864,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"1000\"} )",
            "value": 138.2140385867998,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"10000\"} )",
            "value": 27.178306937209328,
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
          "id": "449e6f977e28e392659be6fd615ee9756c663b5d",
          "message": "Merge pull request #351 from telicent-oss/dependabot/docker/scg-docker/telicent/telicent-java21-1.2.40\n\nBump telicent/telicent-java21 from 1.2.39 to 1.2.40 in /scg-docker",
          "timestamp": "2026-01-29T07:49:07Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/449e6f977e28e392659be6fd615ee9756c663b5d"
        },
        "date": 1770020623876,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"100\"} )",
            "value": 61.028854768492764,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"1000\"} )",
            "value": 155.18347097668362,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"10000\"} )",
            "value": 19.736460691780948,
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
          "id": "585d68f290598186cec7a9787bd3060c7c4de508",
          "message": "Merge pull request #352 from telicent-oss/dependabot/maven/maven-patch-group-640e564cb0\n\nBump the maven-patch-group group with 5 updates",
          "timestamp": "2026-02-03T12:02:26Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/585d68f290598186cec7a9787bd3060c7c4de508"
        },
        "date": 1770625579255,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"100\"} )",
            "value": 51.630106521289655,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"1000\"} )",
            "value": 25.08245173827011,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"10000\"} )",
            "value": 20.15522785668629,
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
          "id": "83d28ff3826e84062be600b36b2aef07b553cb58",
          "message": "Merge pull request #355 from telicent-oss/core_714_consolidate_graph_logging\n\n[CORE-714] Consolidate logging so approach is consistent, using SLF4 Logger over Jena FmtLog.",
          "timestamp": "2026-02-12T09:56:39Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/83d28ff3826e84062be600b36b2aef07b553cb58"
        },
        "date": 1771230298375,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"100\"} )",
            "value": 55.24699106959946,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"1000\"} )",
            "value": 99.35102621887467,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.IngestionBenchmark.benchmarkIngestWithLabels ( {\"batchSize\":\"10000\"} )",
            "value": 21.948166365863084,
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
        "date": 1767605061451,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"1\"} )",
            "value": 0.2536894823768356,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"10\"} )",
            "value": 2.5162376782952935,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"100\"} )",
            "value": 25.64392684260279,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"1000\"} )",
            "value": 278.2075816442047,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"1\"} )",
            "value": 0.25697812382684837,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"10\"} )",
            "value": 2.3222487314270355,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"100\"} )",
            "value": 24.71577753590965,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"1000\"} )",
            "value": 263.8847578079151,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
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
          "id": "de216a089334f623ad999422c34150a95470aa08",
          "message": "Merge pull request #340 from telicent-oss/dependabot/maven/maven-patch-group-f90304635c\n\nBump the maven-patch-group group with 6 updates",
          "timestamp": "2026-01-07T17:33:30Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/de216a089334f623ad999422c34150a95470aa08"
        },
        "date": 1768209842349,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"1\"} )",
            "value": 0.2525976912105517,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"10\"} )",
            "value": 2.4204896925240518,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"100\"} )",
            "value": 27.8356331310034,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"1000\"} )",
            "value": 274.03132041230083,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"1\"} )",
            "value": 0.2584716077757253,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"10\"} )",
            "value": 2.3265532777725797,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"100\"} )",
            "value": 24.689383460512385,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"1000\"} )",
            "value": 263.01066253044087,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
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
          "id": "9c23109b008a8c00081239144766b25e5e098f8d",
          "message": "Merge pull request #341 from telicent-oss/dependabot/maven/maven-patch-group-6c1186fe36\n\nBump org.sonatype.central:central-publishing-maven-plugin from 0.9.0 to 0.10.0 in the maven-patch-group group",
          "timestamp": "2026-01-13T12:44:52Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/9c23109b008a8c00081239144766b25e5e098f8d"
        },
        "date": 1768814669622,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"1\"} )",
            "value": 0.33126243170252256,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"10\"} )",
            "value": 3.187268718200652,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"100\"} )",
            "value": 33.99716380953513,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"1000\"} )",
            "value": 371.0956126174614,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"1\"} )",
            "value": 0.3544286586285144,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"10\"} )",
            "value": 3.122402326387545,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"100\"} )",
            "value": 33.52332778025999,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"1000\"} )",
            "value": 357.3662034466626,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
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
          "id": "c288b71f581140df97930ce6ae42210f3996e99f",
          "message": "Merge pull request #349 from telicent-oss/release/0.93.0\n\nComplete Release 0.93.0",
          "timestamp": "2026-01-23T12:41:20Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/c288b71f581140df97930ce6ae42210f3996e99f"
        },
        "date": 1769419438800,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"1\"} )",
            "value": 0.25399110690746085,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"10\"} )",
            "value": 2.4016667950045556,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"100\"} )",
            "value": 25.92097349012307,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"1000\"} )",
            "value": 271.4422704756525,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"1\"} )",
            "value": 0.2548981699097624,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"10\"} )",
            "value": 2.3189801044767386,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"100\"} )",
            "value": 24.62524905526469,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"1000\"} )",
            "value": 263.3235755814473,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
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
          "id": "449e6f977e28e392659be6fd615ee9756c663b5d",
          "message": "Merge pull request #351 from telicent-oss/dependabot/docker/scg-docker/telicent/telicent-java21-1.2.40\n\nBump telicent/telicent-java21 from 1.2.39 to 1.2.40 in /scg-docker",
          "timestamp": "2026-01-29T07:49:07Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/449e6f977e28e392659be6fd615ee9756c663b5d"
        },
        "date": 1770024636271,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"1\"} )",
            "value": 0.2544154411120098,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"10\"} )",
            "value": 2.39210863935865,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"100\"} )",
            "value": 25.60450094736708,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"1000\"} )",
            "value": 275.9091854895364,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"1\"} )",
            "value": 0.2630101191932372,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"10\"} )",
            "value": 2.2860100528221805,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"100\"} )",
            "value": 24.46732439721268,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"1000\"} )",
            "value": 262.83706379145895,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
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
          "id": "585d68f290598186cec7a9787bd3060c7c4de508",
          "message": "Merge pull request #352 from telicent-oss/dependabot/maven/maven-patch-group-640e564cb0\n\nBump the maven-patch-group group with 5 updates",
          "timestamp": "2026-02-03T12:02:26Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/585d68f290598186cec7a9787bd3060c7c4de508"
        },
        "date": 1770629590771,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"1\"} )",
            "value": 0.33034194010502227,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"10\"} )",
            "value": 3.1803588440152124,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"100\"} )",
            "value": 34.45763913360455,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkNewParseAttributeListToLabels ( {\"numberOfLabels\":\"1000\"} )",
            "value": 370.28870095715314,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"1\"} )",
            "value": 0.3553230244425202,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"10\"} )",
            "value": 3.122995562542847,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"100\"} )",
            "value": 33.00972976614362,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelParsingBenchmark.benchmarkOldParseAttributeList ( {\"numberOfLabels\":\"1000\"} )",
            "value": 356.28764873360325,
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
        "date": 1767607648161,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"10\"} )",
            "value": 0.2656341422478414,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"100\"} )",
            "value": 0.29745985164348654,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"1000\"} )",
            "value": 0.5869748025917461,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"10000\"} )",
            "value": 4.090578965442674,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"1000000\"} )",
            "value": 531.3026802238948,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
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
          "id": "de216a089334f623ad999422c34150a95470aa08",
          "message": "Merge pull request #340 from telicent-oss/dependabot/maven/maven-patch-group-f90304635c\n\nBump the maven-patch-group group with 6 updates",
          "timestamp": "2026-01-07T17:33:30Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/de216a089334f623ad999422c34150a95470aa08"
        },
        "date": 1768213117069,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"10\"} )",
            "value": 0.2712228927449465,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"100\"} )",
            "value": 0.2997969312210362,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"1000\"} )",
            "value": 0.5888850017304263,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"10000\"} )",
            "value": 4.0693057315561765,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"1000000\"} )",
            "value": 526.9822441037945,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
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
          "id": "9c23109b008a8c00081239144766b25e5e098f8d",
          "message": "Merge pull request #341 from telicent-oss/dependabot/maven/maven-patch-group-6c1186fe36\n\nBump org.sonatype.central:central-publishing-maven-plugin from 0.9.0 to 0.10.0 in the maven-patch-group group",
          "timestamp": "2026-01-13T12:44:52Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/9c23109b008a8c00081239144766b25e5e098f8d"
        },
        "date": 1768817937352,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"10\"} )",
            "value": 0.31795497800319905,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"100\"} )",
            "value": 0.3532532490535811,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"1000\"} )",
            "value": 0.6945759416788188,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"10000\"} )",
            "value": 4.028220700177553,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"1000000\"} )",
            "value": 482.3636455712986,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
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
          "id": "c288b71f581140df97930ce6ae42210f3996e99f",
          "message": "Merge pull request #349 from telicent-oss/release/0.93.0\n\nComplete Release 0.93.0",
          "timestamp": "2026-01-23T12:41:20Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/c288b71f581140df97930ce6ae42210f3996e99f"
        },
        "date": 1769422713358,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"10\"} )",
            "value": 0.2753189654501751,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"100\"} )",
            "value": 0.3107062686133113,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"1000\"} )",
            "value": 0.6088621547489619,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"10000\"} )",
            "value": 3.7075030436015535,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"1000000\"} )",
            "value": 524.1524113447775,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
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
          "id": "449e6f977e28e392659be6fd615ee9756c663b5d",
          "message": "Merge pull request #351 from telicent-oss/dependabot/docker/scg-docker/telicent/telicent-java21-1.2.40\n\nBump telicent/telicent-java21 from 1.2.39 to 1.2.40 in /scg-docker",
          "timestamp": "2026-01-29T07:49:07Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/449e6f977e28e392659be6fd615ee9756c663b5d"
        },
        "date": 1770027913904,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"10\"} )",
            "value": 0.2667060868182008,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"100\"} )",
            "value": 0.30140784597873266,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"1000\"} )",
            "value": 0.6106744069011105,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"10000\"} )",
            "value": 4.631098551778714,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"1000000\"} )",
            "value": 566.0730024844031,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
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
          "id": "585d68f290598186cec7a9787bd3060c7c4de508",
          "message": "Merge pull request #352 from telicent-oss/dependabot/maven/maven-patch-group-640e564cb0\n\nBump the maven-patch-group group with 5 updates",
          "timestamp": "2026-02-03T12:02:26Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/585d68f290598186cec7a9787bd3060c7c4de508"
        },
        "date": 1770632861527,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"10\"} )",
            "value": 0.3201737967941592,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"100\"} )",
            "value": 0.3518540666001839,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"1000\"} )",
            "value": 0.6885965715995758,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"10000\"} )",
            "value": 4.106058684027318,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 5\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGBenchmark.test_executeQuery ( {\"arraySize\":\"1000000\"} )",
            "value": 481.63575062900424,
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
        "date": 1767607844779,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.SCGraphScenarioBenchmark.benchmarkGraphQL",
            "value": 0.8504957286993562,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGraphScenarioBenchmark.benchmarkSparqlSelect",
            "value": 0.8443292171178245,
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
          "id": "de216a089334f623ad999422c34150a95470aa08",
          "message": "Merge pull request #340 from telicent-oss/dependabot/maven/maven-patch-group-f90304635c\n\nBump the maven-patch-group group with 6 updates",
          "timestamp": "2026-01-07T17:33:30Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/de216a089334f623ad999422c34150a95470aa08"
        },
        "date": 1768213254078,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.SCGraphScenarioBenchmark.benchmarkGraphQL",
            "value": 0.7420084390319042,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGraphScenarioBenchmark.benchmarkSparqlSelect",
            "value": 0.7836670714596112,
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
          "id": "9c23109b008a8c00081239144766b25e5e098f8d",
          "message": "Merge pull request #341 from telicent-oss/dependabot/maven/maven-patch-group-6c1186fe36\n\nBump org.sonatype.central:central-publishing-maven-plugin from 0.9.0 to 0.10.0 in the maven-patch-group group",
          "timestamp": "2026-01-13T12:44:52Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/9c23109b008a8c00081239144766b25e5e098f8d"
        },
        "date": 1768818074199,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.SCGraphScenarioBenchmark.benchmarkGraphQL",
            "value": 0.8815318628499288,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGraphScenarioBenchmark.benchmarkSparqlSelect",
            "value": 1.0899789821945327,
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
          "id": "c288b71f581140df97930ce6ae42210f3996e99f",
          "message": "Merge pull request #349 from telicent-oss/release/0.93.0\n\nComplete Release 0.93.0",
          "timestamp": "2026-01-23T12:41:20Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/c288b71f581140df97930ce6ae42210f3996e99f"
        },
        "date": 1769422850997,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.SCGraphScenarioBenchmark.benchmarkGraphQL",
            "value": 0.7867860641044071,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGraphScenarioBenchmark.benchmarkSparqlSelect",
            "value": 0.7979903187942576,
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
          "id": "449e6f977e28e392659be6fd615ee9756c663b5d",
          "message": "Merge pull request #351 from telicent-oss/dependabot/docker/scg-docker/telicent/telicent-java21-1.2.40\n\nBump telicent/telicent-java21 from 1.2.39 to 1.2.40 in /scg-docker",
          "timestamp": "2026-01-29T07:49:07Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/449e6f977e28e392659be6fd615ee9756c663b5d"
        },
        "date": 1770028051143,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.SCGraphScenarioBenchmark.benchmarkGraphQL",
            "value": 0.5707892155900549,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGraphScenarioBenchmark.benchmarkSparqlSelect",
            "value": 0.9764797987428786,
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
          "id": "585d68f290598186cec7a9787bd3060c7c4de508",
          "message": "Merge pull request #352 from telicent-oss/dependabot/maven/maven-patch-group-640e564cb0\n\nBump the maven-patch-group group with 5 updates",
          "timestamp": "2026-02-03T12:02:26Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/585d68f290598186cec7a9787bd3060c7c4de508"
        },
        "date": 1770632998649,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.SCGraphScenarioBenchmark.benchmarkGraphQL",
            "value": 0.9333118367954617,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.SCGraphScenarioBenchmark.benchmarkSparqlSelect",
            "value": 1.0828054386023689,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      }
    ],
    "Labels Query Benchmark": [
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
          "id": "de216a089334f623ad999422c34150a95470aa08",
          "message": "Merge pull request #340 from telicent-oss/dependabot/maven/maven-patch-group-f90304635c\n\nBump the maven-patch-group group with 6 updates",
          "timestamp": "2026-01-07T17:33:30Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/de216a089334f623ad999422c34150a95470aa08"
        },
        "date": 1768210107621,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.LabelsQueryServiceBenchmark.benchmarkQueryDSGAndLabelStore ( {\"tripleCount\":\"1000\"} )",
            "value": 0.3889342990342751,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelsQueryServiceBenchmark.benchmarkQueryDSGAndLabelStore ( {\"tripleCount\":\"10000\"} )",
            "value": 4.547626909215717,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelsQueryServiceBenchmark.benchmarkQueryOnlyLabelStore ( {\"tripleCount\":\"1000\"} )",
            "value": 0.00001809869533720652,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelsQueryServiceBenchmark.benchmarkQueryOnlyLabelStore ( {\"tripleCount\":\"10000\"} )",
            "value": 0.00001819641804287701,
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
          "id": "9c23109b008a8c00081239144766b25e5e098f8d",
          "message": "Merge pull request #341 from telicent-oss/dependabot/maven/maven-patch-group-6c1186fe36\n\nBump org.sonatype.central:central-publishing-maven-plugin from 0.9.0 to 0.10.0 in the maven-patch-group group",
          "timestamp": "2026-01-13T12:44:52Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/9c23109b008a8c00081239144766b25e5e098f8d"
        },
        "date": 1768814934430,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.LabelsQueryServiceBenchmark.benchmarkQueryDSGAndLabelStore ( {\"tripleCount\":\"1000\"} )",
            "value": 0.379969160788353,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelsQueryServiceBenchmark.benchmarkQueryDSGAndLabelStore ( {\"tripleCount\":\"10000\"} )",
            "value": 5.058201063218297,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelsQueryServiceBenchmark.benchmarkQueryOnlyLabelStore ( {\"tripleCount\":\"1000\"} )",
            "value": 0.000017995274903804046,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelsQueryServiceBenchmark.benchmarkQueryOnlyLabelStore ( {\"tripleCount\":\"10000\"} )",
            "value": 0.000018103380782969317,
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
          "id": "c288b71f581140df97930ce6ae42210f3996e99f",
          "message": "Merge pull request #349 from telicent-oss/release/0.93.0\n\nComplete Release 0.93.0",
          "timestamp": "2026-01-23T12:41:20Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/c288b71f581140df97930ce6ae42210f3996e99f"
        },
        "date": 1769419704930,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.LabelsQueryServiceBenchmark.benchmarkQueryDSGAndLabelStore ( {\"tripleCount\":\"1000\"} )",
            "value": 0.3862696769957292,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelsQueryServiceBenchmark.benchmarkQueryDSGAndLabelStore ( {\"tripleCount\":\"10000\"} )",
            "value": 4.803613651467739,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelsQueryServiceBenchmark.benchmarkQueryOnlyLabelStore ( {\"tripleCount\":\"1000\"} )",
            "value": 0.000018025392265044436,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelsQueryServiceBenchmark.benchmarkQueryOnlyLabelStore ( {\"tripleCount\":\"10000\"} )",
            "value": 0.000018350944180597143,
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
          "id": "449e6f977e28e392659be6fd615ee9756c663b5d",
          "message": "Merge pull request #351 from telicent-oss/dependabot/docker/scg-docker/telicent/telicent-java21-1.2.40\n\nBump telicent/telicent-java21 from 1.2.39 to 1.2.40 in /scg-docker",
          "timestamp": "2026-01-29T07:49:07Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/449e6f977e28e392659be6fd615ee9756c663b5d"
        },
        "date": 1770024901196,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.LabelsQueryServiceBenchmark.benchmarkQueryDSGAndLabelStore ( {\"tripleCount\":\"1000\"} )",
            "value": 0.39542625070413745,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelsQueryServiceBenchmark.benchmarkQueryDSGAndLabelStore ( {\"tripleCount\":\"10000\"} )",
            "value": 5.483151964330358,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelsQueryServiceBenchmark.benchmarkQueryOnlyLabelStore ( {\"tripleCount\":\"1000\"} )",
            "value": 0.000018109728783342423,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelsQueryServiceBenchmark.benchmarkQueryOnlyLabelStore ( {\"tripleCount\":\"10000\"} )",
            "value": 0.000018179286268281595,
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
          "id": "585d68f290598186cec7a9787bd3060c7c4de508",
          "message": "Merge pull request #352 from telicent-oss/dependabot/maven/maven-patch-group-640e564cb0\n\nBump the maven-patch-group group with 5 updates",
          "timestamp": "2026-02-03T12:02:26Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/585d68f290598186cec7a9787bd3060c7c4de508"
        },
        "date": 1770629856089,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.LabelsQueryServiceBenchmark.benchmarkQueryDSGAndLabelStore ( {\"tripleCount\":\"1000\"} )",
            "value": 0.3713453082202301,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelsQueryServiceBenchmark.benchmarkQueryDSGAndLabelStore ( {\"tripleCount\":\"10000\"} )",
            "value": 4.886445587173258,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelsQueryServiceBenchmark.benchmarkQueryOnlyLabelStore ( {\"tripleCount\":\"1000\"} )",
            "value": 0.00001791158706479941,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.LabelsQueryServiceBenchmark.benchmarkQueryOnlyLabelStore ( {\"tripleCount\":\"10000\"} )",
            "value": 0.000018222170303143047,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      }
    ],
    "Access Query Benchmark": [
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
          "id": "de216a089334f623ad999422c34150a95470aa08",
          "message": "Merge pull request #340 from telicent-oss/dependabot/maven/maven-patch-group-f90304635c\n\nBump the maven-patch-group group with 6 updates",
          "timestamp": "2026-01-07T17:33:30Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/de216a089334f623ad999422c34150a95470aa08"
        },
        "date": 1768210377075,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.AccessQueryServiceBenchmark.benchmarkGetTriples ( {\"tripleCount\":\"1000\"} )",
            "value": 0.5006286132469467,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.AccessQueryServiceBenchmark.benchmarkGetTriples ( {\"tripleCount\":\"10000\"} )",
            "value": 3.9870456731045385,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.AccessQueryServiceBenchmark.benchmarkVisibleTriplesCount ( {\"tripleCount\":\"1000\"} )",
            "value": 0.34304852400401714,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.AccessQueryServiceBenchmark.benchmarkVisibleTriplesCount ( {\"tripleCount\":\"10000\"} )",
            "value": 2.9147349764190613,
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
          "id": "9c23109b008a8c00081239144766b25e5e098f8d",
          "message": "Merge pull request #341 from telicent-oss/dependabot/maven/maven-patch-group-6c1186fe36\n\nBump org.sonatype.central:central-publishing-maven-plugin from 0.9.0 to 0.10.0 in the maven-patch-group group",
          "timestamp": "2026-01-13T12:44:52Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/9c23109b008a8c00081239144766b25e5e098f8d"
        },
        "date": 1768815203560,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.AccessQueryServiceBenchmark.benchmarkGetTriples ( {\"tripleCount\":\"1000\"} )",
            "value": 0.5180481355089497,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.AccessQueryServiceBenchmark.benchmarkGetTriples ( {\"tripleCount\":\"10000\"} )",
            "value": 4.397679841794281,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.AccessQueryServiceBenchmark.benchmarkVisibleTriplesCount ( {\"tripleCount\":\"1000\"} )",
            "value": 0.3743802844682347,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.AccessQueryServiceBenchmark.benchmarkVisibleTriplesCount ( {\"tripleCount\":\"10000\"} )",
            "value": 2.850263640819095,
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
          "id": "c288b71f581140df97930ce6ae42210f3996e99f",
          "message": "Merge pull request #349 from telicent-oss/release/0.93.0\n\nComplete Release 0.93.0",
          "timestamp": "2026-01-23T12:41:20Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/c288b71f581140df97930ce6ae42210f3996e99f"
        },
        "date": 1769419974956,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.AccessQueryServiceBenchmark.benchmarkGetTriples ( {\"tripleCount\":\"1000\"} )",
            "value": 0.466505296419356,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.AccessQueryServiceBenchmark.benchmarkGetTriples ( {\"tripleCount\":\"10000\"} )",
            "value": 3.873324847650797,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.AccessQueryServiceBenchmark.benchmarkVisibleTriplesCount ( {\"tripleCount\":\"1000\"} )",
            "value": 0.3319616044114955,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.AccessQueryServiceBenchmark.benchmarkVisibleTriplesCount ( {\"tripleCount\":\"10000\"} )",
            "value": 2.712289248574418,
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
          "id": "449e6f977e28e392659be6fd615ee9756c663b5d",
          "message": "Merge pull request #351 from telicent-oss/dependabot/docker/scg-docker/telicent/telicent-java21-1.2.40\n\nBump telicent/telicent-java21 from 1.2.39 to 1.2.40 in /scg-docker",
          "timestamp": "2026-01-29T07:49:07Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/449e6f977e28e392659be6fd615ee9756c663b5d"
        },
        "date": 1770025170834,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.AccessQueryServiceBenchmark.benchmarkGetTriples ( {\"tripleCount\":\"1000\"} )",
            "value": 0.5146382212392762,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.AccessQueryServiceBenchmark.benchmarkGetTriples ( {\"tripleCount\":\"10000\"} )",
            "value": 3.822453806152513,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.AccessQueryServiceBenchmark.benchmarkVisibleTriplesCount ( {\"tripleCount\":\"1000\"} )",
            "value": 0.37066392238228757,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.AccessQueryServiceBenchmark.benchmarkVisibleTriplesCount ( {\"tripleCount\":\"10000\"} )",
            "value": 2.882942925853098,
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
          "id": "585d68f290598186cec7a9787bd3060c7c4de508",
          "message": "Merge pull request #352 from telicent-oss/dependabot/maven/maven-patch-group-640e564cb0\n\nBump the maven-patch-group group with 5 updates",
          "timestamp": "2026-02-03T12:02:26Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/585d68f290598186cec7a9787bd3060c7c4de508"
        },
        "date": 1770630125618,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.AccessQueryServiceBenchmark.benchmarkGetTriples ( {\"tripleCount\":\"1000\"} )",
            "value": 0.5712088853938365,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.AccessQueryServiceBenchmark.benchmarkGetTriples ( {\"tripleCount\":\"10000\"} )",
            "value": 4.34202787705942,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.AccessQueryServiceBenchmark.benchmarkVisibleTriplesCount ( {\"tripleCount\":\"1000\"} )",
            "value": 0.36789947320904937,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.AccessQueryServiceBenchmark.benchmarkVisibleTriplesCount ( {\"tripleCount\":\"10000\"} )",
            "value": 2.7835943819500044,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      }
    ],
    "Backup Restore Benchmark": [
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
          "id": "de216a089334f623ad999422c34150a95470aa08",
          "message": "Merge pull request #340 from telicent-oss/dependabot/maven/maven-patch-group-f90304635c\n\nBump the maven-patch-group group with 6 updates",
          "timestamp": "2026-01-07T17:33:30Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/de216a089334f623ad999422c34150a95470aa08"
        },
        "date": 1768210527624,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.BackupRestoreBenchmark.benchmarkBackupDataset ( {\"tripleCount\":\"1000\"} )",
            "value": 11.815953898102308,
            "unit": "ms/op",
            "extra": "iterations: 3\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.BackupRestoreBenchmark.benchmarkBackupDataset ( {\"tripleCount\":\"10000\"} )",
            "value": 37.58644749845269,
            "unit": "ms/op",
            "extra": "iterations: 3\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.BackupRestoreBenchmark.benchmarkRestoreDataset ( {\"tripleCount\":\"1000\"} )",
            "value": 38.71366890125642,
            "unit": "ms/op",
            "extra": "iterations: 3\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.BackupRestoreBenchmark.benchmarkRestoreDataset ( {\"tripleCount\":\"10000\"} )",
            "value": 147.46306096447856,
            "unit": "ms/op",
            "extra": "iterations: 3\nforks: 1\nthreads: 1"
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
          "id": "9c23109b008a8c00081239144766b25e5e098f8d",
          "message": "Merge pull request #341 from telicent-oss/dependabot/maven/maven-patch-group-6c1186fe36\n\nBump org.sonatype.central:central-publishing-maven-plugin from 0.9.0 to 0.10.0 in the maven-patch-group group",
          "timestamp": "2026-01-13T12:44:52Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/9c23109b008a8c00081239144766b25e5e098f8d"
        },
        "date": 1768815352872,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.BackupRestoreBenchmark.benchmarkBackupDataset ( {\"tripleCount\":\"1000\"} )",
            "value": 10.276790833607512,
            "unit": "ms/op",
            "extra": "iterations: 3\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.BackupRestoreBenchmark.benchmarkBackupDataset ( {\"tripleCount\":\"10000\"} )",
            "value": 34.13173466360197,
            "unit": "ms/op",
            "extra": "iterations: 3\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.BackupRestoreBenchmark.benchmarkRestoreDataset ( {\"tripleCount\":\"1000\"} )",
            "value": 36.81430873193556,
            "unit": "ms/op",
            "extra": "iterations: 3\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.BackupRestoreBenchmark.benchmarkRestoreDataset ( {\"tripleCount\":\"10000\"} )",
            "value": 146.30608693719807,
            "unit": "ms/op",
            "extra": "iterations: 3\nforks: 1\nthreads: 1"
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
          "id": "c288b71f581140df97930ce6ae42210f3996e99f",
          "message": "Merge pull request #349 from telicent-oss/release/0.93.0\n\nComplete Release 0.93.0",
          "timestamp": "2026-01-23T12:41:20Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/c288b71f581140df97930ce6ae42210f3996e99f"
        },
        "date": 1769420126159,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.BackupRestoreBenchmark.benchmarkBackupDataset ( {\"tripleCount\":\"1000\"} )",
            "value": 13.447315088781558,
            "unit": "ms/op",
            "extra": "iterations: 3\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.BackupRestoreBenchmark.benchmarkBackupDataset ( {\"tripleCount\":\"10000\"} )",
            "value": 39.27560119378146,
            "unit": "ms/op",
            "extra": "iterations: 3\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.BackupRestoreBenchmark.benchmarkRestoreDataset ( {\"tripleCount\":\"1000\"} )",
            "value": 49.82216620559462,
            "unit": "ms/op",
            "extra": "iterations: 3\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.BackupRestoreBenchmark.benchmarkRestoreDataset ( {\"tripleCount\":\"10000\"} )",
            "value": 159.61173303174604,
            "unit": "ms/op",
            "extra": "iterations: 3\nforks: 1\nthreads: 1"
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
          "id": "449e6f977e28e392659be6fd615ee9756c663b5d",
          "message": "Merge pull request #351 from telicent-oss/dependabot/docker/scg-docker/telicent/telicent-java21-1.2.40\n\nBump telicent/telicent-java21 from 1.2.39 to 1.2.40 in /scg-docker",
          "timestamp": "2026-01-29T07:49:07Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/449e6f977e28e392659be6fd615ee9756c663b5d"
        },
        "date": 1770025321352,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.BackupRestoreBenchmark.benchmarkBackupDataset ( {\"tripleCount\":\"1000\"} )",
            "value": 12.346165013009333,
            "unit": "ms/op",
            "extra": "iterations: 3\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.BackupRestoreBenchmark.benchmarkBackupDataset ( {\"tripleCount\":\"10000\"} )",
            "value": 38.86410836916695,
            "unit": "ms/op",
            "extra": "iterations: 3\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.BackupRestoreBenchmark.benchmarkRestoreDataset ( {\"tripleCount\":\"1000\"} )",
            "value": 40.59126975294425,
            "unit": "ms/op",
            "extra": "iterations: 3\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.BackupRestoreBenchmark.benchmarkRestoreDataset ( {\"tripleCount\":\"10000\"} )",
            "value": 149.7493807562189,
            "unit": "ms/op",
            "extra": "iterations: 3\nforks: 1\nthreads: 1"
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
          "id": "585d68f290598186cec7a9787bd3060c7c4de508",
          "message": "Merge pull request #352 from telicent-oss/dependabot/maven/maven-patch-group-640e564cb0\n\nBump the maven-patch-group group with 5 updates",
          "timestamp": "2026-02-03T12:02:26Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/585d68f290598186cec7a9787bd3060c7c4de508"
        },
        "date": 1770630275827,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.BackupRestoreBenchmark.benchmarkBackupDataset ( {\"tripleCount\":\"1000\"} )",
            "value": 10.995125964691368,
            "unit": "ms/op",
            "extra": "iterations: 3\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.BackupRestoreBenchmark.benchmarkBackupDataset ( {\"tripleCount\":\"10000\"} )",
            "value": 34.127608796107296,
            "unit": "ms/op",
            "extra": "iterations: 3\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.BackupRestoreBenchmark.benchmarkRestoreDataset ( {\"tripleCount\":\"1000\"} )",
            "value": 42.94090396565294,
            "unit": "ms/op",
            "extra": "iterations: 3\nforks: 1\nthreads: 1"
          },
          {
            "name": "io.telicent.core.BackupRestoreBenchmark.benchmarkRestoreDataset ( {\"tripleCount\":\"10000\"} )",
            "value": 152.11735493818784,
            "unit": "ms/op",
            "extra": "iterations: 3\nforks: 1\nthreads: 1"
          }
        ]
      }
    ],
    "SCG ABAC Persistent Scenario Benchmark": [
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
          "id": "de216a089334f623ad999422c34150a95470aa08",
          "message": "Merge pull request #340 from telicent-oss/dependabot/maven/maven-patch-group-f90304635c\n\nBump the maven-patch-group group with 6 updates",
          "timestamp": "2026-01-07T17:33:30Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/de216a089334f623ad999422c34150a95470aa08"
        },
        "date": 1768213323534,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.SCGraphAbacPersistentScenarioBenchmark.benchmarkAbacSparqlSelect",
            "value": 0.8721962461899956,
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
          "id": "9c23109b008a8c00081239144766b25e5e098f8d",
          "message": "Merge pull request #341 from telicent-oss/dependabot/maven/maven-patch-group-6c1186fe36\n\nBump org.sonatype.central:central-publishing-maven-plugin from 0.9.0 to 0.10.0 in the maven-patch-group group",
          "timestamp": "2026-01-13T12:44:52Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/9c23109b008a8c00081239144766b25e5e098f8d"
        },
        "date": 1768818143482,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.SCGraphAbacPersistentScenarioBenchmark.benchmarkAbacSparqlSelect",
            "value": 0.9301269765144552,
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
          "id": "c288b71f581140df97930ce6ae42210f3996e99f",
          "message": "Merge pull request #349 from telicent-oss/release/0.93.0\n\nComplete Release 0.93.0",
          "timestamp": "2026-01-23T12:41:20Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/c288b71f581140df97930ce6ae42210f3996e99f"
        },
        "date": 1769422921171,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.SCGraphAbacPersistentScenarioBenchmark.benchmarkAbacSparqlSelect",
            "value": 0.8107791283127185,
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
          "id": "449e6f977e28e392659be6fd615ee9756c663b5d",
          "message": "Merge pull request #351 from telicent-oss/dependabot/docker/scg-docker/telicent/telicent-java21-1.2.40\n\nBump telicent/telicent-java21 from 1.2.39 to 1.2.40 in /scg-docker",
          "timestamp": "2026-01-29T07:49:07Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/449e6f977e28e392659be6fd615ee9756c663b5d"
        },
        "date": 1770028120684,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.SCGraphAbacPersistentScenarioBenchmark.benchmarkAbacSparqlSelect",
            "value": 0.8584504137958087,
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
          "id": "585d68f290598186cec7a9787bd3060c7c4de508",
          "message": "Merge pull request #352 from telicent-oss/dependabot/maven/maven-patch-group-640e564cb0\n\nBump the maven-patch-group group with 5 updates",
          "timestamp": "2026-02-03T12:02:26Z",
          "url": "https://github.com/telicent-oss/smart-cache-graph/commit/585d68f290598186cec7a9787bd3060c7c4de508"
        },
        "date": 1770633068637,
        "tool": "jmh",
        "benches": [
          {
            "name": "io.telicent.core.SCGraphAbacPersistentScenarioBenchmark.benchmarkAbacSparqlSelect",
            "value": 0.9468231520747656,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      }
    ]
  }
}