package io.telicent.access;

import io.telicent.model.JsonTripleObject;

import java.util.List;

public class AccessQueryResults {

    public String subject;
    public String predicate;
    public List<JsonTripleObject> objects;

    public AccessQueryResults(AccessQuery query, List<JsonTripleObject> objects){
        this.subject = query.subject;
        this.predicate = query.predicate;
        this.objects = objects;
    }

}
