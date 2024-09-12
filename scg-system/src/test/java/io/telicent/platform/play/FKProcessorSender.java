/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.telicent.platform.play;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.telicent.core.FKProcessorSCG;
import org.apache.jena.atlas.io.IOX;
import org.apache.jena.fuseki.kafka.FKBatchProcessor;
import org.apache.jena.fuseki.kafka.FKProcessor;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.kafka.RequestFK;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.Transactional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;

/** Send operations via FKProcessor, including batching */
public class FKProcessorSender {

    /** Create, with a {@link FKProcessorSCG} */
    public static FKProcessorSender create(DatasetGraph dsg, String topic, FusekiServer server) {
        FKProcessor fkProcessor = new FKProcessorSCG(dsg, "http://dataset/kafka/" + topic, server);
        return create(dsg, topic, server, fkProcessor);
    }

    /** Create, with a provided {@link FKProcessor} */
    public static FKProcessorSender create(DatasetGraph dsg, String topic, FusekiServer server, FKProcessor fkProcessor) {
        return new FKProcessorSender(fkProcessor, dsg, topic);
    }


    // Implementation inheritance, hides the details of the PlaySender API.
    private final FKProcessorSender.PlaySenderFKProcessor playSender;
    private static long offset = 0;

    private FKProcessorSender(FKProcessor fkProcessor, Transactional transactional, String topic) {
        Objects.requireNonNull(fkProcessor);
        Objects.requireNonNull(transactional);
        Objects.requireNonNull(topic);
        this.playSender =  new PlaySenderFKProcessor(fkProcessor, transactional, topic);
    }

    public FKProcessorSender send(String filename, Map<String, String> headers) {
        long startOffset = offset;
        var processor = playSender.processor();

        processor.startBatch(1, startOffset);
        try {
            byte[] bytes = Files.readAllBytes(Path.of(filename));
            playSender.play(headers, bytes);
            offset++;
        } catch (IOException e) {
            throw IOX.exception(e);
        }
        long finishOffset = offset;
        int count = Math.toIntExact(finishOffset-startOffset);
        playSender.processor().finishBatch(count, finishOffset, startOffset);
        return this;
    }

    public long offset() {
        return offset;
    }

    private static class PlaySenderFKProcessor implements PlaySender {
        private final FKProcessor fkProcessor;
        private final FKBatchProcessor fkBatchProcessor;
        private final String topic;

        private PlaySenderFKProcessor(FKProcessor fkProcessor, Transactional transactional, String topic) {
            this.fkProcessor = fkProcessor;
            this.fkBatchProcessor = new FKBatchProcessor(transactional, fkProcessor);
            this.topic = topic;
        }

        public FKProcessor processor() {
            return fkProcessor;
        }

        @Override
        public void play(Map<String, String> headers, byte[] bytes) {
            // Batch of one.
            RequestFK requestFK = new RequestFK(topic, headers, bytes);
            ConsumerRecord<String, RequestFK> cRec = new ConsumerRecord<String, RequestFK>(topic, 0, 9999, "key", requestFK);

            List<ConsumerRecord<String, RequestFK>> recordsList = List.of(cRec);

            TopicPartition partition = new TopicPartition(topic, 0);
            Map<TopicPartition, List<ConsumerRecord<String, RequestFK>>> records =
                    Map.of(partition, recordsList);
            ConsumerRecords<String, RequestFK> cRecords = new ConsumerRecords<>(records);
            fkBatchProcessor.processBatch(topic, 999, List.of(cRecords));

            // Old way. Directly call the fkProcessor. This bypasses the batch processor.
//                RequestFK requestFK = new RequestFK(topic, headers, bytes);
//                ResponseFK response = fkProcessor.process(requestFK);
        }
    }
}
