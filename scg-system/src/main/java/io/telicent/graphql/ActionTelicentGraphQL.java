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

package io.telicent.graphql;

import java.util.Enumeration;
import java.util.Objects;
import java.util.function.Function;

import io.telicent.jena.abac.ABAC;
import io.telicent.jena.abac.fuseki.ABAC_Processor;
import io.telicent.jena.abac.fuseki.ABAC_Request;
import io.telicent.jena.graphql.execution.GraphQLOverDatasetExecutor;
import io.telicent.jena.graphql.fuseki.ActionGraphQL;
import io.telicent.jena.graphql.schemas.telicent.graph.TelicentGraphSchema;
import io.telicent.jena.graphql.server.model.GraphQLRequest;
import io.telicent.servlet.auth.jwt.JwtHttpConstants;
import io.telicent.servlet.auth.jwt.JwtServletConstants;
import io.telicent.servlet.auth.jwt.verifier.aws.AwsConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.riot.web.HttpNames;
import org.apache.jena.sparql.core.DatasetGraph;

/**
 * A Fuseki action that evaluates GraphQL Requests that use the Telicent Graph schema
 */
public class ActionTelicentGraphQL extends ActionGraphQL implements ABAC_Processor {
    private final Function<HttpAction, String> getUser;

    public ActionTelicentGraphQL(GraphQLOverDatasetExecutor executor, Function<HttpAction, String> getUser) {
        super(executor);
        this.getUser = Objects.requireNonNull(getUser, "getUser function cannot be null");
    }

    @Override
    protected DatasetGraph prepare(HttpAction action, GraphQLRequest request, DatasetGraph dsg) {
        // Decide the dataset that applies.
        // Note that we also need to pass the authentication token for the request
        // into the execution because, depending on the query, we may need to call
        // out to other CORE services which will require the authentication token
        // for access.

        DatasetGraph dsgRequest;
        if (ABAC.isDatasetABAC(dsg)) {
            dsgRequest = ABAC_Request.decideDataset(action, dsg, getUser);
            String token = findAuthToken(action);
            if (token != null) {
                request.getExtensions().put(TelicentGraphSchema.EXTENSION_AUTH_TOKEN, token);
            }
        } else {
            dsgRequest = dsg;
        }
        return dsgRequest;
    }

    /**
     * Finds the authentication token that was supplied with this request, relies on the JWT Servlet Auth libraries
     * behaviour of placing the raw JWT used to authenticate the user into the request attribute
     * {@link JwtServletConstants#REQUEST_ATTRIBUTE_RAW_JWT}
     *
     * @param httpAction HTTP Action
     * @return Authentication token
     */
    private String findAuthToken(HttpAction httpAction) {
        Object rawJwt = httpAction.getRequest().getAttribute(JwtServletConstants.REQUEST_ATTRIBUTE_RAW_JWT);
        if (rawJwt instanceof String) {
            return (String) rawJwt;
        }
        return null;
    }
}
