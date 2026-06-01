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

import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.labels.LabelsStore;
import io.telicent.smart.cache.distribution.lifecycle.DistributionLifecycleState;
import io.telicent.smart.cache.distribution.lifecycle.events.LifecycleAction;
import io.telicent.smart.cache.distribution.lifecycle.events.listeners.DistributionLifecycleListener;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.system.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A Distribution Lifecycle Listener that reacts to the Deleted state by
 * proactively removing the named graph that corresponds to the distribution
 * from the underlying dataset(s).
 */
public class DistributionGraphDeletionListener implements DistributionLifecycleListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributionGraphDeletionListener.class);

    private final Supplier<Collection<DatasetGraphABAC>> datasetSupplier;

    /**
     * Creates a new listener.
     *
     * @param datasetSupplier Supplies the ABAC datasets to review
     */
    public DistributionGraphDeletionListener(Supplier<Collection<DatasetGraphABAC>> datasetSupplier) {
        this.datasetSupplier = Objects.requireNonNull(datasetSupplier, "datasetsSupplier cannot be null");
    }

    @Override
    public void accept(LifecycleAction action) {
        if (action == null) {
            return;
        }

        if (action.getState().getTo() != DistributionLifecycleState.Deleted) {
            return;
        }

        String distributionId = action.getDistributionId();
        if (StringUtils.isBlank(distributionId)) {
            LOGGER.debug("Ignoring distribution deletion event {} with no distribution id", action.getEventId());
            return;
        }

        Collection<DatasetGraphABAC> datasets = this.datasetSupplier.get();
        if (datasets == null || datasets.isEmpty()) {
            LOGGER.debug("No ABAC datasets to delete named graph {} from", distributionId);
            return;
        }

        Node graphName = NodeFactory.createURI(distributionId);
        List<RuntimeException> failures = new ArrayList<>();
        for (DatasetGraphABAC dataset : datasets) {
            if (dataset == null) {
                continue;
            }
            try {
                deleteDistributionGraph(dataset, graphName);
            } catch (RuntimeException e) {
                LOGGER.error("Failed to delete named graph {} for deleted distribution {}", distributionId,
                             distributionId, e);
                failures.add(e);
            }
        }

        if (!failures.isEmpty()) {
            RuntimeException toThrow =
                    new RuntimeException("Failed to delete named graph for deleted distribution " + distributionId +
                                         " from " + failures.size() + " dataset(s)");
            failures.forEach(toThrow::addSuppressed);
            throw toThrow;
        }
    }

    /**
     * Deletes the named graph corresponding to a distribution, and any associated ABAC security labels, for the given dataset.
     * @param dataset   ABAC dataset to delete from
     * @param graphName Named graph to delete
     */
    static void deleteDistributionGraph(DatasetGraphABAC dataset, Node graphName) {
        Txn.executeWrite(dataset, () -> {
            DatasetGraph base = dataset.getBase();
            LabelsStore labels = dataset.labelsStore();
            if (labels != null) {
                List<Quad> quads = Iter.toList(base.find(graphName, Node.ANY, Node.ANY, Node.ANY));
                for (Quad quad : quads) {
                    try {
                        labels.remove(quad);
                    } catch (RuntimeException e) {
                        LOGGER.warn("Failed to remove security labels for quad {} while deleting named graph {}", quad,
                                    graphName, e);
                    }
                }
            }
            base.removeGraph(graphName);
            LOGGER.info("Deleted named graph {} for deleted distribution", graphName.getURI());
        });
    }
}