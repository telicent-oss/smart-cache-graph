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

import io.telicent.smart.cache.distribution.lifecycle.ApplicationState;
import io.telicent.smart.cache.distribution.lifecycle.DistributionLifecycleState;
import io.telicent.smart.cache.distribution.lifecycle.events.IngestStatus;
import io.telicent.smart.cache.distribution.lifecycle.events.LifecycleAcknowledgement;
import io.telicent.smart.cache.distribution.lifecycle.events.LifecycleAction;
import io.telicent.smart.cache.distribution.lifecycle.events.utils.PartitionOffsets;
import io.telicent.smart.cache.distribution.lifecycle.store.DistributionLifecycleStateStore;
import io.telicent.smart.cache.security.data.distribution.DistributionLifecycleStateFile;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only compatibility adapter for externally maintained lifecycle files. Only single-distribution state lookups
 * are supported, as required by the acceptance sink. This never opens a writable store or caches a stale snapshot:
 * the existing file reader handles atomic replacements, recovery files and optional application verification.
 * Tracker-backed ingest uses the actual shared state store instead of this adapter.
 */
final class ExternalDistributionLifecycleStateStore implements DistributionLifecycleStateStore {
    private final DistributionLifecycleStateFile stateFile;
    private volatile boolean closed;

    ExternalDistributionLifecycleStateStore(Path path, String application) {
        this.stateFile = new DistributionLifecycleStateFile(path, application);
    }

    @Override
    public DistributionLifecycleState getLifecycleState(String distributionId) {
        if (this.closed) {
            throw new IllegalStateException("State store is closed");
        }
        if (StringUtils.isBlank(distributionId)) {
            throw new IllegalArgumentException("Distribution ID cannot be blank");
        }
        var result = this.stateFile.distributionStateResult(distributionId);
        if (!result.available()) {
            throw new IllegalStateException("Distribution lifecycle state is unavailable for " + distributionId);
        }
        // Unknown distributions use the common acceptance sink's registration policy and grace period.
        // Unknown state names fail closed rather than being treated as eligible for ingest.
        return result.state() == null ? DistributionLifecycleState.Unregistered
                                      : DistributionLifecycleState.valueOf(result.state());
    }

    @Override
    public void close() {
        this.closed = true;
    }

    private UnsupportedOperationException unsupported() {
        if (this.closed) {
            throw new IllegalStateException("State store is closed");
        }
        return new UnsupportedOperationException("External lifecycle state supports only distribution state lookups");
    }

    @Override
    public void add(LifecycleAction action) {
        throw unsupported();
    }

    @Override
    public void add(String application, LifecycleAcknowledgement ack) {
        throw unsupported();
    }

    @Override
    public void add(String application, IngestStatus status) {
        throw unsupported();
    }

    @Override
    public List<LifecycleAction> activeEvents() {
        throw unsupported();
    }

    @Override
    public List<LifecycleAction> distributionEvents(String distributionId) {
        throw unsupported();
    }

    @Override
    public LifecycleAction latestEvent(String distributionId) {
        throw unsupported();
    }

    @Override
    public LifecycleAction getEvent(UUID eventId) {
        throw unsupported();
    }

    @Override
    public Map<String, DistributionLifecycleState> getLifecycleStates() {
        throw unsupported();
    }

    @Override
    public Map<String, ApplicationState> getApplicationStates(UUID eventId) {
        throw unsupported();
    }

    @Override
    public ApplicationState getApplicationState(UUID eventId, String application) {
        throw unsupported();
    }

    @Override
    public Instant getApplicationStateLastUpdated(UUID eventId, String application) {
        throw unsupported();
    }

    @Override
    public Map<String, PartitionOffsets> getIngestStatuses(String application) {
        throw unsupported();
    }

    @Override
    public PartitionOffsets getIngestStatus(String application, String distributionId) {
        throw unsupported();
    }

    @Override
    public Long getIngestOffset(String application, String distributionId, String partition) {
        throw unsupported();
    }

    @Override
    public Map<String, Map<String, PartitionOffsets>> getAllIngestStatuses() {
        throw unsupported();
    }

    @Override
    public void setSourceIdentity(String sourceId) {
        throw unsupported();
    }

    @Override
    public String getSourceIdentity() {
        throw unsupported();
    }

    @Override
    public void clear() {
        throw unsupported();
    }
}
