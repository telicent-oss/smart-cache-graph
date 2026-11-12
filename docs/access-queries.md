# Access queries

There are three types of RDF triple access query endpoints available within Smart Cache Graph.

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
      "value": "http://dbpedia.org/resource/United_Kingdom"
    },
    {
      "value": "http://dbpedia.org/resource/England"
    }
  ]
}
```

Object values that are resource URIs omit `dataType`. Literal values include `dataType`, and literal URIs use
`http://www.w3.org/2001/XMLSchema#anyURI`.
Blank node objects are returned as values like `_:label` with no `dataType`.

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
          "value": "http://dbpedia.org/resource/United_Kingdom"
        }
      ]
    }
  ],
  "visible": true
}
```

## Access labels endpoint

`POST https://{hostname}/{dataset}/access/labels` returns the security labels of the quads the user can see. It takes
the same request and returns the same response as the admin endpoint `/$/labels/{dataset}` ([API docs](labels-api.yaml)),
but filters by the user's attributes:

- Only quads the user can see are returned. A hidden quad and a non-existent one give the same empty response.
- Unlabelled quads use the dataset's default label (`authz:tripleDefaultAttributes`).
- The labels graph (`http://telicent.io/security#labels`) is never returned.
- Always enabled. It needs the same roles and permissions as the other `/{dataset}/access/*` endpoints.

The request has a `quads` or `triples` array (not both). Each entry has `subject`, `predicate`, `object.value` and an
optional `graph`, and any of these can be `*`. Without `graph`, only the default graph is searched. With `"graph": "*"`,
all graphs are searched. The object is read as:

- `*`: any object.
- With `dataType` (e.g. `xsd:integer` or a full URI): a typed literal.
- With `language` (e.g. `en`): a language tagged literal.
- With neither: a URI if it starts with `http://` or `https://`, otherwise a plain string literal.

Both `dataType` and `language`, an unknown data type, or a malformed body give `400 Bad Request`.

Example request:

```json
{
  "triples": [
    {
      "graph": "*",
      "subject": "http://dbpedia.org/resource/London",
      "predicate": "*",
      "object": { "value": "*" }
    }
  ]
}
```

Example response, for a user who can see London's country but not its population (`graph` is omitted for the default
graph):

```json
{
  "results": [
    {
      "subject": "http://dbpedia.org/resource/London",
      "predicate": "http://dbpedia.org/ontology/country",
      "object": "http://dbpedia.org/resource/United_Kingdom",
      "labels": [ "everyone" ]
    }
  ]
}
```

### Difference from `/$/labels/{dataset}`

`/$/labels/{dataset}` ignores `dataType` and `language`. This endpoint honours them, because it searches the user's
view of the data, where the object must match the stored term exactly.

**Known gap:** a literal requested without its `dataType`/`language` is treated as a plain string. It won't match a
typed or language tagged literal, so it returns no results, while `/$/labels/{dataset}` would return the label. Supply
the `dataType`/`language`, or use `*` for the object. Nothing leaks: the response is the same as for hidden data.
