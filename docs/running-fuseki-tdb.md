# Notes on operating Apache Jena Fuseki and TDB

Unless otherwise noted, "TDB" means "[Apache Jena TDB2](https://jena.apache.org/documentation/tdb2/)", 
and not TDB1, although several items of TDB1 documentation apply to TDB2.

For use in Smart Cache Graph, there is an additional label store for each database with RDF
ABAC and also a GraphQL service.

## Hardware

TDB uses memory mapped files for database indexes. These are cached by the
operating system in the filing system cache, not in the JVM heap.  Memory mapped
files form part of the Java process's virtual memory space. The Java process can
therefore appear to be very large but that does not mean the actual physical
process size is large.

The database design benefits from local SSD.

Databases that greatly exceed the size of the machine RAM wil be slow if the
disk is a HDD and/or remote storage.

## Configuration

There are no configuration parameters except some [fine-tuning
parameters](https://jena.apache.org/documentation/tdb/store-parameters.html)
useful only for small scale deployments,. Otherwise TDB

## Heap

A Fuseki server supports multiple databases.  Each database operates
independently; they do not share resources.

Each database uses heap space for query execution and also for caching the RDF
terms in the database. Each database needs about 1.5Gbytes (the "about" is
because the size of caches entries - especially literals can vary between
usage).

The cache is an LRU cache. It fills up over time so it does not hit its maximum
size until after the server has been operating for a while.  A setting for
maximum heap size `-Xmx` of 2 gigabytes per database is a good starting point.

## Backup

On the CORE platform, a database can be rebuilt by starting a new instance and
having it load all events from the knowledge topic.

It is possible to separately have a data backup taken remotely by simply making
an HTTP request:

`GET https://server:3030/database`

assuming that there is an endpoint:

```
:someService rdf:type fuseki:Service ;
    fuseki:name "/database" ;
    fuseki:endpoint [ fuseki:operation fuseki:gsp-r ; ] ;
    ...
```
Note - this must not be accessible outside the CORE platform because it does not implement RDF-ABAC filtering.


The request will stream the data in the database.  The request can specific the content-type.

## Compaction

A Fuseki-TDB database does not reclaim unused automatically which would need
tracking data in-use while transactions were in progress.  A similar situation
happens in PostgreSQL, which provides the "vacuum" operation.

The database can grow quite large. It should be compacted from time-to-time.

Due to this problem, we have an additional module, `Initial Compaction`, has been added
which will call the compact operation at application start-up prior to the server becoming
available. This is automatically turned on but can be disabled by setting the 
environment variable `DISABLE_INITIAL_COMPACTION`.

If the fuseki server is run with `--compact`, then the endpoint `/$/compact`
will perform a database compaction. This can execute on a live server allowing
read-operation to continue to be processed in parallel.  *Note:* write
operations are held up during this action.

## Backup / Restore

In order to improve the resiliency of the Smart Cache Graph the facility to back up the data is currently being worked upon. 
At present only the underlying Fuseki-TDB will be saved. This will be extended to include backing up the security label, and Kafka offsets too.
An obvious follow-up will be the ability to restore from backup.

This facility can be turned on by setting the environment variable `ENABLE_BACKUPS` to true.

### Encryption
It is also possible to encrypt backup files using [PGP](https://en.wikipedia.org/wiki/Pretty_Good_Privacy) for more secure storage or transportation. In order to do this you need to provide a PGP key pair and passphrase using the following enviroment variables:
- `PRIVATE_KEY_URL` - the URL of the private key file, for example `file:///tmp/private.asc`
- `PUBLIC_KEY_URL` - the URL of the public key file, for example `file:///tmp/public.asc`
- `PASSKEY` - the passphrase used when creating the key

If these environment variables are not set, or one of them is missing, encryption will not be applied. A warning message is shown in the application log files if encryption is not enabled.

If you need to create a PGP key pair this can be done as follows:
```shell
gpg --full-generate-key
``` 
Select the default or other appropriate options and choose a passphrase - this will need to be set as the value of the `PASSKEY` environment variable. Then list the public keys:
```shell
gpg --list-keys --keyid-format=long
```
Copy the key ID for the public key you just created, e.g. `F2C0B93297F09448`, then export the key to a file:
```shell
gpg --armor --export F2C0B93297F09448 > public.asc
```
Do the same for the private key, first obtaining the private key ID: 
```shell
gpg --list-secret-keys --keyid-format=long
```
Then write this to a file as well:
```shell
gpg --armor --export-secret-keys F2C0B93297F09448 > private.asc
```
## Exclusions

There is an additional Fuseki module `JWT Servlet Auth` that applies authentication to all the endpoints offered by the server.

We have disabled authentication for a number of the utility endpoints listed below:
- `/$/compact` 
- `/$/ping`
- `/$/metrics` 
- `/$/stats`

At present, they are hard-coded.  As mentioned above, though, the compact operation can disable write operations during the compaction process and be accessible by non-authorised users. 
Users need to be aware of this when deploying with the `--compact` command line setting. We recommend disabling remote access to this via other means.