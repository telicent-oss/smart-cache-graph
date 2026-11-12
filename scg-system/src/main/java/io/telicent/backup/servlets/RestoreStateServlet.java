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

package io.telicent.backup.servlets;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.core.RestoreMarker;
import io.telicent.core.RestoreMarkerStore;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.atlas.lib.DateTimeUtils;

import java.util.Comparator;
import java.util.List;

import static io.telicent.backup.services.DatasetBackupService.sanitiseName;
import static io.telicent.backup.utils.BackupConstants.DATE_FORMAT;
import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;
import static io.telicent.utils.ServletUtils.handleError;
import static io.telicent.utils.ServletUtils.processResponse;

/**
 * Servlet reporting the record of a restore that did not complete
 * <p>
 * A restore that is interrupted can leave a dataset with some of its stores restored and others not, which is recorded
 * durably so that the dataset is not silently served afterwards. This endpoint is how that state is inspected.
 * </p>
 */
// S1948: Embedded Jetty does not serialise these servlets or replicate their injected service state.
@SuppressWarnings("java:S1948")
public class RestoreStateServlet extends HttpServlet {

    private static final String DATASET_NAME = "dataset-name";

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) {
        final ObjectNode resultNode = OBJECT_MAPPER.createObjectNode();
        resultNode.put("date", DateTimeUtils.nowAsString(DATE_FORMAT));
        final String datasetName = sanitiseName(request.getPathInfo());
        if (datasetName.isEmpty()) {
            resultNode.set("restore-state", restoreState());
            processResponse(response, resultNode);
            return;
        }
        final List<RestoreMarker> markers = markersFor(datasetName);
        if (markers.isEmpty()) {
            handleError(response, resultNode, HttpServletResponse.SC_NOT_FOUND,
                        "No incomplete or failed restore is recorded for dataset " + datasetName);
            return;
        }
        resultNode.put(DATASET_NAME, datasetName);
        resultNode.set("restore-state", toJson(markers));
        processResponse(response, resultNode);
    }

    /**
     * @return Every recorded incomplete or failed restore, newest first
     */
    public static ArrayNode restoreState() {
        return toJson(RestoreMarkerStore.markers().stream()
                                        .sorted(Comparator.comparing(RestoreMarker::startedAt).reversed())
                                        .toList());
    }

    private static List<RestoreMarker> markersFor(final String datasetName) {
        return RestoreMarkerStore.markers().stream()
                                 .filter(marker -> datasetName.equalsIgnoreCase(sanitiseName(marker.datasetName())))
                                 .toList();
    }

    private static ArrayNode toJson(final List<RestoreMarker> markers) {
        final ArrayNode array = OBJECT_MAPPER.createArrayNode();
        markers.stream().map(RestoreMarker::toJson).forEach(array::add);
        return array;
    }

}
