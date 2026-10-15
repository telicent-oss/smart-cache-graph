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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads this process's container memory and disk I/O figures from its cgroup (v2, or v1 as a fallback).
 * <p>
 * These are the figures that matter for TDB2: its files are memory-mapped, so it relies on the page cache, and in a
 * container the page cache counts towards the memory limit. During a compaction, a page cache squeezed by the limit
 * shows up as disk writes far exceeding the size of the compacted copy (dirty pages are flushed again and again).
 * </p>
 * <p>
 * Reading never throws: any figure that can't be read is reported as {@link #UNKNOWN}.
 * </p>
 */
@SuppressWarnings("java:S1192")
public final class ContainerResources {

    /**
     * Value of any figure that isn't available
     */
    public static final long UNKNOWN = -1;
    /**
     * cgroup v1 reports "no limit" as a very large number rather than {@code max}
     */
    private static final long V1_NO_LIMIT_THRESHOLD = 1L << 60;

    /**
     * A point-in-time reading. I/O byte counts and fault counts are cumulative since the container started.
     *
     * @param readBytes   Bytes read from block devices
     * @param writeBytes  Bytes written to block devices
     * @param pageCache   Page cache charged to the container (memory-mapped and other file pages)
     * @param dirty       Page cache waiting to be written to disk
     * @param writeback   Page cache currently being written to disk
     * @param anon        Anonymous memory, essentially the JVM (heap, metaspace, threads, direct buffers)
     * @param limit       Memory limit, or {@link #UNKNOWN} if there isn't one
     * @param usage       Total memory charged to the container, including page cache
     * @param majorFaults Page faults that needed a disk read
     * @param refaults    Evicted page cache pages that were needed again
     */
    public record Snapshot(long readBytes, long writeBytes, long pageCache, long dirty, long writeback, long anon,
                           long limit, long usage, long majorFaults, long refaults) {
    }

    private enum Version {V1, V2}

    private final Path memoryRoot;
    private final Path ioRoot;
    private final Version version;

    private ContainerResources(Path memoryRoot, Path ioRoot, Version version) {
        this.memoryRoot = memoryRoot;
        this.ioRoot = ioRoot;
        this.version = version;
    }

    /**
     * Detects the cgroup this process is in
     *
     * @return Container resources, or empty if no cgroup memory accounting is available (e.g. not on Linux)
     */
    public static Optional<ContainerResources> detect() {
        return detect(Path.of("/sys/fs/cgroup"), readLines(Path.of("/proc/self/cgroup")));
    }

    /**
     * Detects the cgroup layout under the given root
     *
     * @param root cgroup filesystem root, normally {@code /sys/fs/cgroup}
     * @return Container resources, or empty if neither a v2 nor a v1 memory controller is found
     */
    static Optional<ContainerResources> detect(Path root) {
        return detect(root, List.of());
    }

    static Optional<ContainerResources> detect(Path root, List<String> membership) {
        Path unified = root;
        Path memory = root.resolve("memory");
        Path io = root.resolve("blkio");
        for (String line : membership) {
            String[] fields = line.split(":", 3);
            if (fields.length != 3 || !fields[2].startsWith("/")) {
                continue;
            }
            if (fields[1].isEmpty()) {
                unified = memberRoot(root, fields[2], "memory.stat");
            } else {
                List<String> controllers = List.of(fields[1].split(","));
                if (controllers.contains("memory")) {
                    memory = memberRoot(root.resolve("memory"), fields[2], "memory.stat");
                }
                if (controllers.contains("blkio")) {
                    io = memberRoot(root.resolve("blkio"), fields[2], "blkio.throttle.io_service_bytes");
                }
            }
        }
        if (Files.isReadable(unified.resolve("memory.stat"))) {
            return Optional.of(new ContainerResources(unified, unified, Version.V2));
        }
        if (Files.isReadable(memory.resolve("memory.stat"))) {
            return Optional.of(new ContainerResources(memory, io, Version.V1));
        }
        return Optional.empty();
    }

    private static Path memberRoot(Path mount, String membership, String accountingFile) {
        Path member = mount.resolve(membership.substring(1)).normalize();
        // In a private cgroup namespace the mount may already be rooted at the process's cgroup.
        return member.startsWith(mount) && Files.isReadable(member.resolve(accountingFile)) ? member : mount;
    }

    /**
     * @return The cgroup version in use, {@code v1} or {@code v2}
     */
    public String version() {
        return this.version == Version.V2 ? "v2" : "v1";
    }

    /**
     * Takes a reading
     *
     * @return Snapshot
     */
    public Snapshot read() {
        return this.version == Version.V2 ? readV2() : readV1();
    }

    private Snapshot readV2() {
        Map<String, Long> stat = readKeyValues(this.memoryRoot.resolve("memory.stat"));
        long[] io = readIoStatV2(this.ioRoot.resolve("io.stat"));
        long refaults = stat.getOrDefault("workingset_refault_file", stat.getOrDefault("workingset_refault", UNKNOWN));
        return new Snapshot(io[0], io[1], stat.getOrDefault("file", UNKNOWN), stat.getOrDefault("file_dirty", UNKNOWN),
                            stat.getOrDefault("file_writeback", UNKNOWN), stat.getOrDefault("anon", UNKNOWN),
                            readLimit(this.memoryRoot.resolve("memory.max")), readLong(this.memoryRoot.resolve("memory.current")),
                            stat.getOrDefault("pgmajfault", UNKNOWN), refaults);
    }

    private Snapshot readV1() {
        Path memory = this.memoryRoot;
        Map<String, Long> stat = readKeyValues(memory.resolve("memory.stat"));
        long[] io = readIoServiceBytesV1(this.ioRoot.resolve("blkio.throttle.io_service_bytes"));
        long limit = readLong(memory.resolve("memory.limit_in_bytes"));
        return new Snapshot(io[0], io[1], v1(stat, "cache"), v1(stat, "dirty"), v1(stat, "writeback"), v1(stat, "rss"),
                            limit >= V1_NO_LIMIT_THRESHOLD ? UNKNOWN : limit,
                            readLong(memory.resolve("memory.usage_in_bytes")), v1(stat, "pgmajfault"),
                            v1(stat, "workingset_refault"));
    }

    /**
     * cgroup v1 reports a figure both for the cgroup itself and, prefixed {@code total_}, including its descendants;
     * prefer the latter
     */
    private static long v1(Map<String, Long> stat, String key) {
        return stat.getOrDefault("total_" + key, stat.getOrDefault(key, UNKNOWN));
    }

    /**
     * Parses {@code key value} lines, as in {@code memory.stat}
     */
    static Map<String, Long> readKeyValues(Path file) {
        Map<String, Long> values = new HashMap<>();
        for (String line : readLines(file)) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 2) {
                parseLong(parts[1]).ifPresent(v -> values.put(parts[0], v));
            }
        }
        return values;
    }

    /**
     * Sums {@code rbytes} and {@code wbytes} over all devices in a cgroup v2 {@code io.stat}, whose lines look like
     * {@code 259:0 rbytes=123 wbytes=456 rios=7 wios=8 dbytes=0 dios=0}
     *
     * @return Read and write bytes, or {@link #UNKNOWN} if the file can't be read
     */
    static long[] readIoStatV2(Path file) {
        try {
            long read = 0;
            long write = 0;
            for (String line : Files.readAllLines(file)) {
                for (String field : line.trim().split("\\s+")) {
                    if (field.startsWith("rbytes=")) {
                        read += parseLong(field.substring(7)).orElse(0L);
                    } else if (field.startsWith("wbytes=")) {
                        write += parseLong(field.substring(7)).orElse(0L);
                    }
                }
            }
            return new long[] { read, write };
        } catch (IOException | RuntimeException e) {
            return new long[] { UNKNOWN, UNKNOWN };
        }
    }

    /**
     * Sums reads and writes over all devices in a cgroup v1 {@code blkio.throttle.io_service_bytes}, whose lines look
     * like {@code 8:0 Read 123} (with a {@code Total} line at the end, which is ignored)
     *
     * @return Read and write bytes, or {@link #UNKNOWN} if the file can't be read
     */
    static long[] readIoServiceBytesV1(Path file) {
        try {
            long read = 0;
            long write = 0;
            for (String line : Files.readAllLines(file)) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length == 3) {
                    long value = parseLong(parts[2]).orElse(0L);
                    if (parts[1].equals("Read")) {
                        read += value;
                    } else if (parts[1].equals("Write")) {
                        write += value;
                    }
                }
            }
            return new long[] { read, write };
        } catch (IOException | RuntimeException e) {
            return new long[] { UNKNOWN, UNKNOWN };
        }
    }

    /**
     * Reads a cgroup v2 limit, which is {@code max} when there is none
     */
    static long readLimit(Path file) {
        List<String> lines = readLines(file);
        if (lines.isEmpty() || lines.get(0).trim().equals("max")) {
            return UNKNOWN;
        }
        return parseLong(lines.get(0).trim()).orElse(UNKNOWN);
    }

    private static long readLong(Path file) {
        List<String> lines = readLines(file);
        return lines.isEmpty() ? UNKNOWN : parseLong(lines.get(0).trim()).orElse(UNKNOWN);
    }

    private static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    private static Optional<Long> parseLong(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
