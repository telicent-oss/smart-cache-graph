This file describes some testing for the Smart Cache Graph container.

## Testing the container without Kafka

Set-up: in-memory DB, local attribute store, no Kafka connector
with test data in `Test/`

```
d-run --config config/config-abac-local.ttl
```

## Client calls:

```
URL="http://localhost:3030/ds"
```

Some basic tokens:

```
# user: user1
TOKEN="VW5zZXQ.eyBlbWFpbDogInVzZXIxIn0.VW5zZXQ"
```
(one result)

```
# user: employee
TOKEN="VW5zZXQ.eyBlbWFpbDogImVtcGxveWVlIn0.VW5zZXQ"
```
(no attributes - rejected)

```
# user: contractor
TOKEN="VW5zZXQ.eyBlbWFpbDogImNvbnRyYWN0b3IifQ.VW5zZXQ"
(no attributes - rejected)
```

```
   curl -XPOST --data-binary @Test/data1.trig \
        --header 'Content-type: application/trig' \
        $URL/upload
```
```
   curl --header "x-amzn-oidc-data: Bearer: $TOKEN" \
        -d query="SELECT * { ?s ?p ?o}"
$URL/sparql
```

## Testing with Kafka

```
   d-run --config config/config-replay-abac.ttl
```
