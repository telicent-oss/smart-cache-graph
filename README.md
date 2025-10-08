# smart-cache-graph

A Smart Cache for the RDF Dataset of the knowledge channel.

SCG (Smart Cache Graph) provides [SPARQL](https://www.w3.org/TR/sparql-overview/) access using the [SPARQL
protocol](https://www.w3.org/TR/sparql-protocol/) and [SPARQL Graph Store
Protocol](https://www.w3.org/TR/sparql-graph-store-protocol/) to RDF data with [ABAC data
security](https://github.com/Telicent-oss/rdf-abac/blob/main/docs/abac.md).

It is a container that consists of:

- [Apache Jena Fuseki](https://jena.apache.org/documentation/fuseki2/fuseki-main.html) server
- [Fuseki-Kafka bridge](https://github.com/telicent-oss/jena-fuseki-kafka)
    - Please refer to the `README.md` in that repository if you require additional Kafka configuration e.g. for
      Kafka Authentication.
- JSON Web Token (JWT) based [authentication](#jwks_url)
- Telicent RDF ABAC data security
- Telicent [Authorization policies](#authorization-policies)
- Telicent [GraphQL](https://github.com/telicent-oss/graphql-jena) extensions

## Example configuration

Smart Cache Graph is configured using a Fuseki configuration file
([documentation](docs/configuration-smart-cache-graph.md)). There is an [example config.ttl](docs/config.ttl) file.

You can find further example configurations later under [Try It Out](#try-it-out-).

## System Configuration

The following environment variables can be used to control Smart Cache Graph:

### `USER_ATTRIBUTES_URL`

This is the network location of [user attribute server](https://github.com/telicent-oss/telicent-access) which also
includes the hierarchies' management.

The URL value is a template including `{user}`. Example: `http://some-host/users/lookup/{user}`

### `JWKS_URL`

This specifies the JSON Web Key Set (JWKS) URL to use to obtain the public keys for verifying JSON Web Tokens (JWTs).
The value "disabled" turns off token verification.

### `USERINFO_URL`

From `0.91.0` onwards configured the User Info lookup URL that is used to exchange the authenticated JWT for a User Info
response, this is used to help enforce Telicent Authorization policies.  If authentication is [disabled](#jwks_url) then
this has no effect.

This should be set to the `/userinfo`, or equivalent endpoint, of your OAuth 2/OIDC compatible Identity Provider which
is issuing the JWTs used to authenticate users to Smart Cache Graph.

### `FEATURE_FLAG_AUTHZ`

From `0.91.0` onwards if set to `false` then disables the Telicent Authorization policy features.  Note that this form
of Authorization only applies if authentication is [enabled](#jwks_url).

#### Authorization Policies

Since `0.91.0` the Telicent Authorization policy feature enforces that authenticated users require specific roles and
permissions in order to access different endpoints provided by the server.  With this being determined from both the
information in the authenticated JWT and by the [User Info](#userinfo_url) obtained from the configured `/userinfo`
endpoint of the OAuth 2/OIDC compliant identity provider.

All endpoints require either the `USER` or `ADMIN_SYSTEM` roles, and additionally require specific permissions depending
on the endpoint.

For each dataset configured via the Fuseki configuration file all configured endpoints will have authorization policy
dynamically defined for them:

- If the endpoint has a known Fuseki `Operation` registered for it then permissions are `api.<dataset>.read` for
  read-only operations, or `api.<dataset>.read` and `api.<dataset>.write` for read/write operations.
- The catch all `/<dataset>` endpoint requires both `api.<dataset>.read` and `api.<dataset>.write` permissions since
  with that endpoint the request is dynamically dispatched to the appropriate endpoint based upon the request method and
  body.
- If the operation is unknown no specific policy is applied, internally this causes these endpoints to default to the
  `DENY_ALL` policy.

Please refer to the `SCG_AuthPolicy` class for what Fuseki `Operation`s are considered read-only, versus read/write.

For other endpoints provided by the various Telicent modules added to Fuseki the endpoints the following policies apply:

| Endpoint               | Roles Required | Permissions Required                                |
|------------------------|----------------|-----------------------------------------------------|
| `/$/backups/create`    | `ADMIN_SYSTEM` | `backup.write`                                      |
| `/$/backups/delete`    | `ADMIN_SYSTEM` | `backup.delete`                                     |
| `/$/backups/restore`   | `ADMIN_SYSTEM` | `backup.restore`                                    |
| `/$/backups/*`         | `ADMIN_SYSTEM` | `backup.read`                                       |
| `/$/compactall`        | `ADMIN_SYSTEM` | `api.<dataset>.compact` for all configured datasets |
| `/$/compact/<dataset>` | `ADMIN_SYSTEM` | `api.<dataset>.compact`                             |
| `/$/labels/<dataset>`  | `USER`         | `api.<dataset>.read`                                |
| `/<dataset>/access/*`  | `USER`         | `api.<dataset>.read`                                |

If your Identity Provider is not able to manage roles and permissions information in a way compatible with Smart Cache
Graph then you can disable this via the aforementioned [`FEATURE_FLAG_AUTHZ`](#feature_flag_authz) environment variable.
If you disable this you may wish to limit access to these endpoints via other mechanisms available in your deployment
environment, e.g. service mesh policy, proxy server rules etc.

### `ENABLE_LABELS_QUERY`

Setting this to `true` will enable the security label query endpoint at `http://{hostname}/$/labels/{datasetName}`. More
information about this endpoint can be found in the [API docs](docs/labels-api.yaml). You can also run a Docker
container with the endpoint enabled which can be accessed from the API docs by running:

```commandline
scg-docker/docker-run.sh --config config/config-labels-query-test.ttl
```

To populate this instance with sample security labelled data you can run:

```commandline
curl --location 'http://localhost:3030/securedDataset1/upload' --header 'Security-Label: !' --header 'Content-Type: application/trig' --data-binary '@scg-system/src/test/files/sample-data-labelled-1.trig'
curl --location 'http://localhost:3030/securedDataset2/upload' --header 'Security-Label: !' --header 'Content-Type: application/trig' --data-binary '@scg-system/src/test/files/sample-data-labelled-2.trig'
```

You can then query these endpoints for label data, e.g. for `securedDataset1`:

```commandline
curl --location 'http://localhost:3030/$/labels/securedDataset1' \
--header 'Content-Type: application/json' \
--header 'Authorization: ••••••' \
--data '{
    "triples":[
        {
            "subject": "http://dbpedia.org/resource/London",
            "predicate": "http://dbpedia.org/ontology/country",
            "object": {
                "value": "http://dbpedia.org/resource/United_Kingdom"
            }
        }
    ]
}'
```

Which should return the following:

```json
{
    "results": [
        {
            "subject": "http://dbpedia.org/resource/London",
            "predicate": "http://dbpedia.org/ontology/country",
            "object": "http://dbpedia.org/resource/United_Kingdom",
            "labels": [
                "everyone"
            ]
        }
    ]
}
```

Or to query `securedDataset2`:

```commandline
curl --location 'http://localhost:3030/$/labels/securedDataset2' \
--header 'Content-Type: application/json' \
--header 'Authorization: ••••••' \
--data '{
    "triples":[
        {
            "subject": "http://dbpedia.org/resource/Birmingham",
            "predicate": "http://dbpedia.org/ontology/populationTotal",
            "object": {
                "value": 2919600,
                "dataType": "xsd:nonNegativeInteger"
            }
        }
    ]
}'
```

Which should return the following:

```json
{
    "results": [
        {
            "subject": "http://dbpedia.org/resource/Birmingham",
            "predicate": "http://dbpedia.org/ontology/populationTotal",
            "object": "\"2919600\"",
            "labels": [
                "census",
                "admin"
            ]
        }
    ]
}
```

## Build

Building Smart Cache Graph is a two-step process.

The java artifacts are built using the maven release plugin. When these are released, the docker container is
automatically built.

Check versions in `release-setup`.

### Build and release the smart cache graph maven artifacts

On branch `main`:

Edit and commit `release-setup` to set the correct versions.

```
source release-setup
```

This prints the dry-run command.  If you need to change this file, edit it, then simply source the file again.

Dry run:

```
mvn $MVN_ARGS -DdryRun=true release:clean release:prepare
```

and for real:

```
mvn $MVN_ARGS release:clean release:prepare
```

This updates the version number.  Our automated GitHub Actions pipelines handles publishing the release build to Maven
Central and Docker Hub.

After release, do `git pull` to sync local and remote git repositories.

To rebuild for update version for development:

```
mvn clean install
```

### About the Docker Container

The docker container is automatically built by github action on a release of the Smart Cache Graph jar artifacts.

In the docker container we have:

```
    /fuseki/logs/
    /fuseki/databases/
    /fuseki/config/
```

and configuration files go into host `mnt/config/`.

### Try it out! 

The provided script, [latest-docker-run.sh](scg-docker/latest-docker-run.sh), runs the latest published image of SCG in
a docker container, with the contents of the local [mnt/config](scg-docker/mnt/config) directory mounted into the newly
generated docker image for ease of use. Similarly, the [mnt/databases](scg-docker/mnt/databases) and
[mnt/logs](scg-docker/mnt/logs) are mounted for easier analysis.

#### Example configuration - *Default*

```bash
   scg-docker/latest-docker-run.sh
```
Passing no parameters means that it will default to (`"--mem /ds"`)

It specifies an in-memory dataset at "/ds" which replays the "RDF" topic on start-up. It assumes that Kafka must be up
and running, prior to launch.

The Fuseki server is available at `http://localhost:3030/ds`.

#### Example configuration - *ABAC*
```bash
   scg-docker/latest-docker-run.sh --config config/config-local-abac.ttl
```
This runs the server using the configuration file [config-abac-local.ttl](scg-docker/mnt/config/config-abac-local.ttl.
It specifies an in-memory dataset at `/ds` and that Attribute Based Access Control is enabled.

*Note:* See caveat below re: authentication.


#### Example configuration - *Kafka Replay* 

```bash
   scg-docker/latest-docker-run.sh --config config/config-replay-abac.ttl
```
As this suggests, this runs server using the configuration file `config/config-replay-abac.ttl` 
or [config-replay-abac.ttl](scg-docker/mnt/config/config-replay-abac.ttl) as it's known locally. 

It specifies an in-memory dataset at "/ds" which replays the "RDF" topic on start-up. It assumes that Kafka must be up
and running, prior to launch.

The Fuseki server is available at `http://localhost:3030/ds`.

#### More advanced testing - docker-run.sh & d-run
To run the local instance you can use other scripts. You will need `mvn` installed in order to build the code (as
described [above](#build)).

You can then run `docker-run.sh` to use the newly built images.
```bash
   scg-docker/docker-run.sh
```
It uses the same parameters as the latest-docker-run. sh script above.

Alternately, you can use the script `d-run` which will map the relevant config and database directories from the local
filesystem, pulling down the given image and running it directly (not in `-d` mode). It requires a number of environment
variables to be set as indicated in the script.   

It can be run with exactly the same configuration as latest-docker-run.sh except with no default configuration if
nothing is provided.

#### Open Telemetry

Open Telemetry for SCG will be enabled if any environment variables with `OTEL` in the name are present at runtime.  If
this is not the case then the Open Telemetry Agent is not attached to the JVM and no metrics/traces will be exported.
