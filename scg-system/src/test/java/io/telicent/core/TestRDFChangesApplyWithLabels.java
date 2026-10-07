package io.telicent.core;

import io.telicent.jena.abac.ABAC;
import io.telicent.smart.cache.security.data.labels.DatasetGraphLabelled;
import io.telicent.smart.cache.security.data.labels.SecurityLabels;
import io.telicent.smart.cache.security.data.plugins.DataSecurityPluginLoader;
import io.telicent.jena.abac.SysABAC;
import io.telicent.jena.abac.attributes.syntax.AEX;
import io.telicent.jena.abac.core.AttributesStoreLocal;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.core.VocabAuthz;
import io.telicent.jena.abac.labels.Label;
import io.telicent.jena.abac.labels.Labels;
import io.telicent.jena.abac.labels.LabelsStore;
import java.nio.charset.StandardCharsets;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.system.Txn;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TestRDFChangesApplyWithLabels {

    private static final Node S = NodeFactory.createURI("http://s");
    private static final Node P = NodeFactory.createURI("http://p");
    private static final Node O = NodeFactory.createURI("http://o");
    private static final Node GRAPH = NodeFactory.createURI("http://graph");
    private static final Label LABEL = Label.fromText("clearance=S");
    private static final SecurityLabels<?> SECURITY_LABEL =
            DataSecurityPluginLoader.load().labelsParser().parseSecurityLabels("clearance=S".getBytes(StandardCharsets.UTF_8));

    private DatasetGraphABAC abac;

    private static DatasetGraphLabelled labelled(DatasetGraphABAC dsg) {
        return DataSecurityPluginLoader.load().prepareLabelledDataset(dsg).orElseThrow();
    }

    @BeforeEach
    public void setUp() {
        abac = ABAC.authzDataset(DatasetGraphFactory.createTxnMem(),
                AEX.strALLOW, Labels.createLabelsStoreMem(), SysABAC.denyLabel, new AttributesStoreLocal());
    }

    @Test
    public void givenLabelAndNoDistributionId_whenAdding_thenTripleStoredWithLabel() {
        final RDFChangesApplyWithLabels changes = new RDFChangesApplyWithLabels(labelled(abac), SECURITY_LABEL);
        changes.txnBegin();
        changes.add(GRAPH, S, P, O);
        changes.txnCommit();

        Assertions.assertTrue(abac.contains(GRAPH, S, P, O));
        Label stored = abac.labelsStore().labelForQuad(Quad.create(GRAPH, S, P, O));
        Assertions.assertEquals(stored, LABEL);
    }

    @Test
    public void givenNullLabel_whenAdding_thenTripleStoredWithoutLabel() {
        final RDFChangesApplyWithLabels changes = new RDFChangesApplyWithLabels(labelled(abac), null);
        changes.txnBegin();
        changes.add(GRAPH, S, P, O);
        changes.txnCommit();

        Assertions.assertTrue(abac.contains(GRAPH, S, P, O));
        Label stored = abac.labelsStore().labelForQuad(Quad.create(GRAPH, S, P, O));
        Assertions.assertNull(stored);
    }

    @Test
    public void givenDistributionId_whenAdding_thenTripleStoredInDistributionGraph() {
        final String distributionId = "http://distribution/1";
        final Node distGraph = NodeFactory.createURI(distributionId);

        final RDFChangesApplyWithLabels changes = new RDFChangesApplyWithLabels(labelled(abac), SECURITY_LABEL, distributionId);
        changes.txnBegin();
        changes.add(GRAPH, S, P, O);
        changes.txnCommit();

        Assertions.assertTrue(abac.contains(distGraph, S, P, O));
        Assertions.assertFalse(abac.contains(GRAPH, S, P, O));
    }

    @Test
    public void givenLabelsGraphQuad_whenAdding_thenNotStoredAsDataTriple() {
        final RDFChangesApplyWithLabels changes = new RDFChangesApplyWithLabels(labelled(abac), SECURITY_LABEL);
        changes.txnBegin();
        changes.add(VocabAuthz.graphForLabels, S, P, O);
        changes.txnCommit();

        Assertions.assertFalse(abac.contains(VocabAuthz.graphForLabels, S, P, O));
    }

    @Test
    public void givenAddedTriple_whenDeleting_thenTripleRemoved() {
        // First add via commit
        final RDFChangesApplyWithLabels add = new RDFChangesApplyWithLabels(labelled(abac), SECURITY_LABEL);
        add.txnBegin();
        add.add(GRAPH, S, P, O);
        add.txnCommit();
        Assertions.assertTrue(abac.contains(GRAPH, S, P, O));

        // Then delete in a new transaction
        final RDFChangesApplyWithLabels delete = new RDFChangesApplyWithLabels(labelled(abac), SECURITY_LABEL);
        delete.txnBegin();
        delete.delete(GRAPH, S, P, O);
        delete.txnCommit();
        Assertions.assertFalse(abac.contains(GRAPH, S, P, O));
    }

    @Test
    public void givenLabelsGraphQuad_whenDeleting_thenNotTreatedAsDataDelete() {
        final RDFChangesApplyWithLabels changes = new RDFChangesApplyWithLabels(labelled(abac), SECURITY_LABEL);
        changes.txnBegin();
        changes.delete(VocabAuthz.graphForLabels, S, P, O);
        changes.txnCommit();
        // No exception — labels-graph deletes are tracked internally, not as data
    }

    @Test
    public void givenTransactionBeginCommit_whenCommitting_thenChangesVisible() {
        final RDFChangesApplyWithLabels changes = new RDFChangesApplyWithLabels(labelled(abac), SECURITY_LABEL);
        changes.txnBegin();
        changes.add(GRAPH, S, P, O);
        changes.txnCommit();
        Assertions.assertTrue(abac.contains(GRAPH, S, P, O));
    }

    @Test
    public void givenTransactionBeginAbort_whenAborting_thenChangesNotVisible() {
        final RDFChangesApplyWithLabels changes = new RDFChangesApplyWithLabels(labelled(abac), SECURITY_LABEL);
        changes.txnBegin();
        changes.add(GRAPH, S, P, O);
        changes.txnAbort();
        Assertions.assertFalse(abac.contains(GRAPH, S, P, O));
    }

    @Test
    public void givenNullGraph_whenAdding_thenGraphSetToDefault() {
        final RDFChangesApplyWithLabels changes = new RDFChangesApplyWithLabels(labelled(abac), SECURITY_LABEL);
        changes.txnBegin();
        changes.add(null, S, P, O);
        changes.txnCommit();
        changes.finish();
        Assertions.assertTrue(abac.contains(Quad.defaultGraphIRI, S, P, O));
    }

    @Test
    public void givenNullGraph_whenAddingAndDeleting_thenGraphSetToDefault() {
        final RDFChangesApplyWithLabels changes = new RDFChangesApplyWithLabels(labelled(abac), SECURITY_LABEL);
        changes.txnBegin();
        changes.add(null, S, P, O);
        changes.delete(null, S, P, O);
        changes.txnCommit();
        changes.finish();
        Assertions.assertFalse(abac.contains(Quad.defaultGraphIRI, S, P, O));
    }

    @Test
    public void givenNullGraph_whenAddingAndDeleting_thenTargetGraphAffected() {
        final String distributionId = "http://distribution/1";
        final Node distGraph = NodeFactory.createURI(distributionId);
        final RDFChangesApplyWithLabels changes = new RDFChangesApplyWithLabels(labelled(abac), SECURITY_LABEL, distributionId);

        changes.txnBegin();
        changes.add(null, S, P, O);
        changes.txnCommit();
        Assertions.assertTrue(abac.contains(distGraph, S, P, O));
        changes.txnBegin();
        changes.delete(null, S, P, O);
        changes.txnCommit();
        changes.finish();
        Assertions.assertFalse(abac.contains(distGraph, S, P, O));
    }

    @Test
    public void givenDuplicateQuads_whenCommitting_thenLabelsAreAppliedAsOneBatch() {
        LabelsStore labelsStore = spy(Labels.createLabelsStoreMem());
        DatasetGraphABAC dataset = ABAC.authzDataset(DatasetGraphFactory.createTxnMem(),
                AEX.strALLOW, labelsStore, SysABAC.denyLabel, new AttributesStoreLocal());
        final RDFChangesApplyWithLabels changes = new RDFChangesApplyWithLabels(labelled(dataset), SECURITY_LABEL);

        changes.txnBegin();
        changes.add(GRAPH, S, P, O);
        changes.add(GRAPH, S, P, O);
        changes.txnCommit();

        verify(labelsStore, times(1)).addAll(any(), org.mockito.ArgumentMatchers.eq(LABEL));
        Assertions.assertEquals(labelsStore.labelForQuad(Quad.create(GRAPH, S, P, O)), LABEL);
    }

    @Test
    public void givenAbortedBatch_whenFinishing_thenLabelsAreNotApplied() {
        LabelsStore labelsStore = spy(Labels.createLabelsStoreMem());
        DatasetGraphABAC dataset = ABAC.authzDataset(DatasetGraphFactory.createTxnMem(),
                AEX.strALLOW, labelsStore, SysABAC.denyLabel, new AttributesStoreLocal());
        final RDFChangesApplyWithLabels changes = new RDFChangesApplyWithLabels(labelled(dataset), SECURITY_LABEL);

        changes.txnBegin();
        changes.add(GRAPH, S, P, O);
        changes.txnAbort();
        changes.finish();

        verify(labelsStore, times(0)).addAll(any(), org.mockito.ArgumentMatchers.eq(LABEL));
        Assertions.assertNull(labelsStore.labelForQuad(Quad.create(GRAPH, S, P, O)));
    }

    @Test
    public void givenExternalTransactionWithoutPatchTransaction_whenFinishing_thenLabelsAreApplied() {
        Txn.executeWrite(abac, () -> {
            final RDFChangesApplyWithLabels changes = new RDFChangesApplyWithLabels(labelled(abac), SECURITY_LABEL);
            changes.add(GRAPH, S, P, O);
            changes.finish();
        });

        Assertions.assertEquals(abac.labelsStore().labelForQuad(Quad.create(GRAPH, S, P, O)), LABEL);
    }

}
