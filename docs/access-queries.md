# Access queries

There are two types of RDF triple access query endpoints available within Smart Cache Graph.

## Access query endpoint

The first allows you to query for a given subject and predicate of an RDF triple and returns the subject, predicate
and any objects found for that subject and predicate combination, provided 1) they exist in the database and 2) the
requesting user has the appropriate access - otherwise `null` is returned.

The endpoint accepts authenticated `POST` requests at `https://{hostname}/{dataset}/access/query`

Example request body:

```json
{
  "subject": "http://dbpedia.org/resource/London",
  "predicate": "http://dbpedia.org/ontology/country"
}
```

Example response body with object values found:

```json
{
  "subject": "http://dbpedia.org/resource/London",
  "predicate": "http://dbpedia.org/ontology/country",
  "objects": [
    {
      "dataType": "http://www.w3.org/2001/XMLSchema#anyURI",
      "value": "http://dbpedia.org/resource/United_Kingdom"
    },
    {
      "dataType": "http://www.w3.org/2001/XMLSchema#anyURI",
      "value": "http://dbpedia.org/resource/England"
    }
  ]
}
```

Example response body without any object values found:

```json
{
  "subject": "http://dbpedia.org/resource/London",
  "predicate": "http://dbpedia.org/ontology/birthDate",
  "objects": null
}
```

## Triple access endpoint

The second endpoint allows you to query for one of more specific RDF triple subject -> predicate -> object combinations
and receive a boolean response as to whether the requesting user has visibility of such a triple. Visibility will depend
on the triple (or triples) existing in the database and the user having the necessary access. There is no difference in
response between a triple that does not exist and a triple the user does not have access to, so the user cannot use the
endpoint to sniff for the existence of information they do not have access to.

When multiple triples are supplied in the request, it may be that the user only has visibility of some of them. In this
scenario the default response it that the visibility will be considered `false` as the user does not have visibility of
all the information. However, this can be overridden by adding a query parameter `all=false` to the request and in this
case visibility will be shown as `true` if the user has visibility of some of the triples.

The endpoint accepts authenticated `POST` requests at `https://{hostname}/{dataset}/access/triples`

Example request body:

```json
{
  "triples": [
    {
      "subject": "http://dbpedia.org/resource/London",
      "predicate": "http://dbpedia.org/ontology/populationTotal",
      "object": {
        "dataType": "xsd:integer",
        "value": "8799800"
      }
    },
    {
      "subject": "http://dbpedia.org/resource/London",
      "predicate": "http://dbpedia.org/ontology/country",
      "object": {
        "dataType": "xsd:anyURI",
        "value": "http://dbpedia.org/resource/United_Kingdom"
      }
    }
  ]
}
```

Example response body:

```json
{
  "triples": [
    {
      "subject": "http://dbpedia.org/resource/London",
      "predicate": "http://dbpedia.org/ontology/populationTotal",
      "object": [
        {
          "dataType": "xsd:integer",
          "value": "8799800"
        }
      ]
    },
    {
      "subject": "http://dbpedia.org/resource/London",
      "predicate": "http://dbpedia.org/ontology/country",
      "object": [
        {
          "dataType": "xsd:anyURI",
          "value": "http://dbpedia.org/resource/United_Kingdom"
        }
      ]
    }
  ],
  "visible": true
}
```

