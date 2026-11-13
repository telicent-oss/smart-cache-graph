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

import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.distribution.lifecycle.DistributionLifecycleState;
import io.telicent.smart.cache.security.data.distribution.DistributionLifecycleStateFile;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Decides, from a distribution's lifecycle state, whether an event's data may be ingested.
 * <ul>
 *     <li><strong>State unavailable</strong> (state file missing, unreadable or for another application) - rejected,
 *     every event, so ingest fails closed.</li>
 *     <li><strong>{@code Deleted}</strong> - rejected. The first event per distribution is dead-lettered, later ones
 *     are dropped.</li>
 *     <li><strong>{@code Unregistered}</strong>, <strong>or not in the state file at all</strong> - rejected, and
 *     <strong>every</strong> such event is dead-lettered. The state file only lists distributions the lifecycle
 *     tracker has seen, so an unknown distribution is one that has never been registered. Its data may well be wanted
 *     later, so none of it is silently dropped; it can be replayed from the dead letter topic once the distribution is
 *     registered.</li>
 *     <li>Anything else ({@code Registered}, {@code Active}, {@code Withdrawn}) - ingested. Visibility is handled at
 *     query time.</li>
 * </ul>
 * <p>
 * The lifecycle tracker consumes the lifecycle topic concurrently with data ingestion, so data for a newly registered
 * distribution can arrive before the tracker has written the registration to the state file. To avoid rejecting it,
 * the first time an unregistered distribution is seen the gate re-reads its state for a short grace period before
 * rejecting. After the grace period has been spent once for a distribution, later events for it are rejected without
 * waiting, until its state changes.
 * </p>
 * <p>
 * NB - the sink is normally called inside the ingest write transaction, so while waiting out the grace period other
 * writers are blocked (readers are not).  This only happens once per unregistered distribution per process.
 * </p>
 */
final class DistributionIngestGate {

    /**
     * Environment variable controlling whether data for unregistered distributions is rejected (default {@code true})
     */
    static final String REJECT_UNREGISTERED = "DISTRIBUTION_REJECT_UNREGISTERED";
    /**
     * Environment variable giving the grace period, as an ISO-8601 duration, for an unregistered distribution's
     * registration to reach the lifecycle state file (default {@code PT30S})
     */
    static final String UNREGISTERED_GRACE = "DISTRIBUTION_UNREGISTERED_GRACE";
    static final Duration DEFAULT_UNREGISTERED_GRACE = Duration.ofSeconds(30);
    static final Duration RECHECK_INTERVAL = Duration.ofMillis(500);

    /**
     * What to do with an event
     */
    enum Decision {
        /**
         * Ingest the event's data
         */
        INGEST,
        /**
         * Do not ingest; reject the event so it is dead-lettered
         */
        REJECT,
        /**
         * Do not ingest; drop the event (a deleted distribution whose first event was already rejected)
         */
        DROP
    }

    /**
     * Outcome of {@link #decide(String)}
     *
     * @param decision Decision
     * @param reason   Rejection reason, {@code null} unless the decision is {@link Decision#REJECT}
     */
    record Outcome(Decision decision, String reason) {
    }

    /**
     * Sleeps for the given number of milliseconds
     */
    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private static final String UNREGISTERED = DistributionLifecycleState.Unregistered.name();
    private static final String DELETED = DistributionLifecycleState.Deleted.name();

    private final DistributionLifecycleStateFile stateFile;
    private final boolean rejectUnregistered;
    private final Duration grace;
    private final Sleeper sleeper;
    private final LongSupplier nanoClock;
    // NB - in-memory only, so after a restart the first event for a still-deleted distribution is dead-lettered again,
    //      and an unregistered distribution gets its grace period again
    private final Set<String> deletedAlreadyRejected = ConcurrentHashMap.newKeySet();
    private final Set<String> unregisteredGraceSpent = ConcurrentHashMap.newKeySet();

    DistributionIngestGate(DistributionLifecycleStateFile stateFile, boolean rejectUnregistered, Duration grace,
                           Sleeper sleeper, LongSupplier nanoClock) {
        this.stateFile = Objects.requireNonNull(stateFile);
        this.rejectUnregistered = rejectUnregistered;
        this.grace = grace == null || grace.isNegative() ? Duration.ZERO : grace;
        this.sleeper = Objects.requireNonNull(sleeper);
        this.nanoClock = Objects.requireNonNull(nanoClock);
    }

    /**
     * Creates a gate configured from the environment
     *
     * @param stateFile Lifecycle state file
     * @return Gate
     */
    static DistributionIngestGate fromConfiguration(DistributionLifecycleStateFile stateFile) {
        boolean reject = Configurator.get(new String[] { REJECT_UNREGISTERED }, Boolean::parseBoolean, true);
        Duration grace =
                Configurator.get(new String[] { UNREGISTERED_GRACE }, Duration::parse, DEFAULT_UNREGISTERED_GRACE);
        return new DistributionIngestGate(stateFile, reject, grace, Thread::sleep, System::nanoTime);
    }

    /**
     * Decides what to do with an event for the given distribution
     *
     * @param distributionId Distribution ID, already verified to be non-blank
     * @return Outcome
     */
    Outcome decide(String distributionId) {
        DistributionLifecycleStateFile.DistributionStateResult result = this.stateFile.distributionStateResult(distributionId);
        if (!result.available()) {
            return reject("Rejecting ingest because distribution lifecycle state is unavailable for distribution "
                                  + distributionId);
        }
        String state = normalise(result.state());
        if (UNREGISTERED.equals(state) && this.rejectUnregistered
                && !this.unregisteredGraceSpent.contains(distributionId)) {
            state = awaitRegistration(distributionId);
            if (state == null) {
                return reject("Rejecting ingest because distribution lifecycle state became unavailable for "
                                      + "distribution " + distributionId);
            }
        }

        if (DELETED.equals(state)) {
            this.unregisteredGraceSpent.remove(distributionId);
            return this.deletedAlreadyRejected.add(distributionId) ?
                   reject("Rejecting ingest for Deleted distribution " + distributionId) :
                   new Outcome(Decision.DROP, null);
        }
        if (UNREGISTERED.equals(state) && this.rejectUnregistered) {
            this.unregisteredGraceSpent.add(distributionId);
            // NB - same message format as Deleted ("Rejecting ingest for <state> distribution <id>")
            return reject("Rejecting ingest for Unregistered distribution " + distributionId);
        }

        // Viable - forget any earlier rejection so it is re-evaluated if the state changes again
        this.deletedAlreadyRejected.remove(distributionId);
        this.unregisteredGraceSpent.remove(distributionId);
        return new Outcome(Decision.INGEST, null);
    }

    private static Outcome reject(String reason) {
        return new Outcome(Decision.REJECT, reason);
    }

    /**
     * A distribution missing from the state file has never been registered, which the lifecycle contract reports as
     * {@code Unregistered}
     */
    private static String normalise(String state) {
        return state == null ? UNREGISTERED : state.trim();
    }

    /**
     * Re-reads the state until it is no longer {@code Unregistered} or the grace period expires
     *
     * @return Final state, or {@code null} if the state became unavailable while waiting
     */
    private String awaitRegistration(String distributionId) {
        final long deadline = this.nanoClock.getAsLong() + this.grace.toNanos();
        String state = UNREGISTERED;
        while (UNREGISTERED.equals(state) && this.nanoClock.getAsLong() - deadline < 0) {
            try {
                this.sleeper.sleep(RECHECK_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            DistributionLifecycleStateFile.DistributionStateResult result =
                    this.stateFile.distributionStateResult(distributionId);
            if (!result.available()) {
                return null;
            }
            state = normalise(result.state());
        }
        return state;
    }
}
