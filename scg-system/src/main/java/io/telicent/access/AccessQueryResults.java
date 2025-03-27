package io.telicent.access;

import java.util.List;

public class AccessQueryResults {

    public String subject;
    public String predicate;
    public List<String> objects;

    public AccessQueryResults(AccessQuery query, List<String> objects){
        this.subject = query.subject;
        this.predicate = query.predicate;
        this.objects = objects;
    }

}
