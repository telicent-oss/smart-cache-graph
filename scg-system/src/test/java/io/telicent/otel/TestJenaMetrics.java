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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.logging.LoggingMetricExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.telicent.LibTestsSCG;
import io.telicent.core.SmartCacheGraph;
import io.telicent.servlet.auth.jwt.verifier.aws.AwsConstants;
import org.apache.jena.Jena;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.http.HttpEnv;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sys.JenaSystem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TestJenaMetrics {

    @BeforeAll
    static void setup() throws Exception {
        JenaSystem.init();
        FusekiLogging.setLogging();
        System.setProperty("otel.java.global-autoconfigure.enabled", "true");
        LibTestsSCG.setupAuthentication();
        LibTestsSCG.disableInitialCompaction();
    }

    @Test
    void no_configuration() throws Exception {
        OpenTelemetry otel = JenaMetrics.get();
        assertNotNull(otel);
        LibTestsSCG.teardownAuthentication();
    }

    @Test
    void explicit_configuration() {
        OpenTelemetry defOtel = JenaMetrics.get();
        OpenTelemetry explicit = OpenTelemetry.propagating(ContextPropagators.noop());
        assertNotEquals(defOtel, explicit);

        JenaMetrics.set(explicit);
        assertEquals(JenaMetrics.get(), explicit);
    }

    @Test
    void get_meter_01() {
        Meter m;
        try {
            mockOpenTelemetry_01();

            // If we've explicitly configured a proper Open Telemetry instance then we should get the same meter each
            // time we use the same meter name
            m = JenaMetrics.getMeter("test", Jena.VERSION);
            Meter m2 = JenaMetrics.getMeter("test", Jena.VERSION);
            assertEquals(m, m2);

            // However a different meter name should produce a different instance
            Meter n = JenaMetrics.getMeter("other", Jena.VERSION);
            assertNotEquals(m, n);
            assertNotEquals(m2, n);
        } finally {
            JenaMetrics.reset();
        }

        // After a reset should get different meter instances again
        Meter m3 = JenaMetrics.getMeter("test", Jena.VERSION);
        assertNotEquals(m, m3);

        // Calling reset() again has no effect here
        JenaMetrics.reset();
    }

    @Test
    void get_meter_02() {
        JenaMetrics.getMeter("test", Jena.VERSION);
    }

    private static void mockOpenTelemetry_01() {
        MetricExporter exporter = mock(MetricExporter.class);
        when(exporter.getDefaultAggregation(any())).thenReturn(Aggregation.defaultAggregation());
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                                                         .registerMetricReader(PeriodicMetricReader.builder(exporter)
                                                                                                   .setInterval(
                                                                                                           Duration.ofSeconds(
                                                                                                                   5))
                                                                                                   .build())
                                                         .build();
        OpenTelemetry otel = OpenTelemetrySdk.builder().setMeterProvider(meterProvider).build();
        JenaMetrics.set(otel);
    }

    @Test
    void get_counter_01() {
        try {
            mockOpenTelemetry_01();

            Meter m = JenaMetrics.getMeter("test", Jena.VERSION);
            LongCounter counter = m.counterBuilder("test")
                                   .setDescription("Description")
                                   .build();
            LongCounter counter2 = m.counterBuilder("test")
                                    .setDescription("Description")
                                    .build();
            // Open Telemetry SDK caching should result in the same counter being returned here
            assertEquals(counter, counter2);


            // Even if we use a different meter a counter of the same name returns the same counter instance
            Meter m2 = JenaMetrics.getMeter("other", "0.2");
            assertNotEquals(m, m2);
            LongCounter counter2a = m2.counterBuilder("test")
                                     .setDescription("Description")
                                     .build();
            assertEquals(counter, counter2a);

            // However a different counter name will result in a different instance
            LongCounter counter3 = m2.counterBuilder("other")
                                     .setDescription("Description")
                                     .build();
            assertNotEquals(counter, counter3);
            assertNotEquals(counter2, counter3);
        } finally {
            JenaMetrics.reset();
        }
    }

    @Test
    void time_01() {
        verifyTimingMetrics(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void verifyTimingMetrics(Runnable runnable) {
        try {
            MetricsCollector collector = new MetricsCollector();
            MetricReader reader = prepareTimingReader(collector);

            Meter m = JenaMetrics.getMeter("test", Jena.VERSION);
            DoubleHistogram histogram = m.histogramBuilder("timings").build();
            Attributes attributes =
                    Attributes.of(AttributeKey.stringKey(AttributeNames.INSTANCE_ID), UUID.randomUUID().toString());

            JenaMetrics.time(histogram, attributes, runnable);
            verifyTimingMetric(collector, reader);
        } finally {
            JenaMetrics.reset();
        }
    }

    private static <T> void verifyTimingMetrics(Callable<T> runnable, Class<?> expectedError) {
        try {
            MetricsCollector collector = new MetricsCollector();
            MetricReader reader = prepareTimingReader(collector);

            Meter m = JenaMetrics.getMeter("test", Jena.VERSION);
            DoubleHistogram histogram = m.histogramBuilder("timings").build();
            Attributes attributes =
                    Attributes.of(AttributeKey.stringKey(AttributeNames.INSTANCE_ID), UUID.randomUUID().toString());

            try {
                JenaMetrics.time(histogram, attributes, runnable);
            } catch (Exception e) {
                if (expectedError == null) {
                    fail("Error not expected");
                } else {
                    assertTrue(expectedError.isAssignableFrom(e.getClass()));
                }
            }
            verifyTimingMetric(collector, reader);
        } finally {
            JenaMetrics.reset();
        }
    }

    private static void verifyTimingMetric(MetricsCollector collector, MetricReader reader) {
        reader.forceFlush();

        Map<String, Map<Attributes, Double>> metrics = collector.getAllMetrics();
        assertFalse(metrics.isEmpty());
        Double count = collector.getMetric("timings.count", Attributes.empty());
        assertEquals(1.0, count, 0.001);
    }

    private static MetricReader prepareTimingReader(MetricsCollector collector) {
        MetricReader reader = PeriodicMetricReader.builder(collector).setInterval(Duration.ofSeconds(5)).build();
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                                                         .registerMetricReader(reader)
                                                         .build();
        OpenTelemetry otel = OpenTelemetrySdk.builder().setMeterProvider(meterProvider).build();
        JenaMetrics.set(otel);
        return reader;
    }

    @Test
    void time_02() {
        verifyTimingMetrics(() -> {
            return 12345;
        }, null);
    }

    @Test
    void time_03() {
        verifyTimingMetrics(() -> {
            throw new Exception("test");
        }, Exception.class);
    }

    @Test
    void time_04() {
        verifyTimingMetrics(() -> {
            throw new FileNotFoundException();
        }, IOException.class);
    }

    @Test
    void server_metrics_reader() throws IOException, InterruptedException {
        boolean openTelemetryEnabledSetting = FMod_OpenTelemetry.ENABLED;
        FMod_OpenTelemetry.ENABLED = true;
        try {
            server_metrics_reader2();
        } finally {
            FMod_OpenTelemetry.ENABLED = openTelemetryEnabledSetting;
        }
    }

    private void server_metrics_reader2() throws IOException, InterruptedException {
        InMemoryMetricReader reader = InMemoryMetricReader.create();
        SdkMeterProvider meterProvider = SdkMeterProvider.builder().registerMetricReader(reader).build();
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setMeterProvider(meterProvider).build();
        JenaMetrics.set(sdk);
        assertEquals(0, reader.collectAllMetrics().size());

        FusekiServer server = SmartCacheGraph.smartCacheGraphBuilder()
                .port(0)
                .add("/ds", DatasetGraphFactory.empty())
                .build()
                .start();
        int port = server.getPort();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:"+port+"/ds"))
                    .headers(AwsConstants.HEADER_DATA, LibTestsSCG.tokenForUser("test"))
                    .GET()
                    .build();
            HttpResponse<Void> response = HttpEnv.getDftHttpClient().send(request, HttpResponse.BodyHandlers.discarding());

            /*
            attributes={db.name="/ds", db.operation="gsp-rw", db.system="Apache Jena Fuseki", fuseki.endpoint="", fuseki.operation="Graph Store Protocol"}, value=1
             */

            // Moving to IncubatingSemanticAttributes
            @SuppressWarnings("deprecation")
            AttributeKey<String> aDBname = io.opentelemetry.semconv.trace.attributes.SemanticAttributes.DB_NAME;
            @SuppressWarnings("deprecation")
            AttributeKey<String> aDBoperation = io.opentelemetry.semconv.trace.attributes.SemanticAttributes.DB_OPERATION;

            assertTrue(
                reader.collectAllMetrics()
                  .stream()
                  .filter(m -> m.getName().equals("smartcache.graph.requests.good"))
                  .flatMap(m -> m.getLongGaugeData().getPoints().stream())
                  .filter(d -> d.getAttributes().get(aDBname).equals("/ds") &&
                               d.getAttributes().get(aDBoperation).equals("gsp-rw"))
                  .anyMatch(d -> d.getValue() == 1));
        } finally { server.stop(); }
    }

    @Test
    void server_metrics_exporter() throws IOException, InterruptedException {
        SdkMeterProvider meterProvider = SdkMeterProvider.builder().registerMetricReader(PeriodicMetricReader
                                                                                                 .builder(LoggingMetricExporter.create()).setInterval(Duration.ofSeconds(30)).build()).build();
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setMeterProvider(meterProvider).build();
        JenaMetrics.set(sdk);

        FusekiServer server = FusekiServer.create()
                .port(0)
                .add("/ds", DatasetGraphFactory.empty())
                .build()
                .start();
        int port = server.getPort();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:"+port+"/ds"))
                    .GET()
                    .build();
            HttpResponse<Void> response = HttpEnv.getDftHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
//            server.join();
        } finally { server.stop(); }
    }
}
