package io.telicent.core.queryTracker;

import org.apache.jena.query.QueryExecution;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class QueryMapper {
    // Maps for running, closed and aborted queries
    public static Map<Long, QueryExecution> queryMapper = new ConcurrentHashMap<>();
    public static Map<Long, QueryExecution> abortMapper = new ConcurrentHashMap<>();
    public static Map<Long, QueryExecution> closedMapper = new ConcurrentHashMap<>();

    public static void abort(Long id) {
        QueryExecution value = queryMapper.remove(id);
        if (value != null) {
            abortMapper.put(id, value);
        }
    }
    public static void close(Long id) {
        QueryExecution value = queryMapper.remove(id);
        if (value != null) {
            closedMapper.put(id, value);
        }
    }

    public static void add(Long id, QueryExecution value) {
        queryMapper.put(id, value);
    }
}
