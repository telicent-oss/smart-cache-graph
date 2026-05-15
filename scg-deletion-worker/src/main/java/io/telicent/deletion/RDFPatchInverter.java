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
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.changes.RDFChangesCollector;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.Quad;

import java.util.Iterator;

public class RDFPatchInverter {

    public RDFPatch invert(DatasetGraph dsg) {
        if (dsg.isEmpty()) return null;

        RDFChangesCollector collector = new RDFChangesCollector();
        collector.txnBegin();

        Iterator<Quad> quads = dsg.find();
        while (quads.hasNext()) {
            Quad quad = quads.next();
            Node g = resolveGraph(quad.getGraph());
            collector.delete(g, quad.getSubject(), quad.getPredicate(), quad.getObject());
        }

        collector.txnCommit();

        return collector.getRDFPatch();
    }
    /**
     * Normalises the graph node — the default graph has two possible
     * representations in Jena, both of which we map to the patch default.
     */
    private Node resolveGraph(Node graph) {
        if (graph == null
                || graph.equals(Quad.defaultGraphIRI)
                || graph.equals(Quad.defaultGraphNodeGenerated)) {
            return Quad.defaultGraphIRI;
        }
        return graph;
    }
}
