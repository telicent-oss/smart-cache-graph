package io.telicent.core;

import io.telicent.smart.cache.configuration.Configurator;
import org.apache.jena.query.Query;
import org.apache.jena.rdfpatch.system.DatasetGraphChanges;
import org.apache.jena.sparql.algebra.Algebra;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.OpLib;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.engine.Plan;
import org.apache.jena.sparql.engine.QueryEngineFactory;
import org.apache.jena.sparql.engine.QueryEngineRegistry;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.engine.main.QueryEngineMain;
import org.apache.jena.sparql.mgt.Explain;
import org.apache.jena.sparql.util.Context;

import java.util.function.BooleanSupplier;

/**
 * A query engine for security labelled datasets that routes default graph queries to the union of all named
 * graphs.
 * <p>
 * Activated by the {@value FMod_DistributionLifecycle#ROUTE_TO_NAMED_GRAPHS} environment variable. When the variable is
 * absent or {@code false}, the factory will not accept queries and Jena's standard {@code QueryEngineMain} handles them
 * instead. When {@code true}, default-graph queries are resolved against the union of all named graphs.
 */
public class CQRSQueryEngine extends QueryEngineMain {

    /**
     * Reads {@value FMod_DistributionLifecycle#ROUTE_TO_NAMED_GRAPHS} from the configuration
     */
    static BooleanSupplier ROUTING_CHECK =
            () -> Configurator.get(FMod_DistributionLifecycle.ROUTE_TO_NAMED_GRAPHS, Boolean::parseBoolean, false);

    public CQRSQueryEngine(Query query, DatasetGraph dsg, Binding input, Context context) {
        super(query, dsg, input, context);
    }

    public CQRSQueryEngine(Op op, DatasetGraph dsg, Binding input, Context context) {
        super(op, dsg, input, context);
    }

    /**
     * Converts the optimised algebra to quad form and applies {@link OpLib#unionDefaultGraphQuads} so that default
     * graph patterns are resolved against the union of all named graphs.
     */
    @Override
    protected Op modifyOp(Op op) {
        op = super.modifyOp(op);
        op = OpLib.unionDefaultGraphQuads(Algebra.toQuadForm(op));
        Explain.explain("REWRITE(Union default graph)", op, context);
        return op;
    }

    // ---- Factory

    private static final QueryEngineFactory
            factory = new CQRSQueryEngineFactory();

    public static QueryEngineFactory getFactory() {
        return factory;
    }

    /**
     * Register with the global {@link QueryEngineRegistry}.
     */
    public static void register() {
        if (!QueryEngineRegistry.containsFactory(factory)) {
            QueryEngineRegistry.addFactory(factory);
        }
    }

    /**
     * Remove from the global {@link QueryEngineRegistry}.
     */
    public static void unregister() {
        QueryEngineRegistry.removeFactory(factory);
    }

    static class CQRSQueryEngineFactory implements QueryEngineFactory {

        @Override
        public boolean accept(Query query, DatasetGraph dsg, Context context) {
            return dsg instanceof DatasetGraphChanges && ROUTING_CHECK.getAsBoolean();
        }

        @Override
        public Plan create(Query query, DatasetGraph dsg, Binding input, Context context) {
            return new CQRSQueryEngine(query, dsg, input, context).getPlan();
        }

        @Override
        public boolean accept(Op op, DatasetGraph dsg, Context context) {
            return dsg instanceof DatasetGraphChanges && ROUTING_CHECK.getAsBoolean();
        }

        @Override
        public Plan create(Op op, DatasetGraph dsg, Binding input, Context context) {
            return new CQRSQueryEngine(op, dsg, input, context).getPlan();
        }
    }
}
