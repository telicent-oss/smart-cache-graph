package io.telicent.labels.services;

import io.telicent.jena.abac.attributes.AttributeExpr;
import io.telicent.labels.TripleLabels;
import io.telicent.smart.cache.security.data.labels.SecurityLabels;
import io.telicent.smart.cache.security.data.labels.SecurityLabelsApplicator;
import io.telicent.smart.cache.security.data.plugins.DataSecurityPlugin;
import io.telicent.smart.cache.security.data.plugins.rdf.abac.RdfAbacParser;
import org.apache.commons.io.FileUtils;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TestLabelsQueryServiceMem {

    private static final String DATASET_NAME = "test";

    private File dbDir;

    @BeforeEach
    public void setUp() throws IOException {
        dbDir = Files.createTempDirectory("tmpDir").toFile();
    }

    @Test
    public void testLabelQuery() {
        final Triple triple = Triple.create(
                NodeFactory.createURI("http://example.org/subject"),
                NodeFactory.createURI("http://example.org/predicate"),
                NodeFactory.createURI("http://example.org/object")
        );
        final DatasetGraph emptyDsg = DatasetGraphFactory.create();
        final DataSecurityPlugin mockDataSecurityPlugin = mock(DataSecurityPlugin.class);
        final SecurityLabelsApplicator mockSecurityLabelsApplicator = mock(SecurityLabelsApplicator.class);
        when(mockDataSecurityPlugin.prepareLabelsApplicator(any(),any())).thenReturn(mockSecurityLabelsApplicator);
        final RdfAbacParser rdfAbacParser = new RdfAbacParser();
        final SecurityLabels<List<AttributeExpr>> securityLabels = rdfAbacParser.parseSecurityLabels("example".getBytes(StandardCharsets.UTF_8));
        doReturn(securityLabels).when(mockSecurityLabelsApplicator).labelForTriple(any());
        final LabelsQueryService queryService = new LabelsQueryService(mockDataSecurityPlugin, emptyDsg, DATASET_NAME);
        final List<TripleLabels> labels = queryService.queryOnlyLabelStore(triple);
        assertEquals(1, labels.size());
        assertEquals("example", labels.getFirst().label.toDebugString());
    }

    @Test
    public void testLabelQueryLiteral() {
        final Triple triple = Triple.create(
                NodeFactory.createURI("http://example.org/subject"),
                NodeFactory.createURI("http://example.org/predicate"),
                NodeFactory.createLiteralByValue("test")
        );
        final DatasetGraph emptyDsg = DatasetGraphFactory.create();
        final DataSecurityPlugin mockDataSecurityPlugin = mock(DataSecurityPlugin.class);
        final SecurityLabelsApplicator mockSecurityLabelsApplicator = mock(SecurityLabelsApplicator.class);
        when(mockDataSecurityPlugin.prepareLabelsApplicator(any(),any())).thenReturn(mockSecurityLabelsApplicator);
        final RdfAbacParser rdfAbacParser = new RdfAbacParser();
        final SecurityLabels<List<AttributeExpr>> securityLabels = rdfAbacParser.parseSecurityLabels("example".getBytes(StandardCharsets.UTF_8));
        doReturn(securityLabels).when(mockSecurityLabelsApplicator).labelForTriple(any());
        final LabelsQueryService queryService = new LabelsQueryService(mockDataSecurityPlugin, emptyDsg, DATASET_NAME);
        final List<TripleLabels> labels = queryService.queryOnlyLabelStore(triple);
        assertEquals(1, labels.size());
        assertEquals("example", labels.getFirst().label.toDebugString());
    }

    @AfterEach
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(dbDir);
    }
}
