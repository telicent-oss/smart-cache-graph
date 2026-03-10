# Ontology-Aware SPARQL Queries

Smart Cache Graph stores data in a `knowledge` dataset and ontology in an `ontology` dataset as
separate Fuseki services. By default, SPARQL queries against `knowledge` have no awareness of the
ontology, so a query for instances of `:Vehicle` will not return instances of `:Ship` even if the
ontology declares `:Ship rdfs:subClassOf :Vehicle`.

This document describes the available options for making queries ontology-aware.

---

## Option 1: SPARQL Federation with Property Paths

Use SPARQL `SERVICE` to pull the subclass hierarchy from the ontology service at query time, then
match instances in the knowledge dataset.

```sparql
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

SELECT ?instance WHERE {
  SERVICE <http://localhost:3030/ontology/sparql> {
    ?type rdfs:subClassOf* <http://example.org/vehicle> .
  }
  ?instance rdf:type ?type .
}
```

The `rdfs:subClassOf*` path traverses the full class hierarchy (zero or more hops), so it matches
both the queried class itself and all of its subclasses.

**Pros**
- Works with the current configuration, no server changes required
- Ontology and knowledge datasets remain independent

**Cons**
- Every query must include the `SERVICE` clause
- Performance degrades with large or deeply nested ontologies
- Only covers `rdfs:subClassOf`/`rdfs:subPropertyOf`; does not provide full OWL inference

---

## Option 2: Fuseki Inference Model

Add a third Fuseki service that presents an RDFS- or OWL-inferred view of the combined knowledge
and ontology data. Jena's assembler vocabulary supports this via `ja:InfModel`.

Add the following to your Fuseki configuration file:

```turtle
PREFIX ja:     <http://jena.hpl.hp.com/2005/11/Assembler#>
PREFIX fuseki: <http://jena.apache.org/fuseki#>
PREFIX rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>

:inferredService rdf:type fuseki:Service ;
    fuseki:name "/inferred" ;
    fuseki:endpoint [ fuseki:operation fuseki:query ; fuseki:name "sparql" ] ;
    fuseki:dataset :inferredDataset ;
    .

## An inference model that combines both base datasets and applies RDFS reasoning.
:inferredDataset rdf:type ja:InfModel ;
    ja:baseModel :combinedModel ;
    ja:reasoner [
        ja:reasonerURL <http://jena.hpl.hp.com/2003/RDFSExptRuleReasoner>
    ] .

## A union of knowledge and ontology base models.
:combinedModel rdf:type ja:OntModel ;
    ja:imports :datasetBase ;
    ja:imports :ontologyDatasetBase ;
    .
```

Queries against `/inferred/sparql` are then plain SPARQL with no special syntax required:

```sparql
SELECT ?instance WHERE {
  ?instance rdf:type <http://example.org/vehicle> .
}
```

**Pros**
- Transparent to query writers; standard SPARQL just works
- Supports configurable reasoning levels (RDFS, OWL Mini, OWL Full)

**Cons**
- Jena in-memory inference is computed when the model is loaded, not incrementally; large datasets
  will be slow to initialise
- Does **not** integrate with ABAC security labelling — inferred triples carry no security labels,
  so this endpoint cannot enforce the same access controls as the `/knowledge` endpoint
- Requires a separate, unprotected service endpoint

---

## Option 3: Materialise Inferences in the Data Pipeline (Recommended for Production)

Run a reasoning step as part of the Kafka-based ingestion pipeline. When new data arrives, a
pipeline component:

1. Reads the current ontology from the `ontology` dataset
2. Applies RDFS or OWL inference using Jena's `InfModel`
3. Publishes the inferred triples (e.g. `?ship rdf:type :vehicle`) back onto the Kafka topic with
   appropriate security labels

The inferred triples are then ingested into the `knowledge` dataset via the normal upload path and
are subject to full ABAC enforcement. Queries remain plain SPARQL against `/knowledge`.

```sparql
SELECT ?instance WHERE {
  ?instance rdf:type <http://example.org/vehicle> .
}
```

**Pros**
- Best query performance; no reasoning overhead at query time
- ABAC security labels apply correctly to inferred triples
- Scales well with large datasets
- Reasoning level (RDFS, OWL) is configurable in the pipeline component

**Cons**
- Adds pipeline complexity
- Inferences become stale if the ontology changes; a re-reasoning step is required to regenerate
  the inferred triples for existing data

---

## Option 4: Load Ontology as a Named Graph in Knowledge

Copy the ontology into the `knowledge` dataset as a named graph (e.g. `<urn:graph:ontology>`).
Queries can then use SPARQL property paths within the same dataset:

```sparql
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

SELECT ?instance WHERE {
  GRAPH <urn:graph:ontology> {
    ?type rdfs:subClassOf* <http://example.org/vehicle> .
  }
  ?instance rdf:type ?type .
}
```

**Pros**
- Single dataset; no federation or separate service required
- No server configuration changes

**Cons**
- Loses the separation between ontology and instance data
- Requires a mechanism to keep the ontology graph in sync when the ontology changes
- Limited to `rdfs:subClassOf`/`rdfs:subPropertyOf` traversal; no full OWL inference

---

## Comparison Summary

| | Option 1: Federation | Option 2: Inference Model | Option 3: Materialise | Option 4: Named Graph |
|---|---|---|---|---|
| Server config changes | None | New service required | None | None |
| Pipeline changes | None | None | New component required | Sync mechanism required |
| ABAC enforcement | Inherits from `/knowledge` | No | Yes | Inherits from `/knowledge` |
| Query complexity | High (SERVICE clause) | None | None | Medium (GRAPH clause) |
| Inference depth | RDFS subclass only | RDFS or OWL | RDFS or OWL | RDFS subclass only |
| Query performance | Low (runtime federation) | Medium (load-time reasoning) | High | Medium (runtime traversal) |
| Best suited for | Prototyping / ad-hoc queries | Small-scale, no ABAC | Production | Simple hierarchies only |

## SQL to create permission and add to user
```sql
INSERT INTO permissions (id, name, description, active, action, resource, created_at, updated_at)
VALUES ('f3dd0625-1008-4f02-b7ff-4e5912427e9a', 'api.inferred.read', 'Read from the inferred dataset', true, 'read', 'core', NOW(), NOW());

UPDATE users SET permissions = permissions || '["f3dd0625-1008-4f02-b7ff-4e5912427e9a"]' WHERE email = 'rob.walpole+system-integration.telicent-sandbox.admin@telicent.io';
```

## SQL to remove permission from user and delete
```sql
UPDATE users SET permissions = permissions - 'f3dd0625-1008-4f02-b7ff-4e5912427e9a' WHERE email = 'rob.walpole+system-integration.telicent-sandbox.admin@telicent.io';

DELETE FROM permissions WHERE id = 'f3dd0625-1008-4f02-b7ff-4e5912427e9a';
```