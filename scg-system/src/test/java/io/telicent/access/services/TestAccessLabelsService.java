package io.telicent.access.services;

import io.jsonwebtoken.Jws;
import io.telicent.jena.abac.ABAC;
import io.telicent.jena.abac.AttributeValueSet;
import io.telicent.jena.abac.SysABAC;
import io.telicent.jena.abac.attributes.syntax.AEX;
import io.telicent.jena.abac.core.AttributesStoreLocal;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.core.VocabAuthz;
import io.telicent.jena.abac.labels.Label;
import io.telicent.jena.abac.labels.Labels;
import io.telicent.jena.abac.labels.LabelsStore;
import io.telicent.labels.QuadLabels;
import io.telicent.servlet.auth.jwt.servlet5.AuthenticatedHttpServletRequest;
import io.telicent.smart.cache.security.data.distribution.DistributionLifecycleFilters;
import io.telicent.smart.cache.security.data.plugins.rdf.abac.RdfAbacPlugin;
import io.telicent.smart.caches.configuration.auth.UserInfo;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.fuseki.system.ActionCategory;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.kafka.FusekiKafka;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.system.Txn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link AccessLabelsService} against an in-memory ABAC dataset, using the real RDF-ABAC plugin, querying as
 * users with different attributes.
 */
public class TestAccessLabelsService {

    private static final String PUBLIC_USER = "public-user";
    private static final String SECRET_USER = "secret-user";
    private static final String NO_ATTRIBUTES_USER = "no-attributes-user";

    private static final String PUBLIC = "public";
    private static final String SECRET = "secret";

    private static final Node GRAPH = NodeFactory.createURI("http://example.org/graph");
    private static final Node SUBJECT = NodeFactory.createURI("http://example.org/subject");
    private static final Node PREDICATE = NodeFactory.createURI("http://example.org/predicate");
    private static final Node PUBLIC_IN_DEFAULT = NodeFactory.createURI("http://example.org/publicInDefault");
    private static final Node SECRET_IN_DEFAULT = NodeFactory.createURI("http://example.org/secretInDefault");
    private static final Node UNLABELLED_IN_DEFAULT = NodeFactory.createURI("http://example.org/unlabelledInDefault");
    private static final Node PUBLIC_IN_NAMED = NodeFactory.createURI("http://example.org/publicInNamed");
    private static final Node SECRET_IN_NAMED = NodeFactory.createURI("http://example.org/secretInNamed");
    private static final Node NON_EXISTENT = NodeFactory.createURI("http://example.org/nonExistent");
    private static final Node IN_LABELS_GRAPH = NodeFactory.createURI("http://example.org/inLabelsGraph");
    private static final Node ACTIVE_DISTRIBUTION = NodeFactory.createURI("http://example.org/distributions#active");
    private static final Node WITHDRAWN_DISTRIBUTION =
            NodeFactory.createURI("http://example.org/distributions#withdrawn");
    private static final Node REGISTERED_DISTRIBUTION =
            NodeFactory.createURI("http://example.org/distributions#registered");
    private static final Node UNKNOWN_DISTRIBUTION = NodeFactory.createURI("http://example.org/distributions#unknown");
    private static final Node IN_ACTIVE_DISTRIBUTION = NodeFactory.createURI("http://example.org/inActive");
    private static final Node IN_WITHDRAWN_DISTRIBUTION = NodeFactory.createURI("http://example.org/inWithdrawn");
    private static final Node IN_REGISTERED_DISTRIBUTION = NodeFactory.createURI("http://example.org/inRegistered");
    private static final Node IN_UNKNOWN_DISTRIBUTION = NodeFactory.createURI("http://example.org/inUnknown");

    @TempDir
    Path tempDir;

    private static AccessLabelsService createService(Label datasetDefaultLabel) {
        return new AccessLabelsService(createDataset(datasetDefaultLabel), new RdfAbacPlugin());
    }

    private static DatasetGraphABAC createDataset(Label datasetDefaultLabel) {
        final LabelsStore labelsStore = Labels.createLabelsStoreMem();
        final AttributesStoreLocal attributesStore = new AttributesStoreLocal();
        attributesStore.put(PUBLIC_USER, AttributeValueSet.of(PUBLIC));
        attributesStore.put(SECRET_USER, AttributeValueSet.of(PUBLIC, SECRET));
        attributesStore.put(NO_ATTRIBUTES_USER, AttributeValueSet.of());
        final DatasetGraphABAC dsgz = ABAC.authzDataset(DatasetGraphFactory.createTxnMem(), AEX.strALLOW, labelsStore,
                                                        datasetDefaultLabel, attributesStore);
        Txn.executeWrite(dsgz, () -> {
            addQuad(dsgz, labelsStore, Quad.defaultGraphIRI, PUBLIC_IN_DEFAULT, PUBLIC);
            addQuad(dsgz, labelsStore, Quad.defaultGraphIRI, SECRET_IN_DEFAULT, SECRET);
            addQuad(dsgz, labelsStore, Quad.defaultGraphIRI, UNLABELLED_IN_DEFAULT, null);
            addQuad(dsgz, labelsStore, GRAPH, PUBLIC_IN_NAMED, PUBLIC);
            addQuad(dsgz, labelsStore, GRAPH, SECRET_IN_NAMED, SECRET);
            // Labelled so it would be visible if the labels graph wasn't excluded
            addQuad(dsgz, labelsStore, VocabAuthz.graphForLabels, IN_LABELS_GRAPH, PUBLIC);
        });
        return dsgz;
    }

    /**
     * As {@link #createService()} with a public quad in the graph of each of an Active, a Withdrawn, a Registered and
     * an unknown (not in the state file) distribution, and the distribution lifecycle filter installed as
     * {@code FMod_DistributionLifecycle} does when routing to named graphs. Only Active distributions are visible, the
     * default graph isn't subject to the lifecycle.
     */
    private AccessLabelsService createLifecycleService() throws IOException {
        final Path stateFile = tempDir.resolve("distribution-lifecycle-state.json");
        Files.writeString(stateFile, """
                {
                  "distributions" : {
                    "%s" : "Active",
                    "%s" : "Withdrawn",
                    "%s" : "Registered"
                  }
                }
                """.formatted(ACTIVE_DISTRIBUTION.getURI(), WITHDRAWN_DISTRIBUTION.getURI(),
                              REGISTERED_DISTRIBUTION.getURI()), StandardCharsets.UTF_8);

        final DatasetGraphABAC dsgz = createDataset(SysABAC.denyLabel);
        final LabelsStore labelsStore = dsgz.labelsStore();
        Txn.executeWrite(dsgz, () -> {
            addQuad(dsgz, labelsStore, ACTIVE_DISTRIBUTION, IN_ACTIVE_DISTRIBUTION, PUBLIC);
            addQuad(dsgz, labelsStore, WITHDRAWN_DISTRIBUTION, IN_WITHDRAWN_DISTRIBUTION, PUBLIC);
            addQuad(dsgz, labelsStore, REGISTERED_DISTRIBUTION, IN_REGISTERED_DISTRIBUTION, PUBLIC);
            addQuad(dsgz, labelsStore, UNKNOWN_DISTRIBUTION, IN_UNKNOWN_DISTRIBUTION, PUBLIC);
        });
        final RdfAbacPlugin plugin = new RdfAbacPlugin();
        final DistributionLifecycleFilters filters = plugin.prepareDistributionLifecycleFilters().orElseThrow();
        assertTrue(filters.installIfConfigured(dsgz, null, stateFile.toString()),
                   "The lifecycle filter should have been installed");
        return new AccessLabelsService(dsgz, plugin);
    }

    private static AccessLabelsService createService() {
        return createService(SysABAC.denyLabel);
    }

    private static void addQuad(DatasetGraphABAC dsgz, LabelsStore labelsStore, Node graph, Node object,
                                String label) {
        final Quad quad = Quad.create(graph, SUBJECT, PREDICATE, object);
        dsgz.getData().add(quad);
        if (label != null) {
            labelsStore.add(quad, Label.fromText(label));
        }
    }

    @SuppressWarnings("unchecked")
    private static HttpAction actionFor(String user) {
        final AuthenticatedHttpServletRequest request = mock(AuthenticatedHttpServletRequest.class);
        when(request.getServletContext()).thenReturn(mock(ServletContext.class));
        when(request.getRemoteUser()).thenReturn(user);
        when(request.getVerifiedJwt()).thenReturn(mock(Jws.class));
        when(request.getAttribute(UserInfo.class.getCanonicalName())).thenReturn(
                UserInfo.builder().sub(user).attributes(Map.of()).build());
        return new HttpAction(1L, FusekiKafka.LOG, ActionCategory.ACTION, request, mock(HttpServletResponse.class));
    }

    private static List<QuadLabels> getLabels(AccessLabelsService service, String user, Node graph, Node object) {
        return service.getLabels(actionFor(user), List.of(Quad.create(graph, SUBJECT, PREDICATE, object)));
    }

    private static Map<Node, String> objectsToLabels(List<QuadLabels> results) {
        return results.stream()
                      .collect(Collectors.toMap(r -> r.triple.getObject(), r -> r.label.toDebugString()));
    }

    @Test
    public void givenWildcardQuery_whenUserHasSomeAttributes_thenOnlyPermittedQuadsReturnedWithLabels() {
        final List<QuadLabels> results = getLabels(createService(), PUBLIC_USER, Quad.defaultGraphIRI, Node.ANY);

        assertEquals(Map.of(PUBLIC_IN_DEFAULT, PUBLIC), objectsToLabels(results));
    }

    @Test
    public void givenWildcardQuery_whenUserHasAllAttributes_thenAllLabelledQuadsReturnedWithLabels() {
        final List<QuadLabels> results = getLabels(createService(), SECRET_USER, Quad.defaultGraphIRI, Node.ANY);

        assertEquals(Map.of(PUBLIC_IN_DEFAULT, PUBLIC, SECRET_IN_DEFAULT, SECRET), objectsToLabels(results));
    }

    @Test
    public void givenWildcardQuery_whenUserHasNoAttributes_thenNothingReturned() {
        assertTrue(getLabels(createService(), NO_ATTRIBUTES_USER, Node.ANY, Node.ANY).isEmpty());
    }

    @Test
    public void givenConcreteQuery_whenQuadVisible_thenQuadReturnedWithLabel() {
        final List<QuadLabels> results =
                getLabels(createService(), PUBLIC_USER, Quad.defaultGraphIRI, PUBLIC_IN_DEFAULT);

        assertEquals(1, results.size());
        assertEquals(Quad.defaultGraphIRI, results.getFirst().graph);
        assertEquals(PUBLIC, results.getFirst().label.toDebugString());
    }

    @Test
    public void givenConcreteQuery_whenQuadHidden_thenSameResultAsNonExistentQuad() {
        final AccessLabelsService service = createService();

        final List<QuadLabels> hidden = getLabels(service, PUBLIC_USER, Quad.defaultGraphIRI, SECRET_IN_DEFAULT);
        final List<QuadLabels> nonExistent = getLabels(service, PUBLIC_USER, Quad.defaultGraphIRI, NON_EXISTENT);

        assertTrue(hidden.isEmpty());
        assertTrue(nonExistent.isEmpty());
    }

    @Test
    public void givenConcreteQueryInNamedGraph_whenQuadHidden_thenNothingReturned() {
        assertTrue(getLabels(createService(), PUBLIC_USER, GRAPH, SECRET_IN_NAMED).isEmpty());
    }

    @Test
    public void givenNamedGraphQuery_whenUserHasSomeAttributes_thenOnlyPermittedQuadsInThatGraphReturned() {
        final List<QuadLabels> results = getLabels(createService(), PUBLIC_USER, GRAPH, Node.ANY);

        assertEquals(1, results.size());
        assertEquals(GRAPH, results.getFirst().graph);
        assertEquals(Map.of(PUBLIC_IN_NAMED, PUBLIC), objectsToLabels(results));
    }

    @Test
    public void givenWildcardGraphQuery_whenUserHasSomeAttributes_thenOnlyPermittedQuadsInAllGraphsReturned() {
        final List<QuadLabels> results = getLabels(createService(), PUBLIC_USER, Node.ANY, Node.ANY);

        assertEquals(Map.of(PUBLIC_IN_DEFAULT, PUBLIC, PUBLIC_IN_NAMED, PUBLIC), objectsToLabels(results));
    }

    @Test
    public void givenWildcardGraphQuery_whenUserHasAllAttributes_thenLabelsGraphExcluded() {
        final List<QuadLabels> results = getLabels(createService(), SECRET_USER, Node.ANY, Node.ANY);

        final Set<Node> graphs = results.stream().map(r -> r.graph).collect(Collectors.toSet());
        assertEquals(Set.of(Quad.defaultGraphIRI, GRAPH), graphs);
        assertEquals(4, results.size());
    }

    @Test
    public void givenDenyDefaultLabel_whenQueryingUnlabelledQuad_thenNothingReturned() {
        assertTrue(getLabels(createService(), SECRET_USER, Quad.defaultGraphIRI, UNLABELLED_IN_DEFAULT).isEmpty());
    }

    @Test
    public void givenDatasetDefaultLabel_whenQueryingUnlabelledQuad_thenReturnedWithDatasetDefaultLabel() {
        final AccessLabelsService service = createService(Label.fromText(PUBLIC));

        final List<QuadLabels> results = getLabels(service, PUBLIC_USER, Quad.defaultGraphIRI, UNLABELLED_IN_DEFAULT);

        assertEquals(1, results.size());
        assertEquals(PUBLIC, results.getFirst().label.toDebugString());
    }

    @Test
    public void givenDatasetDefaultLabel_whenUserLacksDefaultLabelAttributes_thenUnlabelledQuadNotReturned() {
        final AccessLabelsService service = createService(Label.fromText(PUBLIC));

        assertTrue(getLabels(service, NO_ATTRIBUTES_USER, Quad.defaultGraphIRI, UNLABELLED_IN_DEFAULT).isEmpty());
    }

    @Test
    public void givenMultiplePatterns_whenQuerying_thenVisibleResultsForEachPatternReturned() {
        final List<QuadLabels> results = createService().getLabels(actionFor(PUBLIC_USER), List.of(
                Quad.create(Quad.defaultGraphIRI, SUBJECT, PREDICATE, PUBLIC_IN_DEFAULT),
                Quad.create(Quad.defaultGraphIRI, SUBJECT, PREDICATE, SECRET_IN_DEFAULT),
                Quad.create(GRAPH, SUBJECT, PREDICATE, PUBLIC_IN_NAMED)));

        assertEquals(Map.of(PUBLIC_IN_DEFAULT, PUBLIC, PUBLIC_IN_NAMED, PUBLIC), objectsToLabels(results));
    }

    @Test
    public void givenUnauthenticatedRequest_whenQuerying_thenNothingReturned() {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getServletContext()).thenReturn(mock(ServletContext.class));
        when(request.getRemoteUser()).thenReturn(SECRET_USER);
        final HttpAction action =
                new HttpAction(1L, FusekiKafka.LOG, ActionCategory.ACTION, request, mock(HttpServletResponse.class));

        assertTrue(createService().getLabels(action, List.of(Quad.create(Node.ANY, Node.ANY, Node.ANY, Node.ANY)))
                                  .isEmpty());
    }

    @Test
    public void givenLifecycleFilter_whenWildcardGraphQuery_thenOnlyActiveDistributionAndDefaultGraphReturned()
            throws IOException {
        final List<QuadLabels> results = getLabels(createLifecycleService(), PUBLIC_USER, Node.ANY, Node.ANY);

        final Set<Node> objects = results.stream().map(r -> r.triple.getObject()).collect(Collectors.toSet());
        assertTrue(objects.contains(IN_ACTIVE_DISTRIBUTION), "Active distribution's quad should be returned");
        assertTrue(objects.contains(PUBLIC_IN_DEFAULT), "Default graph isn't subject to the lifecycle");
        assertFalse(objects.contains(IN_WITHDRAWN_DISTRIBUTION), "Withdrawn distribution's quad must not be returned");
        assertFalse(objects.contains(IN_REGISTERED_DISTRIBUTION),
                    "Registered distribution's quad must not be returned");
        assertFalse(objects.contains(IN_UNKNOWN_DISTRIBUTION), "Unknown distribution's quad must not be returned");
    }

    @Test
    public void givenLifecycleFilter_whenConcreteQueryInActiveDistribution_thenReturnedWithLabel() throws IOException {
        final List<QuadLabels> results =
                getLabels(createLifecycleService(), PUBLIC_USER, ACTIVE_DISTRIBUTION, IN_ACTIVE_DISTRIBUTION);

        assertEquals(Map.of(IN_ACTIVE_DISTRIBUTION, PUBLIC), objectsToLabels(results));
    }

    @Test
    public void givenLifecycleFilter_whenConcreteQueryInWithdrawnDistribution_thenSameResultAsNonExistentQuad()
            throws IOException {
        final AccessLabelsService service = createLifecycleService();

        final List<QuadLabels> withdrawn =
                getLabels(service, PUBLIC_USER, WITHDRAWN_DISTRIBUTION, IN_WITHDRAWN_DISTRIBUTION);
        final List<QuadLabels> nonExistent = getLabels(service, PUBLIC_USER, WITHDRAWN_DISTRIBUTION, NON_EXISTENT);

        assertTrue(withdrawn.isEmpty());
        assertTrue(nonExistent.isEmpty());
    }

    @Test
    public void givenLifecycleFilter_whenConcreteQueryInRegisteredDistribution_thenNothingReturned()
            throws IOException {
        assertTrue(getLabels(createLifecycleService(), PUBLIC_USER, REGISTERED_DISTRIBUTION,
                             IN_REGISTERED_DISTRIBUTION).isEmpty());
    }

    @Test
    public void givenLifecycleFilter_whenConcreteQueryInDistributionNotInStateFile_thenNothingReturned()
            throws IOException {
        assertTrue(getLabels(createLifecycleService(), PUBLIC_USER, UNKNOWN_DISTRIBUTION, IN_UNKNOWN_DISTRIBUTION)
                           .isEmpty());
    }

    @Test
    public void givenLifecycleFilter_whenConcreteQueryInDefaultGraph_thenReturnedWithLabel() throws IOException {
        final List<QuadLabels> results =
                getLabels(createLifecycleService(), PUBLIC_USER, Quad.defaultGraphIRI, PUBLIC_IN_DEFAULT);

        assertEquals(Map.of(PUBLIC_IN_DEFAULT, PUBLIC), objectsToLabels(results));
    }
}
