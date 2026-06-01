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

package io.telicent.core;

import io.telicent.jena.abac.ABAC;
import io.telicent.jena.abac.SysABAC;
import io.telicent.jena.abac.attributes.syntax.AEX;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.labels.Label;
import io.telicent.jena.abac.labels.Labels;
import io.telicent.jena.abac.labels.LabelsStore;
import io.telicent.smart.cache.distribution.lifecycle.DistributionLifecycleState;
import io.telicent.smart.cache.distribution.lifecycle.events.LifecycleAction;
import io.telicent.smart.cache.distribution.lifecycle.events.utils.LifecycleStateTransition;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.core.mem.DatasetGraphInMemory;
import org.apache.jena.system.Txn;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class TestDistributionGraphDeletionListener {

    private static final String DISTRIBUTION_ID = "https://example.org/distributions/id-1";
    private static final String DISTRIBUTION_ID_2 = "https://example.org/distributions/id-2";
    private static final String DATASET_ID = "dataset-1";
    private static final String USER_ID = "test@example.org";
    private static final Node SUBJECT_NODE = NodeFactory.createURI("https://example.org/subject");
    private static final Node PREDICATE_NODE = NodeFactory.createURI("https://example.org/predicate");

    private static DatasetGraphABAC newDataset() {
        LabelsStore labels = Labels.createLabelsStoreMem();
        return ABAC.authzDataset(DatasetGraphFactory.createTxnMem(), AEX.strALLOW, labels, SysABAC.allowLabel, null);
    }

    private static void addQuad(DatasetGraphABAC dsg, String graphUri, String objectValue, Label label) {
        Quad quad = Quad.create(NodeFactory.createURI(graphUri), SUBJECT_NODE, PREDICATE_NODE, NodeFactory.createLiteralString(objectValue));
        Txn.executeWrite(dsg, () -> {
            dsg.add(quad);
            if (label != null) {
                dsg.labelsStore().add(quad, label);
            }
        });
    }

    private static boolean graphEmpty(DatasetGraphABAC dsg, String graphUri) {
        return Txn.calculateRead(dsg, () -> dsg.getBase().getGraph(NodeFactory.createURI(graphUri)).isEmpty());
    }

    private static boolean labelsEmpty(DatasetGraphABAC dsg) {
        return Txn.calculateRead(dsg, () -> dsg.labelsStore().isEmpty());
    }

    private static LifecycleAction action(String distributionId, DistributionLifecycleState from,
                                          DistributionLifecycleState to) {
        return LifecycleAction.builder()
                              .eventId(UUID.randomUUID())
                              .distributionId(distributionId)
                              .datasetId(DATASET_ID)
                              .user(USER_ID)
                              .state(LifecycleStateTransition.builder().from(from).to(to).build())
                              .build();
    }

    private static DistributionGraphDeletionListener listenerFor(DatasetGraphABAC... datasets) {
        List<DatasetGraphABAC> list = List.of(datasets);
        return new DistributionGraphDeletionListener(() -> list);
    }

    private static DatasetGraphABAC throwingDataset() {
        // The override must live on the innermost (non-wrapper) graph: DatasetGraphABAC.getBase() recursively unwraps
        // any DatasetGraphWrapper layers, so a wrapper's override would be bypassed by the listener.
        DatasetGraphInMemory base = new DatasetGraphInMemory() {
            @Override
            public void removeGraph(Node graphName) {
                throw new RuntimeException("Failure removing graph");
            }
        };
        return ABAC.authzDataset(base, AEX.strALLOW, Labels.createLabelsStoreMem(), SysABAC.allowLabel, null);
    }

    @Test
    void deletesNamedGraphForDeletedDistribution() {
        // given
        DatasetGraphABAC dsg = newDataset();
        addQuad(dsg, DISTRIBUTION_ID, "value1", Label.fromText("PERMIT"));
        addQuad(dsg, DISTRIBUTION_ID_2, "value2", Label.fromText("PERMIT"));
        // when
        try (DistributionGraphDeletionListener  listener = listenerFor(dsg)) {
            assertNotNull(listener);
            listener.accept(action(DISTRIBUTION_ID, DistributionLifecycleState.Active, DistributionLifecycleState.Deleted));
        }
        // then
        assertTrue(graphEmpty(dsg, DISTRIBUTION_ID), "Deleted distribution's named graph should be removed");
        assertFalse(graphEmpty(dsg, DISTRIBUTION_ID_2), "Other distribution's named graph should be untouched");

    }

    @Test
    void removesSecurityLabelsForDeletedDistribution() {
        //given
        DatasetGraphABAC dsg = newDataset();
        addQuad(dsg, DISTRIBUTION_ID, "value1", Label.fromText("PERMIT"));
        // when
        try (DistributionGraphDeletionListener  listener = listenerFor(dsg)) {
            assertNotNull(listener);
            listener.accept(action(DISTRIBUTION_ID, DistributionLifecycleState.Active, DistributionLifecycleState.Deleted));
        }
        // then
        assertTrue(graphEmpty(dsg, DISTRIBUTION_ID), "Named graph should be removed");
        assertTrue(labelsEmpty(dsg), "Security labels for the deleted distribution should be removed");
    }

    @Test
    void ignoresNonDeletedTransition() {
        // given
        DatasetGraphABAC dsg = newDataset();
        addQuad(dsg, DISTRIBUTION_ID, "value1", Label.fromText("PERMIT"));
        // when
        try (DistributionGraphDeletionListener  listener = listenerFor(dsg)) {
            assertNotNull(listener);
            listener.accept(action(DISTRIBUTION_ID, DistributionLifecycleState.Active, DistributionLifecycleState.Withdrawn));
        }
        // then
        assertFalse(graphEmpty(dsg, DISTRIBUTION_ID), "A non-Deleted transition must not delete the named graph");
        assertFalse(labelsEmpty(dsg), "A non-Deleted transition must not remove labels");
    }

    @Test
    void isIdempotentWhenAppliedRepeatedly() {
        // given
        DatasetGraphABAC dsg = newDataset();
        addQuad(dsg, DISTRIBUTION_ID, "value1", Label.fromText("PERMIT"));
        // when
        try (DistributionGraphDeletionListener listener = listenerFor(dsg)) {
            LifecycleAction deleted =
                    action(DISTRIBUTION_ID, DistributionLifecycleState.Active, DistributionLifecycleState.Deleted);
            // then
            assertDoesNotThrow(() -> {
                listener.accept(deleted);
                listener.accept(deleted);
            });
        }
        assertTrue(graphEmpty(dsg, DISTRIBUTION_ID));
    }

    @Test
    void isSafeWhenNamedGraphDoesNotExist() {
        // given
        DatasetGraphABAC dsg = newDataset();
        // when
        try (DistributionGraphDeletionListener listener = listenerFor(dsg)) {
            // then
            assertDoesNotThrow(() -> listener.accept(
                    action(DISTRIBUTION_ID, DistributionLifecycleState.Active, DistributionLifecycleState.Deleted)));
            assertTrue(graphEmpty(dsg, DISTRIBUTION_ID));
        }
    }

    @Test
    void deletesNamedGraphFromAllManagedDatasets() {
        // given
        DatasetGraphABAC dsg1 = newDataset();
        DatasetGraphABAC dsg2 = newDataset();
        addQuad(dsg1, DISTRIBUTION_ID, "value1", Label.fromText("PERMIT"));
        addQuad(dsg2, DISTRIBUTION_ID, "value1", Label.fromText("PERMIT"));
        // when
        try (DistributionGraphDeletionListener listener = listenerFor(dsg1, dsg2)) {
            listener.accept(action(DISTRIBUTION_ID, DistributionLifecycleState.Active, DistributionLifecycleState.Deleted));
        }
        // then
        assertTrue(graphEmpty(dsg1, DISTRIBUTION_ID), "Named graph should be removed from the first dataset");
        assertTrue(graphEmpty(dsg2, DISTRIBUTION_ID), "Named graph should be removed from the second dataset");
    }

    @Test
    void handlesNoManagedDatasets() {
        // given
        try (DistributionGraphDeletionListener listener = new DistributionGraphDeletionListener(List::of)) {
            // when
            // then
            assertDoesNotThrow(() -> listener.accept(
                    action(DISTRIBUTION_ID, DistributionLifecycleState.Active, DistributionLifecycleState.Deleted)));
        }
    }

    @Test
    void ignoresNullAction() {
        // given
        DatasetGraphABAC dsg = newDataset();
        addQuad(dsg, DISTRIBUTION_ID, "value1", Label.fromText("PERMIT"));

        try (DistributionGraphDeletionListener listener = listenerFor(dsg)) {
            // when
            assertDoesNotThrow(() -> listener.accept(null));
        }

        // then
        assertFalse(graphEmpty(dsg, DISTRIBUTION_ID), "A null action must not delete any data");
    }

    @Test
    void ignoresBlankDistributionId() {
        // given
        DatasetGraphABAC dsg = newDataset();
        addQuad(dsg, DISTRIBUTION_ID, "value1", Label.fromText("PERMIT"));

        try (DistributionGraphDeletionListener listener = listenerFor(dsg)) {
            // when
            listener.accept(action("", DistributionLifecycleState.Active, DistributionLifecycleState.Deleted));
        }

        // then
        assertFalse(graphEmpty(dsg, DISTRIBUTION_ID),
                    "A deletion event with a blank distribution id must not delete any data");
    }

    @Test
    void handlesNullDatasetCollection() {
        // given
        LifecycleAction deleted =
                action(DISTRIBUTION_ID, DistributionLifecycleState.Active, DistributionLifecycleState.Deleted);

        try (DistributionGraphDeletionListener listener = new DistributionGraphDeletionListener(() -> null)) {
            // when
            // then
            assertDoesNotThrow(() -> listener.accept(deleted));
        }
    }

    @Test
    void skipsNullDatasetEntries() {
        // given
        DatasetGraphABAC dsg = newDataset();
        addQuad(dsg, DISTRIBUTION_ID, "value1", Label.fromText("PERMIT"));

        try (DistributionGraphDeletionListener listener =
                     new DistributionGraphDeletionListener(() -> Arrays.asList(null, dsg))) {
            // when
            assertDoesNotThrow(() -> listener.accept(
                    action(DISTRIBUTION_ID, DistributionLifecycleState.Active, DistributionLifecycleState.Deleted)));
        }

        // then
        assertTrue(graphEmpty(dsg, DISTRIBUTION_ID),
                   "Null dataset entries should be skipped and remaining datasets still processed");
    }

    @Test
    void continuesProcessingOtherDatasetsAndRethrowsWhenADatasetFails() {
        // given
        DatasetGraphABAC failing = throwingDataset();
        DatasetGraphABAC working = newDataset();
        addQuad(working, DISTRIBUTION_ID, "value1", Label.fromText("PERMIT"));

        try (DistributionGraphDeletionListener listener = listenerFor(failing, working)) {
            LifecycleAction deleted =
                    action(DISTRIBUTION_ID, DistributionLifecycleState.Active, DistributionLifecycleState.Deleted);

            // when
            // then
            assertThrows(RuntimeException.class, () -> listener.accept(deleted));
            assertTrue(graphEmpty(working, DISTRIBUTION_ID),
                       "A failure on one dataset must not prevent deletion on the others");
        }
    }
}
