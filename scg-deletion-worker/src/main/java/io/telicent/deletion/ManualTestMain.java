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

package io.telicent.deletion;

import io.telicent.smart.cache.sources.TelicentHeaders;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.BytesSerializer;
import org.apache.kafka.common.utils.Bytes;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

//TODO
// remove, or change to just publish records
public class ManualTestMain {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
//    private static final String TOPIC = "knowledge";
    private static final String TOPIC = "RDF";
    private static final String TARGET_DISTRIBUTION = "dist-test-001";
    private static final String OTHER_DISTRIBUTION = "dist-test-002";

    public static void main(String[] args) throws Exception {

        System.out.println("Publishing test records...");
        try (KafkaProducer<Bytes, Bytes> producer = createProducer()) {
            publishRecord(producer, TARGET_DISTRIBUTION, nquads("http://example.org/emp/1", "Alice"));
            publishRecord(producer, OTHER_DISTRIBUTION, nquads("http://example.org/emp/2", "Bob"));
            publishRecord(producer, TARGET_DISTRIBUTION, nquads("http://example.org/emp/3", "Carol"));
            publishRecord(producer, OTHER_DISTRIBUTION, nquads("http://example.org/emp/4", "Dave"));
            publishRecord(producer, TARGET_DISTRIBUTION, nquads("http://example.org/emp/5", "Eve"));
        }
        System.out.println("Published 5 records (3 for target distribution, 2 for other)");

        String jobId = "manual-test-" + UUID.randomUUID();
        System.out.println("\nStarting deletion consumer for distribution: " + TARGET_DISTRIBUTION);
        System.out.println("Job ID: " + jobId);

        List<String> handled = new ArrayList<>();
        try (DeletionJobConsumer consumer = new DeletionJobConsumer(
                BOOTSTRAP_SERVERS, TOPIC, TARGET_DISTRIBUTION, jobId)) {
            consumer.process(record -> {
                String payload = new String(record.value().get(), StandardCharsets.UTF_8);
                System.out.println("  -> Handled record at offset " + record.offset() + ": " + payload);
                handled.add(payload);
            });
        }

        // report
        System.out.println("\nDone. Handled " + handled.size() + " records.");
        System.out.println("Expected: 3 (Alice, Carol, Eve)");
        System.out.println(handled.size() == 3 ? "PASS" : "FAIL — expected 3, got " + handled.size());
    }

    private static void publishRecord(KafkaProducer<Bytes, Bytes> producer,
                                      String distributionId, String payload) throws Exception {
        ProducerRecord<Bytes, Bytes> record = new ProducerRecord<>(
                TOPIC,
                Bytes.wrap("key".getBytes(StandardCharsets.UTF_8)),
                Bytes.wrap(payload.getBytes(StandardCharsets.UTF_8))
        );
        record.headers().add(
                TelicentHeaders.DISTRIBUTION_ID,
                distributionId.getBytes(StandardCharsets.UTF_8)
        );
        record.headers().add(
                TelicentHeaders.CONTENT_TYPE,
                "NQuads".getBytes(StandardCharsets.UTF_8)
        );
        producer.send(record).get();
        System.out.println("  Published: " + distributionId + " -> " + payload.substring(0, 40) + "...");
    }

    private static String nquads(String subject, String name) {
        return "<" + subject + "> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> " +
                "<http://xmlns.com/foaf/0.1/Person> .\n" +
                "<" + subject + "> <http://xmlns.com/foaf/0.1/name> \"" + name + "\" .";
    }

    private static KafkaProducer<Bytes, Bytes> createProducer() {
        Properties props = new Properties();
        props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, BytesSerializer.class.getName());
        props.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, BytesSerializer.class.getName());
        return new KafkaProducer<>(props);
    }
}
