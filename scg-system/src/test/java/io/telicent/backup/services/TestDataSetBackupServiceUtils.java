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
package io.telicent.backup.services;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class TestDataSetBackupServiceUtils {

    @Test
    void testHumanReadableDuration_allZero() {
        assertEquals("0ms", DatasetBackupService.humanReadableDuration(Duration.ZERO));
    }

    @Test
    void testHumanReadableDuration_onlyMillis() {
        assertEquals("123ms", DatasetBackupService.humanReadableDuration(Duration.ofMillis(123)));
    }

    @Test
    void testHumanReadableDuration_secondsAndMillis() {
        assertEquals("2s 345ms", DatasetBackupService.humanReadableDuration(Duration.ofMillis(2345)));
    }

    @Test
    void testHumanReadableDuration_minutesSecondsMillis() {
        assertEquals("1m 2s 345ms", DatasetBackupService.humanReadableDuration(Duration.ofMillis(62_345)));
    }

    @Test
    void testHumanReadableDuration_hoursMinutesSecondsMillis() {
        assertEquals("1h 2m 3s 456ms", DatasetBackupService.humanReadableDuration(Duration.ofHours(1)
                .plusMinutes(2).plusSeconds(3).plusMillis(456)));
    }

    @Test
    void testHumanReadableDuration_justHours() {
        assertEquals("2h", DatasetBackupService.humanReadableDuration(Duration.ofHours(2)));
    }

    @Test
    void testHumanReadableDuration_justMinutes() {
        assertEquals("5m", DatasetBackupService.humanReadableDuration(Duration.ofMinutes(5)));
    }

    @Test
    void testHumanReadableDuration_justSeconds() {
        assertEquals("7s", DatasetBackupService.humanReadableDuration(Duration.ofSeconds(7)));
    }
}
