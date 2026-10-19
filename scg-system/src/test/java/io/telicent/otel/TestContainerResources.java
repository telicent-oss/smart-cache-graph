/*
 *  Copyright (c) Telicent Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.telicent.otel;

import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TestContainerResources {

    private static final String V2_MEMORY_STAT = """
            anon 1184509952
            file 5337333760
            file_mapped 4687843328
            file_dirty 12288
            file_writeback 4096
            pgmajfault 264712
            workingset_refault_file 6744609
            """;

    private static Path cgroupV2(Path root, String memoryMax) throws IOException {
        Files.writeString(root.resolve("memory.stat"), V2_MEMORY_STAT);
        Files.writeString(root.resolve("memory.max"), memoryMax + "\n");
        Files.writeString(root.resolve("memory.current"), "6775279616\n");
        Files.writeString(root.resolve("io.stat"), """
                259:0 rbytes=1000 wbytes=2000 rios=1 wios=2 dbytes=0 dios=0
                259:1 rbytes=30 wbytes=400 rios=1 wios=2 dbytes=0 dios=0
                """);
        return root;
    }

    @Test
    void givenCgroupV2_whenReading_thenAllFiguresParsed(@TempDir Path root) throws IOException {
        // Given
        ContainerResources resources = ContainerResources.detect(cgroupV2(root, "10737418240")).orElseThrow();

        // When
        ContainerResources.Snapshot snapshot = resources.read();

        // Then
        assertEquals("v2", resources.version());
        assertEquals(1030, snapshot.readBytes());
        assertEquals(2400, snapshot.writeBytes());
        assertEquals(5337333760L, snapshot.pageCache());
        assertEquals(12288, snapshot.dirty());
        assertEquals(4096, snapshot.writeback());
        assertEquals(1184509952L, snapshot.anon());
        assertEquals(10737418240L, snapshot.limit());
        assertEquals(6775279616L, snapshot.usage());
        assertEquals(264712, snapshot.majorFaults());
        assertEquals(6744609, snapshot.refaults());
    }

    @Test
    void givenCgroupV2WithoutLimitOrIoStat_whenReading_thenThoseAreUnknown(@TempDir Path root) throws IOException {
        // Given
        cgroupV2(root, "max");
        Files.delete(root.resolve("io.stat"));

        // When
        ContainerResources.Snapshot snapshot = ContainerResources.detect(root).orElseThrow().read();

        // Then
        assertEquals(ContainerResources.UNKNOWN, snapshot.limit());
        assertEquals(ContainerResources.UNKNOWN, snapshot.readBytes());
        assertEquals(ContainerResources.UNKNOWN, snapshot.writeBytes());
        assertEquals(5337333760L, snapshot.pageCache());
    }

    @Test
    void givenOlderKernelRefaultName_whenReading_thenUsed(@TempDir Path root) throws IOException {
        cgroupV2(root, "max");
        Files.writeString(root.resolve("memory.stat"), "file 10\nworkingset_refault 42\ngarbage line here\nanon x\n");

        ContainerResources.Snapshot snapshot = ContainerResources.detect(root).orElseThrow().read();

        assertEquals(42, snapshot.refaults());
        assertEquals(10, snapshot.pageCache());
        assertEquals(ContainerResources.UNKNOWN, snapshot.anon(), "Unparseable values are unknown");
    }

    @Test
    void givenCgroupV1_whenReading_thenTotalsPreferredAndNoLimitUnknown(@TempDir Path root) throws IOException {
        // Given
        Path memory = Files.createDirectories(root.resolve("memory"));
        Path blkio = Files.createDirectories(root.resolve("blkio"));
        Files.writeString(memory.resolve("memory.stat"), """
                cache 100
                rss 50
                dirty 5
                writeback 1
                pgmajfault 7
                total_cache 200
                total_rss 60
                total_dirty 6
                total_writeback 2
                total_pgmajfault 9
                """);
        Files.writeString(memory.resolve("memory.limit_in_bytes"), "9223372036854771712\n");
        Files.writeString(memory.resolve("memory.usage_in_bytes"), "300\n");
        Files.writeString(blkio.resolve("blkio.throttle.io_service_bytes"), """
                8:0 Read 10
                8:0 Write 20
                8:0 Sync 25
                8:0 Total 30
                8:16 Read 1
                8:16 Write 2
                Total 33
                """);

        // When
        ContainerResources resources = ContainerResources.detect(root).orElseThrow();
        ContainerResources.Snapshot snapshot = resources.read();

        // Then
        assertEquals("v1", resources.version());
        assertEquals(11, snapshot.readBytes());
        assertEquals(22, snapshot.writeBytes());
        assertEquals(200, snapshot.pageCache());
        assertEquals(60, snapshot.anon());
        assertEquals(6, snapshot.dirty());
        assertEquals(2, snapshot.writeback());
        assertEquals(9, snapshot.majorFaults());
        assertEquals(ContainerResources.UNKNOWN, snapshot.limit());
        assertEquals(300, snapshot.usage());
        assertEquals(ContainerResources.UNKNOWN, snapshot.refaults());
    }

    @Test
    void givenNoCgroup_whenDetecting_thenEmpty(@TempDir Path root) {
        assertTrue(ContainerResources.detect(root).isEmpty());
    }

    @Test
    void givenCgroupV2_whenMetricsCollected_thenRecordedUnderExpectedNames(@TempDir Path root) throws IOException {
        // Given
        ContainerResources resources = ContainerResources.detect(cgroupV2(root, "max")).orElseThrow();
        InMemoryMetricReader reader = InMemoryMetricReader.create();
        SdkMeterProvider provider = SdkMeterProvider.builder().registerMetricReader(reader).build();
        try {
            // When
            FMod_OpenTelemetry.buildContainerMetrics(provider.get("test"), resources);
            Map<String, MetricData> metrics = reader.collectAllMetrics()
                                                    .stream()
                                                    .collect(Collectors.toMap(MetricData::getName, m -> m));

            // Then
            assertEquals(1030, value(metrics, FMod_OpenTelemetry.CONTAINER_IO_READ));
            assertEquals(2400, value(metrics, FMod_OpenTelemetry.CONTAINER_IO_WRITE));
            assertEquals(5337333760L, value(metrics, FMod_OpenTelemetry.CONTAINER_PAGE_CACHE));
            assertEquals(12288, value(metrics, FMod_OpenTelemetry.CONTAINER_DIRTY));
            assertEquals(4096, value(metrics, FMod_OpenTelemetry.CONTAINER_WRITEBACK));
            assertEquals(1184509952L, value(metrics, FMod_OpenTelemetry.CONTAINER_ANON));
            assertEquals(6775279616L, value(metrics, FMod_OpenTelemetry.CONTAINER_USAGE));
            assertEquals(264712, value(metrics, FMod_OpenTelemetry.CONTAINER_MAJOR_FAULTS));
            assertEquals(6744609, value(metrics, FMod_OpenTelemetry.CONTAINER_REFAULTS));
            // Units drive the exported Prometheus names (e.g. _bytes_total for the I/O counters)
            assertEquals("bytes", metrics.get(FMod_OpenTelemetry.CONTAINER_IO_WRITE).getUnit());
            assertEquals("bytes", metrics.get(FMod_OpenTelemetry.CONTAINER_PAGE_CACHE).getUnit());
            // Counters are monotonic sums, so Prometheus treats them as counters
            assertTrue(metrics.get(FMod_OpenTelemetry.CONTAINER_IO_WRITE).getLongSumData().isMonotonic());
            // No limit set, so nothing recorded rather than a bogus value
            assertTrue(metrics.get(FMod_OpenTelemetry.CONTAINER_LIMIT) == null
                       || metrics.get(FMod_OpenTelemetry.CONTAINER_LIMIT).getLongGaugeData().getPoints().isEmpty());
        } finally {
            provider.shutdown().join(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void givenSnapshotCache_whenCollectedTwiceQuickly_thenFilesReadOnce(@TempDir Path root) throws IOException {
        // Given
        ContainerResources resources = ContainerResources.detect(cgroupV2(root, "max")).orElseThrow();
        InMemoryMetricReader reader = InMemoryMetricReader.create();
        SdkMeterProvider provider = SdkMeterProvider.builder().registerMetricReader(reader).build();
        try {
            FMod_OpenTelemetry.buildContainerMetrics(provider.get("test"), resources);
            reader.collectAllMetrics();

            // When -- the files change straight away
            Files.writeString(root.resolve("io.stat"), "259:0 rbytes=1 wbytes=9999999\n");
            Map<String, MetricData> metrics = reader.collectAllMetrics()
                                                    .stream()
                                                    .collect(Collectors.toMap(MetricData::getName, m -> m));

            // Then -- still the cached reading (refreshed at most once a second)
            assertEquals(2400, value(metrics, FMod_OpenTelemetry.CONTAINER_IO_WRITE));
        } finally {
            provider.shutdown().join(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void givenUnreadableIoFiles_whenReading_thenUnknownInsteadOfCounterReset(@TempDir Path root) throws IOException {
        // A directory passes Files.isReadable but cannot be read as a cgroup statistics file.
        assertArrayEquals(new long[] { ContainerResources.UNKNOWN, ContainerResources.UNKNOWN },
                          ContainerResources.readIoStatV2(root));
        assertArrayEquals(new long[] { ContainerResources.UNKNOWN, ContainerResources.UNKNOWN },
                          ContainerResources.readIoServiceBytesV1(root));
        Path empty = Files.createFile(root.resolve("empty"));
        assertArrayEquals(new long[] { 0, 0 }, ContainerResources.readIoStatV2(empty));
        assertArrayEquals(new long[] { 0, 0 }, ContainerResources.readIoServiceBytesV1(empty));
    }

    @Test
    void givenNestedCgroupV2_whenDetecting_thenProcessMetricsUsed(@TempDir Path root) throws IOException {
        cgroupV2(root, "100");
        Path member = cgroupV2(Files.createDirectories(root.resolve("system.slice/graph")), "200");
        Files.writeString(member.resolve("memory.current"), "42\n");
        ContainerResources resources = ContainerResources.detect(root, List.of("0::/system.slice/graph"))
                                                          .orElseThrow();
        assertEquals(200, resources.read().limit());
        assertEquals(42, resources.read().usage());
    }

    @Test
    void givenSeparateV1ControllerMemberships_whenDetecting_thenBothResolved(@TempDir Path root) throws IOException {
        Path memory = Files.createDirectories(root.resolve("memory/graph"));
        Files.writeString(memory.resolve("memory.stat"), "cache 42\n");
        Path io = Files.createDirectories(root.resolve("blkio/other"));
        Files.writeString(io.resolve("blkio.throttle.io_service_bytes"), "8:0 Write 123\n");
        ContainerResources resources = ContainerResources.detect(root, List.of(
                "5:memory:/graph", "4:blkio:/other")).orElseThrow();
        assertEquals(42, resources.read().pageCache());
        assertEquals(123, resources.read().writeBytes());
    }

    @Test
    void givenCgroupMountAlreadyRootedAtContainer_whenDetecting_thenMountUsed(@TempDir Path root) throws IOException {
        cgroupV2(root, "200");
        ContainerResources resources = ContainerResources.detect(root, List.of("0::/unexposed/graph"))
                                                          .orElseThrow();
        assertEquals(200, resources.read().limit());
    }

    private static long value(Map<String, MetricData> metrics, String name) {
        MetricData metric = metrics.get(name);
        assertNotNull(metric, name + " not recorded");
        Collection<? extends LongPointData> points = switch (metric.getType()) {
            case LONG_SUM -> metric.getLongSumData().getPoints();
            case LONG_GAUGE -> metric.getLongGaugeData().getPoints();
            default -> throw new AssertionError("Unexpected type " + metric.getType() + " for " + name);
        };
        assertEquals(1, points.size(), name);
        return points.iterator().next().getValue();
    }
}
