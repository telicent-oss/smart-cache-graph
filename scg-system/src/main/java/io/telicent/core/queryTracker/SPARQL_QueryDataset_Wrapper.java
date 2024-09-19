package io.telicent.core.queryTracker;

import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.fuseki.servlets.SPARQL_QueryDataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.sparql.core.DatasetGraph;

import static io.telicent.core.queryTracker.QueryMapper.queryMapper;

public class SPARQL_QueryDataset_Wrapper extends SPARQL_QueryDataset {

    SPARQL_QueryDataset underlyingDataset;

    public SPARQL_QueryDataset_Wrapper(SPARQL_QueryDataset underlying) {
        underlyingDataset = underlying;
    }

    @Override
    protected QueryExecution createQueryExecution(HttpAction action, Query query, DatasetGraph dataset) {
        QueryExecution execution = super.createQueryExecution(action, query, dataset);
        QueryExecutionWrapper wrapper = new QueryExecutionWrapper(action.id, execution);
        queryMapper.put(action.id, wrapper);
        return wrapper;
    }
}