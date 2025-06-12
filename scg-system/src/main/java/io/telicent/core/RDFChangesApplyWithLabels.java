package io.telicent.core;

import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.core.VocabAuthz;
import io.telicent.jena.abac.labels.Label;
import org.apache.jena.graph.Node;
import org.apache.jena.kafka.utils.RDFChangesApplyExternalTransaction;
import org.apache.jena.query.TxnType;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.sparql.graph.GraphTxn;

/**
 * A {@link org.apache.jena.rdfpatch.RDFChanges} implementation that honours the transaction semantics of
 * {@link RDFChangesApplyExternalTransaction} as well as updating the {@link io.telicent.jena.abac.labels.LabelsStore}
 * for a {@link DatasetGraphABAC} appropriately
 */
class RDFChangesApplyWithLabels extends RDFChangesApplyExternalTransaction {

    private final Label securityLabel;
    private final DatasetGraphABAC datasetABAC;
    private final GraphTxn labelsGraph = GraphFactory.createTxnGraph();

    public RDFChangesApplyWithLabels(DatasetGraphABAC dsgz,
                                     Label securitylabel) {
        super(dsgz);
        this.securityLabel = securitylabel;
        this.datasetABAC = dsgz;
        this.labelsGraph.begin(TxnType.WRITE);
    }

    @Override
    public void add(Node g, Node s, Node p, Node o) {
        if (VocabAuthz.graphForLabels.equals(g)) {
            // If quad is for labels graph just track that for now
            this.labelsGraph.add(s, p, o);
        } else {
            super.add(g, s, p, o);

            // Apply specific security label if there is one, if not we're relying on the dataset default label applying
            // at read time
            if (securityLabel != null) {
                this.datasetABAC.labelsStore().add(s, p, o, securityLabel);
            }
        }
    }

    @Override
    public void delete(Node g, Node s, Node p, Node o) {
        if (VocabAuthz.graphForLabels.equals(g)) {
            // If quad is for labels graph just update the labels graph state
            this.labelsGraph.delete(s, p, o);
        } else {
            // Otherwise remove the quad
            // NB - While there is a remove() method on LabelsStore we intentionally don't use it because otherwise a
            //      malicious data producer could remove labels from data by creating a patch that deleted and then re-added
            //      triples
            super.delete(g, s, p, o);
        }
    }

    @Override
    public void txnBegin() {
        // Begin a new transaction first
        super.txnBegin();

        // Begin a transaction on the labels graph
        if (!this.labelsGraph.isInTransaction()) {
            this.labelsGraph.begin(TxnType.WRITE);
        }
    }

    @Override
    public void txnCommit() {
        // Commit and apply the labels graph first
        this.labelsGraph.commit();
        applyLabelsGraph();

        // Then apply the commit as normal
        super.txnCommit();
    }

    private void applyLabelsGraph() {
        if (!this.labelsGraph.isEmpty()) {
            this.datasetABAC.labelsStore().addGraph(this.labelsGraph);
        }
    }

    @Override
    public void txnAbort() {
        // Abort any changes to the labels graph first
        this.labelsGraph.abort();

        // Then apply the abort as normal
        super.txnAbort();
    }

    @Override
    public void finish() {
        // Upon finish apply the final state of the labels graph
        applyLabelsGraph();
        super.finish();
    }
}
