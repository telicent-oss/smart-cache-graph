package io.telicent.access.services;

import io.telicent.servlet.auth.jwt.servlet5.AuthenticatedHttpServletRequest;
import io.telicent.smart.cache.security.data.DataAccessAuthorizer;
import io.telicent.smart.cache.security.data.plugins.DataSecurityPlugin;
import io.telicent.smart.cache.security.data.requests.MinimalRequestContext;
import io.telicent.smart.cache.security.data.requests.RequestContext;
import io.telicent.smart.caches.configuration.auth.UserInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.system.Txn;

import java.util.List;
import java.util.Optional;

public class AccessQueryService {

    private final DatasetGraph datasetGraph;
    private final DataSecurityPlugin dataSecurityPlugin;

    public AccessQueryService(DatasetGraph datasetGraph, DataSecurityPlugin dataSecurityPlugin) {
        this.datasetGraph = datasetGraph;
        this.dataSecurityPlugin = dataSecurityPlugin;
    }

    public List<Triple> getTriples(final HttpAction action, final Triple triple) {
        try (DataAccessAuthorizer authorizer = dataSecurityPlugin.prepareAuthorizer(requestContextFrom(action))) {
            return Txn.calculateRead(datasetGraph, () -> {
                final Optional<DatasetGraph> datasetGraphForUser = authorizer.decideDataset(action, datasetGraph);
                if(datasetGraphForUser.isPresent()) {
                    return datasetGraphForUser.get().getDefaultGraph().find(triple).toList();
                } else {
                    return List.of();
                }
            });
        }
    }

    public int getVisibleTriplesCount(final HttpAction action, final List<Triple> triples) {
        try (DataAccessAuthorizer authorizer = dataSecurityPlugin.prepareAuthorizer(requestContextFrom(action))) {
            return Txn.calculateRead(datasetGraph, () -> {
                final Optional<DatasetGraph> datasetGraphForUser = authorizer.decideDataset(action, datasetGraph);
                if(datasetGraphForUser.isPresent()) {
                    final Graph defaultGraphForUser = datasetGraphForUser.get().getDefaultGraph();
                    int visibleCount = 0;
                    for (Triple triple : triples) {
                        if (defaultGraphForUser.contains(triple)) {
                            visibleCount++;
                        }
                    }
                    return visibleCount;
                } else {
                    return 0;
                }
            });
        }
    }

    private static RequestContext requestContextFrom(HttpAction action) {
        HttpServletRequest request = action.getRequest();
        if (!(request instanceof AuthenticatedHttpServletRequest authenticated)) {
            return null;
        }
        UserInfo userInfo = (UserInfo) request.getAttribute(UserInfo.class.getCanonicalName());
        return MinimalRequestContext.builder()
                                    .jwt(authenticated.getVerifiedJwt())
                                    .username(authenticated.getRemoteUser())
                                    .userInfo(userInfo)
                                    .build();
    }
}
