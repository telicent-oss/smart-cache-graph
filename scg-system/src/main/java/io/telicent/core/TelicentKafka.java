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

import io.telicent.jena.abac.fuseki.ABAC_ChangeDispatch;
import org.apache.jena.riot.web.HttpNames;

/**
 * Constants related to the incoming Kafka events.
 * <p>
 * <a href="https://github.com/Telicent-io/telicent-architecture/wiki/Kafka-Headers">telicent-architecture/wiki/Kafka-Headers</a>.
 */
public class TelicentKafka {

    /**
     * Request-Id : Unique ID for the request (UUID).
     */
    public static final String TF_RequestId = "Request-Id";

    /**
     * Content-Type : Syntax of the message value.
     * <p>
     * If this does not agree with {@link HttpNames#hContentType},
     * check {@link ABAC_ChangeDispatch}.
     * and {@code HttpServletRequestMinimal}
     * so the Fuseki-Kafka connector so {@code HttpAction.getRequestContentType()} works.
     */
    public static final String TF_ContentType = HttpNames.hContentType; // "Content-Type"

    /**
     * Content-Encoding : The form of compression.
     * <p>
     * If this does not agree with {@link HttpNames#hContentType},
     * check {@link ABAC_ChangeDispatch}.
     */
    public static final String TF_ContentEncoding = HttpNames.hContentEncoding; // "Content-Encoding"

    /**
     * Data-Model : The data schema (if not in the body).
     */
    public static final String TF_DataModel = "Data-Model";

    /**
     * Exec-Path
     */
    public static final String JF_ExecPath = "";
}
