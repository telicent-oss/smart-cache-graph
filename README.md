# smart-cache-graph

A Smart Cache for the RDF Dataset of the knowledge channel.

SCG (Smart Cache Graph) provides
[SPARQL](https://www.w3.org/TR/sparql-overview/) access using the
[SPARQL protocol](https://www.w3.org/TR/sparql-protocol/)
and [SPARQL Graph Store
Protocol](https://www.w3.org/TR/sparql-graph-store-protocol/)
to RDF data with [ABAC data security](https://github.com/Telicent-oss/rdf-abac/blob/main/docs/abac.md).

It is a container that consists of:

- [Apache Jena Fuseki](https://jena.apache.org/documentation/fuseki2/fuseki-main.html) server
- [Fuseki-Kafka bridge](https://github.com/telicent-oss/jena-fuseki-kafka)
- Telicent RDF ABAC data security
- Telicent GraphQL

## Example configuration

Smart Cache Graph is configured using a Fuseki configuration file
([documentation](docs/configuration-smart-cache-graph.md)). 
There is an [example config.ttl](docs/config.ttl) file.

## System Configuration

The following environment variables can be used to control Smart Cache Graph:

### `USER_ATTRIBUTES_URL`

This is the network location of
[user attribute server](https://github.com/telicent-oss/telicent-access)
which also includes the hierarchies management.

The URL value is a template including `{user}`. Example: `http://some-host/users/lookup/{user}`

### `JWKS_URL`

This specifies the JSON Web Key Set (JWKS) URL to use to obtain
the public keys for verifying JSON Web Tokens (JWTs). The value "disabled"
turns off token verification.

## Build

Building Smart Cache Graph is a two-step process.

The java artifacts are built using the maven release plugin. When these are
released, the docker container is automatically built.

Check versions in `release-setup`.

### Build and release the smart cache graph maven artifacts

On branch `main`:

Edit and commit `release-setup` to set the correct versions.

```
    source release-setup
```

This prints the dry-run command.

If you need to change this file, edit it, then simply source the file again.

Dry run

```
    mvn $MVN_ARGS -DdryRun=true release:clean release:prepare
```

and for real

```
    mvn $MVN_ARGS release:clean release:prepare
    mvn $MVN_ARGS release:perform
```

This updates the version number.

After release, do `git pull` to sync local and remote git repositories.

To rebuild for update version for development:

```
    mvn clean install
```

The maven artifacts have been uploaded to the maven repository
configured in the POM file.

### About the Docker Container

The docker container is automatically built by github action on a release of the
Smart Cache Graph jar artifacts.

In the docker container we have:

```
    /fuseki/logs/
    /fuseki/databases/
    /fuseki/config/
```

and configuration files go into host `mnt/config/`.

#### Try it! Docker run

Script `d-run` runs the container. A number of directories are mapped to the host
filesystem including `config/`.

Set the environment variables in `d-run` for the image required and fetch the
image into the local docker repository.

Example configuration:

```
    d-run --config config/config-replay-abac.ttl
```

The configuration file `config/config-replay-abac.ttl` is on the host server. It is
for an in-memory dataset at "/ds" which replays the "RDF" topic on start-up.
Kafka must be up and running.

The "mnt" area is mapped into the docker container. The configuration file is
`./mnt/config/config-replay-abac.ttl` in the host. The `mnt/` is not included as
an argument to the container.

The directory `mnt/databases`, which is created by `d-run` if necessary, is the
TDB databases if persistent and also the Kafka offset tracking files
(`*.state`). This directory must be writable by the container; one way is to set the
access to `a+rwx` on the host.

This command assumes Kafka is running.

The Fuseki server is available at `http://localhost:3030/ds`.

Open Telemetry for SCG will be enabled if any environment variables with `OTEL` in the
name are present at runtime.  If this is not the case then the Open Telemetry Agent is
not attached to the JVM and no metrics/traces will be exported.
