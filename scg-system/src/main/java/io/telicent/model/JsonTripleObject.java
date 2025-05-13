package io.telicent.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

public class JsonTripleObject {

    @JsonInclude(Include.NON_NULL)
    public String dataType;
    public String value;
    @JsonInclude(Include.NON_NULL)
    public String language;

    public JsonTripleObject(){};

    public JsonTripleObject(String dataType, String value) {
        this.dataType = dataType;
        this.value = value;
    }

    public JsonTripleObject(String dataType, String value, String language){
        this.dataType = dataType;
        this.value = value;
        this.language = language;
    }

}
