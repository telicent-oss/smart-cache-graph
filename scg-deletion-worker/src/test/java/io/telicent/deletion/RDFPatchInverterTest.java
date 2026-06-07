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

package io.telicent.deletion;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdfpatch.RDFChanges;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.core.Quad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RDFPatchInverterTest {
    private RDFPatchInverter rdfPatchInverter;
    private static final Node S = NodeFactory.createURI("http://example.org/subject");
    private static final Node P = NodeFactory.createURI("http://example.org/predicate");
    private static final Node O = NodeFactory.createURI("http://example.org/object");
    private static final Node G = NodeFactory.createURI("http://example.org/graph");
    private static final Node LITERAL = NodeFactory.createLiteralString("hello");
    private static final Node TYPED_LITERAL = NodeFactory.createLiteralDT(
            "2024-01-01", org.apache.jena.datatypes.xsd.XSDDatatype.XSDdate);
    private static final Node LANG_LITERAL = NodeFactory.createLiteralLang("bonjour", "fr");


    @BeforeEach
    public void setup() {
        rdfPatchInverter = new RDFPatchInverter();
    }

    @Test
    void returnsNullForEmptyDataset() {
        DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
        assertNull(rdfPatchInverter.invert(dsg));
    }

    @Test
    void singleQuadInNamedGraphProducesOneDeleteOperation() {
        DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
        dsg.add(G, S, P, O);

        RDFPatch patch = rdfPatchInverter.invert(dsg);
        assertNotNull(patch);

        RecordingChanges changes = new RecordingChanges();
        patch.apply(changes);

        assertEquals(1, changes.deletes.size());
        assertEquals(0, changes.adds.size());
        assertTrue(changes.hasTransaction);

        DeletedQuad deleted = changes.deletes.getFirst();
        assertEquals(G, deleted.g);
        assertEquals(S, deleted.s);
        assertEquals(P, deleted.p);
        assertEquals(O, deleted.o);
    }

    @Test
    void multipleQuadsAllProduceDeleteOperations() {
        DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
        dsg.add(G, S, P, O);
        dsg.add(G, S, P, NodeFactory.createURI("http://example.org/object2"));
        dsg.add(G, S, P, NodeFactory.createURI("http://example.org/object3"));

        RDFPatch patch = rdfPatchInverter.invert(dsg);
        assertNotNull(patch);

        RecordingChanges changes = new RecordingChanges();
        patch.apply(changes);

        assertEquals(3, changes.deletes.size());
        assertEquals(0, changes.adds.size());
    }

    @Test
    void quadInDefaultGraphIsHandledCorrectly() {
        DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
        dsg.add(Quad.defaultGraphIRI, S, P, O);

        RDFPatch patch = rdfPatchInverter.invert(dsg);
        assertNotNull(patch);

        RecordingChanges changes = new RecordingChanges();
        patch.apply(changes);

        assertEquals(1, changes.deletes.size());
        assertEquals(Quad.defaultGraphIRI, changes.deletes.getFirst().g);
    }

    @Test
    void literalObjectIsPreservedCorrectly() {
        DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
        dsg.add(G, S, P, LITERAL);

        RDFPatch patch = rdfPatchInverter.invert(dsg);
        assertNotNull(patch);

        RecordingChanges changes = new RecordingChanges();
        patch.apply(changes);

        assertEquals(1, changes.deletes.size());
        assertEquals(LITERAL, changes.deletes.getFirst().o);
    }

    @Test
    void typedLiteralObjectIsPreservedCorrectly() {
        DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
        dsg.add(G, S, P, TYPED_LITERAL);

        RDFPatch patch = rdfPatchInverter.invert(dsg);
        assertNotNull(patch);

        RecordingChanges changes = new RecordingChanges();
        patch.apply(changes);

        assertEquals(1, changes.deletes.size());
        assertEquals(TYPED_LITERAL, changes.deletes.getFirst().o);
    }

    @Test
    void langLiteralObjectIsPreservedCorrectly() {
        DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
        dsg.add(G, S, P, LANG_LITERAL);

        RDFPatch patch = rdfPatchInverter.invert(dsg);
        assertNotNull(patch);

        RecordingChanges changes = new RecordingChanges();
        patch.apply(changes);

        assertEquals(1, changes.deletes.size());
        assertEquals(LANG_LITERAL, changes.deletes.getFirst().o);
    }

    @Test
    void patchContainsTransactionBoundaries() {
        DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
        dsg.add(G, S, P, O);

        RDFPatch patch = rdfPatchInverter.invert(dsg);
        assertNotNull(patch);

        RecordingChanges changes = new RecordingChanges();
        patch.apply(changes);

        assertTrue(changes.hasTransaction,
                "Patch must contain transaction boundaries (txnBegin/txnCommit)");
    }

    @Test
    void patchContainsNoAddOperations() {
        DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
        dsg.add(G, S, P, O);
        dsg.add(G, S, P, LITERAL);

        RDFPatch patch = rdfPatchInverter.invert(dsg);
        assertNotNull(patch);

        RecordingChanges changes = new RecordingChanges();
        patch.apply(changes);

        assertEquals(0, changes.adds.size(),
                "Delete patch must never contain add operations");
    }

    @Test
    void multipleNamedGraphsAreAllInverted() {
        Node g1 = NodeFactory.createURI("http://example.org/graph1");
        Node g2 = NodeFactory.createURI("http://example.org/graph2");

        DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
        dsg.add(g1, S, P, O);
        dsg.add(g2, S, P, O);

        RDFPatch patch = rdfPatchInverter.invert(dsg);
        assertNotNull(patch);

        RecordingChanges changes = new RecordingChanges();
        patch.apply(changes);

        assertEquals(2, changes.deletes.size());
        assertTrue(changes.deletes.stream().anyMatch(d -> g1.equals(d.g)));
        assertTrue(changes.deletes.stream().anyMatch(d -> g2.equals(d.g)));
    }

    // records what operations were replayed onto it
    static class RecordingChanges implements RDFChanges {

        final List<DeletedQuad> deletes = new ArrayList<>();
        final List<DeletedQuad> adds = new ArrayList<>();
        boolean hasTransaction = false;

        @Override
        public void add(Node g, Node s, Node p, Node o) {
            adds.add(new DeletedQuad(g, s, p, o));
        }

        @Override
        public void delete(Node g, Node s, Node p, Node o) {
            deletes.add(new DeletedQuad(g, s, p, o));
        }

        @Override
        public void addPrefix(Node gn, String prefix, String uriStr) {

        }

        @Override
        public void deletePrefix(Node gn, String prefix) {

        }

        @Override
        public void txnBegin() {
            hasTransaction = true;
        }

        @Override
        public void txnCommit() {}

        @Override
        public void txnAbort() {}

        @Override
        public void segment() {}

        @Override
        public void header(String field, Node value) {}

        @Override
        public void finish() {}

        @Override
        public void start() {}
    }

    record DeletedQuad(Node g, Node s, Node p, Node o) {}
}
