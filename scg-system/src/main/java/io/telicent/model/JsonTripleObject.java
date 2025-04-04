package io.telicent.model;

public class JsonTripleObject {

    public String dataType;
    public String value;

    public JsonTripleObject(){};

    public JsonTripleObject(String dataType, String value){
        this.dataType = dataType;
        this.value = value;
    }

}
