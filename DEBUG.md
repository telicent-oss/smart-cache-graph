# Smart Cache Graph Debug / Perf Guide

This guide is for building Smart Cache Graph on top of the Telicent debug/perf
Java base images, pushing the resulting image to ECR, deploying it and then interrogating the running pod.

## Build Smart Cache Graph on the perf base

The Graph Dockerfile already supports overriding the Java base image with
`JAVA_BASE_IMAGE`.

Build the app:

```bash
mvn clean install
```

Then build and push the PERF variant (note the example tags 1.2.50 & 1.0.2):

```bash
TARGET_PLATFORMS=linux/amd64 \
EXTRA_BUILD_ARGS='--build-arg JAVA_BASE_IMAGE=098669589541.dkr.ecr.eu-west-2.amazonaws.com/telicent-java21-perf:1.2.50' \
./docker-build.sh 1.0.2-PERF 098669589541.dkr.ecr.eu-west-2.amazonaws.com
```

Expected image:

```text
098669589541.dkr.ecr.eu-west-2.amazonaws.com/smart-cache-graph:1.0.2-PERF
```

Notes:

- for multi-arch, set `TARGET_PLATFORMS=linux/amd64,linux/arm64`

## Deploy to SI

### Fast path: set `image.tag` directly

For temporary probe rollouts, the simplest route is to edit the SI values file:

- `k8s-manifests/deployments/CORE/graph/values-system-integration.yaml`

Example:

```yaml
image:
  registry: 098669589541.dkr.ecr.eu-west-2.amazonaws.com
  repository: smart-cache-graph
  tag: 1.0.2-PERF
```

Why this works:

- the chart resolves `image.tag` first
- if `image.tag` is unset it falls back to `Chart.appVersion`
- so for temporary debug/perf deployments you do not need to publish a new chart

Commit the values change in `k8s-manifests` and let ArgoCD reconcile.

### Normal promoted release path

For a real release instead of a temporary probe image:

1. publish the app image with the final release tag
2. publish the Helm chart with matching `appVersion`
3. update `chartVersion` in `deployments/CORE/graph/applicationSet.yaml`

## Recommended SI runtime additions

The perf image provides tools, but you still need a place to write artifacts.

Recommended overlay in `values-system-integration.yaml`:

```yaml
extraEnvVars:
  - name: PROFILE_DIR
    value: /tmp/telicent-profiles

extraVolumes:
  - name: telicent-profiles
    emptyDir: {}

extraVolumeMounts:
  - name: telicent-profiles
    mountPath: /tmp/telicent-profiles
```

Recommended JVM flags:

```yaml
java:
  jvmOptions: >-
    -XX:NativeMemoryTracking=summary
    -XX:+HeapDumpOnOutOfMemoryError
    -XX:HeapDumpPath=/tmp/telicent-profiles
    -Xlog:gc*:file=/tmp/telicent-profiles/gc.log:time,uptime,level,tags
```

If you want startup JFR:

```text
-XX:StartFlightRecording=filename=/tmp/telicent-profiles/startup.jfr,settings=profile
```

## Interrogate the running Graph pod

Find the pod:

```bash
kubectl -n tc-core get pods | rg smart-cache-graph
```

Open a shell:

```bash
kubectl -n tc-core exec -it <graph-pod> -- sh
```

### Basic health and runtime checks

```bash
curl -s http://localhost:3030/$/ping
ps -ef
env | sort | rg 'JAVA|MALLOC|OTEL|KAFKA|JWKS|USERINFO'
ss -tulpn
```

Useful Graph-specific checks:

```bash
curl -s http://localhost:3030/$/stats
curl -s http://localhost:3030/$/server
```

## JVM-first diagnostics

These are the safest first-line diagnostics and do not usually require extra pod
privileges:

```bash
jcmd 1 VM.flags
jcmd 1 VM.command_line
jcmd 1 VM.native_memory summary
jcmd 1 Thread.print > /tmp/telicent-profiles/thread-dump.txt
jcmd 1 GC.class_histogram > /tmp/telicent-profiles/class-histogram.txt
jcmd 1 JFR.start name=on_demand settings=profile filename=/tmp/telicent-profiles/live.jfr duration=300s
```

Useful follow-up:

```bash
jcmd 1 GC.heap_dump /tmp/telicent-profiles/heap.hprof
```

For Graph specifically, `VM.native_memory summary` is useful when comparing:

- RocksDB growth
- direct memory
- thread stack growth
- malloc arena effects

## OS-level tools in the perf image

The perf image extends the debug image with:

- `sysstat`
- `kernel-tools` when available

Typical commands:

```bash
top -H -p 1
mpstat -P ALL 1 5
iostat -xz 1 5
lsof -p 1 | head
```

## Copy artifacts out

```bash
kubectl -n tc-core cp <graph-pod>:/tmp/telicent-profiles ./graph-profiles
```

## `strace`, `gdb`, and `perf` caveat

The chart defaults to a locked-down security context:

- `runAsNonRoot: true`
- `allowPrivilegeEscalation: false`
- `capabilities.drop: [ALL]`
- `seccompProfile.type: RuntimeDefault`

Because of that, these may fail until you temporarily relax security:

- `strace -p 1`
- `gdb -p 1`
- `perf record -p 1 ...`

If cluster policy permits it, use a temporary override:

```yaml
containerSecurityContext:
  runAsUser: 185
  runAsGroup: 185
  runAsNonRoot: true
  allowPrivilegeEscalation: true
  capabilities:
    add:
      - SYS_PTRACE
      - SYS_ADMIN
  seccompProfile:
    type: Unconfined
```

Even then:

- `perf` may still be blocked by host kernel settings
- JFR and `jcmd` are normally the better first choice in SI
