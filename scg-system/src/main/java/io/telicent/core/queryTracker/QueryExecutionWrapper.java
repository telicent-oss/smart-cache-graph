package io.telicent.core.queryTracker;

import org.apache.jena.atlas.json.JsonArray;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.util.Context;

import java.util.Iterator;

import static io.telicent.core.queryTracker.QueryMapper.add;

public class QueryExecutionWrapper implements QueryExecution {

    QueryExecution underlyingExecution;
    Long id;

    public QueryExecutionWrapper(Long id, QueryExecution queryExecution) {
        underlyingExecution = queryExecution;
        this.id = id;
        add(id, queryExecution);
    }

    @Override
    public void abort() {
        QueryMapper.abort(id);
        underlyingExecution.abort();
    }

    @Override
    public void close() {
        QueryMapper.close(id);
        underlyingExecution.close();
    }

    /**
     * Call the underlying for all the other calls
     */

    @Override
    public Dataset getDataset() {
        return underlyingExecution.getDataset();
    }

    @Override
    public Context getContext() {
        return underlyingExecution.getContext();
    }

    @Override
    public Query getQuery() {
        return underlyingExecution.getQuery();
    }

    @Override
    public String getQueryString() {
        return underlyingExecution.getQueryString();
    }

    @Override
    public ResultSet execSelect() {
        return underlyingExecution.execSelect();
    }

    @Override
    public Model execConstruct() {
        return underlyingExecution.execConstruct();
    }

    @Override
    public Model execConstruct(Model model) {
        return underlyingExecution.execConstruct(model);
    }

    @Override
    public Iterator<Triple> execConstructTriples() {
        return underlyingExecution.execConstructTriples();
    }

    @Override
    public Iterator<Quad> execConstructQuads() {
        return underlyingExecution.execConstructQuads();
    }

    @Override
    public Dataset execConstructDataset() {
        return underlyingExecution.execConstructDataset();
    }

    @Override
    public Dataset execConstructDataset(Dataset dataset) {
        return underlyingExecution.execConstructDataset(dataset);
    }

    @Override
    public Model execDescribe() {
        return underlyingExecution.execDescribe();
    }

    @Override
    public Model execDescribe(Model model) {
        return underlyingExecution.execDescribe(model);
    }

    @Override
    public Iterator<Triple> execDescribeTriples() {
        return underlyingExecution.execDescribeTriples();
    }

    @Override
    public boolean execAsk() {
        return underlyingExecution.execAsk();
    }

    @Override
    public JsonArray execJson() {
        return underlyingExecution.execJson();
    }

    @Override
    public Iterator<JsonObject> execJsonItems() {
        return underlyingExecution.execJsonItems();
    }

    @Override
    public boolean isClosed() {
        return underlyingExecution.isClosed();
    }

    @Override
    public long getTimeout1() {
        return underlyingExecution.getTimeout1();
    }

    @Override
    public long getTimeout2() {
        return underlyingExecution.getTimeout2();
    }
}
