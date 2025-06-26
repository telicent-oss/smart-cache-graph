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
package io.telicent.backup.utils;

import io.telicent.smart.cache.configuration.Configurator;

public final class BackupConstants {

    private BackupConstants() {
    }
    public static final String ENV_BACKUP_DELETE_GENERATED_FILES = "BACKUP_DELETE_GENERATED_FILES";
    public static final String ZIP_SUFFIX = ".zip";
    public static final String JSON_INFO_SUFFIX = "_info.json";
    public static final String REPORT_SUFFIX = "-validation-report.ttl";
    //TODO
    public static final String DETAILS_SUFFIX = "";
    public static final String WILDCARD_REPORT_SUFFIX = "-*-validation-report.ttl";
    public static final String RDF_BACKUP_SUFFIX = ".nq.gz";
    public static final boolean DELETE_GENERATED_FILES = Configurator.get(ENV_BACKUP_DELETE_GENERATED_FILES,Boolean::valueOf, true);
    public static final String DATE_FORMAT = "yyyy-MM-dd_HH-mm-ss";
}