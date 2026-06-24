# Telicent Package for Graph

Telicent Graph enables efficient storage, retrieval, and querying of complex relationships, making it ideal for applications that require rapid access to interconnected datasets.

## Introduction

This chart bootstraps Telicent Graph deployment on a [Kubernetes](https://kubernetes.io) cluster using
the [Helm](https://helm.sh) package manager.

## Prerequisites

- Kubernetes 1.23+
- Helm 3.9+

## Installing the Chart

To install the chart with the release name `my-release`:

```console
helm install my-release ./charts/telicent-core/charts/graph
```

## Uninstalling the Chart

To uninstall/delete the `my-release` deployment:

```console
helm delete my-release
```
The command removes all the Kubernetes components associated with the chart and deletes the release.

## Automating README and schema generation

```bash
.dev/readme-generator-for-helm --config=charts/telicent-core/readme.config \
 --values=charts/telicent-core/charts/graph/values.yaml \
 --readme=charts/telicent-core/charts/graph/README.md \
 --schema=charts/telicent-core/charts/graph/values.schema.json
```

## Configuration and installation details

### Resource requests and limits

These are inside the `resources` value (check parameter table). Setting requests is essential for production workloads
and these should be adapted to your specific use case.

### Sidecars and Init Containers

If you have a need for additional containers to run within the same pod (e.g. an additional metrics or logging
exporter), you can do so via the `sidecars` config parameter.
Define your container according to the Kubernetes container spec.

```yaml
sidecars:
- name: your-image-name
  image: your-image
  imagePullPolicy: Always
  ports:
  - name: portname
    containerPort: 1234
```

Similarly, you can add extra init containers using the `initContainers` parameter.

```yaml
initContainers:
- name: your-image-name
  image: your-image
  imagePullPolicy: Always
  ports:
  - name: portname
    containerPort: 1234
```

### Setting Pod's affinity

This chart allows you to set your custom affinity using the `affinity` parameter.
Find more information about Pod's affinity in
the [kubernetes documentation](https://kubernetes.io/docs/concepts/configuration/assign-pod-node/#affinity-and-anti-affinity).

## Parameters

### Global Parameters

Contains global parameters; these parameters are mirrored within the Telicent core umbrella chart.
Note: Only global parameters used within this chart will be listed below.

| Name                                    | Description                                                                                                                                                                            | Value                                          |
| --------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------- |
| `global.imageRegistry`                  | Global image registry                                                                                                                                                                  | `""`                                           |
| `global.imagePullSecrets`               | Global registry secret names as an array                                                                                                                                               | `[]`                                           |
| `global.releaseNameTelicentPreview`     | Release name used during the Telicent Preview chart installation. Note: ensure the value is correct, otherwise there will be no access to data-catalog, user-portal & paperback-writer | `""`                                           |
| `global.enterprise`                     | Enable enterprise mode, adding additional features and configurations                                                                                                                  | `false`                                        |
| `global.appHostDomain`                  | Domain associated with Telicent application/ui services. This value cannot be changed after it is set                                                                                  | `""`                                           |
| `global.apiHostDomain`                  | Domain associated with Telicent Api services. This value cannot be changed after it is set                                                                                             | `""`                                           |
| `global.authHostDomain`                 | Domain associated with Telicent authentication services, including OIDC providers. This value cannot be changed after it is set                                                        | `""`                                           |
| `global.kafka.bootstrapServers`         | Comma separated list containing Kafka bootstrap servers                                                                                                                                | `kafka-bootstrap.kafka.svc.cluster.local:9092` |
| `global.kafka.existingConfigSecretName` | Name of an existing secret containing Kafka configuration (preferred over individual settings below for security)                                                                      | `""`                                           |
| `global.kafka.username`                 | Username for Kafka authentication                                                                                                                                                      | `your.kafka.username.here`                     |
| `global.kafka.password`                 | Password for Kafka authentication                                                                                                                                                      | `your.kafka.password.here`                     |
| `global.kafka.protocol`                 | Protocol used for Kafka communication                                                                                                                                                  | `SASL_SSL`                                     |
| `global.kafka.mechanism`                | SASL mechanism used for Kafka authentication                                                                                                                                           | `SCRAM-SHA-512`                                |
| `global.truststore.existingSecret`      | Name of an existing secret containing the truststore                                                                                                                                   | `""`                                           |
| `global.truststore.mountPath`           | The mount path for the truststore in the container                                                                                                                                     | `/app/config/truststore`                       |

### Application Parameters - Java

Contains Java configuration parameters to be used by the *Graph* application

| Name                  | Description                                                                | Value                                                                                                                                                                                                                                                               |
| --------------------- | -------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `java.jvmOptions`     | JVM options for the application                                            | `-XX:InitialRAMPercentage=20 -XX:MaxRAMPercentage=40 -XX:MinHeapFreeRatio=20 -XX:MaxHeapFreeRatio=40 -XX:-ShrinkHeapInSteps -XX:InitialCodeCacheSize=8m -XX:ReservedCodeCacheSize=64m -XX:MetaspaceSize=32m -XX:MaxMetaspaceSize=256m -XX:MaxDirectMemorySize=100m` |
| `java.mallocArenaMax` | glibc malloc arena cap used to limit native memory fragmentation/retention | `2`                                                                                                                                                                                                                                                                 |

### Application Parameters - Graph

Contains configuration parameters that configure aspects of the *Graph* application behaviour.

| Name                       | Description                                                                                                                                                                                                                                                                                      | Value   |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------- |
| `graph.routeToNamedGraphs` | Enable or disable routing to named graphs. When set to true, data will be routed into named graphs based on the Distribution-ID header set on the incoming Kafka events.                                                                                                                         | `false` |
| `graph.legacyLabels`       | Enable or disable legacy label store format.  When set to false then the new label store format will be used, which allows for more efficient storage and querying of labels.  If a pre-existing store exists in the legacy format it will be automatically migrated forwards to the new format. | `false` |

### ConfigMap Parameters

| Name                                | Description                                                             | Value |
| ----------------------------------- | ----------------------------------------------------------------------- | ----- |
| `configMap.existingEnvConfigMap`    | Name of existing configmap containing *Graph* Environment Configuration | `""`  |
| `configMap.existingFusekiConfigMap` | Name of existing configmap containing Fuseki Configuration              | `""`  |

### Common Parameters

| Name                | Description                                                            | Value |
| ------------------- | ---------------------------------------------------------------------- | ----- |
| `nameOverride`      | String to partially override fullname (will maintain the release name) | `""`  |
| `fullnameOverride`  | String to fully override the generated release name                    | `""`  |
| `namespaceOverride` | String to fully override all deployed resources namespace              | `""`  |
| `commonLabels`      | Add labels to all the deployed resources                               | `{}`  |

### Statefulset Parameters

| Name                   | Description                                                  | Value |
| ---------------------- | ------------------------------------------------------------ | ----- |
| `replicas`             | Number of *Graph* replicas to deploy                         | `1`   |
| `revisionHistoryLimit` | Number of controller revisions to keep                       | `5`   |
| `annotations`          | Add extra annotations to the Statefulset object              | `{}`  |
| `podLabels`            | Add extra labels to the *Graph* pod                          | `{}`  |
| `podAnnotations`       | Add extra annotations to the *Graph* pod                     | `{}`  |
| `extraEnvVars`         | Array with extra environment variables to add to *Graph* pod | `[]`  |
| `extraVolumes`         | Optionally specify extra list of additional volumes          | `[]`  |
| `extraVolumeMounts`    | Optionally specify extra list of additional volumeMounts     | `[]`  |
| `initContainers`       | Add init containers to the pod                               | `[]`  |
| `sidecars`             | Add sidecars to the pod                                      | `[]`  |

### Statefulset Image Parameters

| Name                | Description                                                            | Value                        |
| ------------------- | ---------------------------------------------------------------------- | ---------------------------- |
| `image.registry`    | *Graph* image registry                                                 | `quay.io`                    |
| `image.repository`  | *Graph* image name                                                     | `telicent/smart-cache-graph` |
| `image.tag`         | *Graph* image tag. If not set, a tag is generated using the appVersion | `""`                         |
| `image.pullPolicy`  | *Graph* image pull policy                                              | `IfNotPresent`               |
| `image.pullSecrets` | Specify registry secret names as an array                              | `[]`                         |

### Statefulset Probe Parameters

| Name                                 | Description                                                             | Value |
| ------------------------------------ | ----------------------------------------------------------------------- | ----- |
| `readinessProbe.initialDelaySeconds` | Number of seconds before readiness probe is initiated                   | `120` |
| `readinessProbe.periodSeconds`       | How often (in seconds) to perform the readiness probe                   | `60`  |
| `readinessProbe.timeoutSeconds`      | Number of seconds after which the readiness probe times out             | `10`  |
| `readinessProbe.failureThreshold`    | Number of failures before the pod is marked unready                     | `3`   |
| `readinessProbe.successThreshold`    | Minimum consecutive successes for the probe to be considered successful | `1`   |
| `livenessProbe.initialDelaySeconds`  | Number of seconds before liveness probe is initiated                    | `120` |
| `livenessProbe.periodSeconds`        | How often (in seconds) to perform the liveness probe                    | `60`  |
| `livenessProbe.timeoutSeconds`       | Number of seconds after which the liveness probe times out              | `10`  |
| `livenessProbe.failureThreshold`     | Number of failures before the pod is restarted                          | `3`   |

### Statefulset Resources Parameters - Requests and Limits

| Name        | Description                      | Value |
| ----------- | -------------------------------- | ----- |
| `resources` | Resources for *Graph* containers | `{}`  |

### Statefulset Security Context Parameters - Default Security Context

| Name                                                | Description                                                             | Value            |
| --------------------------------------------------- | ----------------------------------------------------------------------- | ---------------- |
| `podSecurityContext.runAsUser`                      | Set the provisioning pod's Security Context runAsUser User ID           | `185`            |
| `podSecurityContext.runAsGroup`                     | Set the provisioning pod's Security Context runAsGroup Group ID         | `185`            |
| `podSecurityContext.runAsNonRoot`                   | Set the provisioning pod's Security Context runAsNonRoot                | `true`           |
| `podSecurityContext.fsGroup`                        | Set the provisioning pod's Group ID for the mounted volumes' filesystem | `185`            |
| `podSecurityContext.seccompProfile.type`            | Set the provisioning pod's Security Context seccomp profile             | `RuntimeDefault` |
| `containerSecurityContext.runAsUser`                | Set containers' Security Context runAsUser User ID                      | `185`            |
| `containerSecurityContext.runAsGroup`               | Set containers' Security Context runAsGroup Group ID                    | `185`            |
| `containerSecurityContext.runAsNonRoot`             | Set container's Security Context runAsNonRoot                           | `true`           |
| `containerSecurityContext.allowPrivilegeEscalation` | Set container's Security Context allowPrivilegeEscalation               | `false`          |
| `containerSecurityContext.capabilities.drop`        | List of capabilities to be dropped                                      | `["ALL"]`        |
| `containerSecurityContext.seccompProfile.type`      | Set container's Security Context seccomp profile                        | `RuntimeDefault` |

### Statefulset Affinity Parameters

| Name           | Description                    | Value |
| -------------- | ------------------------------ | ----- |
| `affinity`     | Affinity for pod assignment    | `{}`  |
| `nodeSelector` | Node labels for pod assignment | `{}`  |
| `tolerations`  | Tolerations for pod assignment | `[]`  |

### Persistent Volume Claim Parameters

| Name                                                 | Description                                   | Value  |
| ---------------------------------------------------- | --------------------------------------------- | ------ |
| `persistentVolumeClaims.backupsVolume.size`          | PVC Storage Request for the Backup volume     | `25Gi` |
| `persistentVolumeClaims.backupsVolume.storageClass`  | PVC Storage Class for the Backup data volume  | `gp3`  |
| `persistentVolumeClaims.datasetsVolume.size`         | PVC Storage Request for the *Graph* volume    | `25Gi` |
| `persistentVolumeClaims.datasetsVolume.storageClass` | PVC Storage Class for the *Graph* data volume | `gp3`  |

### Service Account Parameters

| Name                         | Description                                                                           | Value  |
| ---------------------------- | ------------------------------------------------------------------------------------- | ------ |
| `serviceAccount.create`      | Specifies whether a service account should be created                                 | `true` |
| `serviceAccount.name`        | Name of the ServiceAccount to use. If not set, a name is generated using the fullname | `""`   |
| `serviceAccount.annotations` | Additional custom annotations for the ServiceAccount                                  | `{}`   |
| `serviceAccount.automount`   | Automatically mount a ServiceAccount's API credentials                                | `true` |

### Traffic Exposure Parameters

| Name           | Description                                                              | Value       |
| -------------- | ------------------------------------------------------------------------ | ----------- |
| `service.name` | *Graph* service name. If not set, a name is generated using the fullname | `""`        |
| `service.port` | *Graph* service port                                                     | `8080`      |
| `service.type` | *Graph* service type                                                     | `ClusterIP` |

### Metrics (Prometheus) Exposure Parameters

| Name                   | Description                     | Value     |
| ---------------------- | ------------------------------- | --------- |
| `metrics.enabled`      | Enable Prometheus metrics       | `true`    |
| `metrics.service.name` | Name for the Prometheus service | `metrics` |
| `metrics.service.port` | Port for the Prometheus service | `9464`    |

### Host(s) Parameters - Contains host information for applications deployed via *telicent-core* chart.

*Graph* interacts directly with other Telicent Applications using their default service/serviceAccount and port.
If either of those details changes, you can use this section to correctly refer to those applications.

| Name                      | Description                                                                                                                                                                                                                        | Value                |
| ------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------- |
| `hosts.enableAutoCorrect` | Allow for the release name to be automatically pre-fixed to each host value when required (default behavior when installing through the parent chart). Alternatively, the host value will be used as is, without any modification. | `true`               |
| `hosts.auth`              | Auth application default host value, as defined by 'service/serviceAccount:port'                                                                                                                                                   | `auth:8080`          |
| `hosts.traefikProxy`      | Traefik Proxy application default host value, as defined by 'service/serviceAccount:port'                                                                                                                                          | `traefik-proxy:8080` |
| `hosts.search`            | Search application default host value, as defined by 'service/serviceAccount:port'                                                                                                                                                 | `search:8080`        |

### Host(s) Preview Parameters - Contains host information for applications deployed via *telicent-preview* chart

*Graph* interacts with applications deployed via *telicent-preview* using their default service/serviceAccount and port.
If either of those details changes, you can use this section to correctly refer to those applications.

| Name                             | Description                                                                                                                                        | Value                    |
| -------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------ |
| `hostsPreview.enableAutoCorrect` | Prefix 'global.releaseNameTelicentPreview' value to each host value. Alternatively, the host value will be used as it is, without any modification | `true`                   |
| `hostsPreview.paperbackWriter`   | Paperback Writer application host value, as defined by 'service/serviceAccount:port'                                                               | `paperback-writer:8080`  |
| `hostsPreview.aiSparqlBuilder`   | AI SPARQL Builder application host value, as defined by 'service/serviceAccount:port'                                                              | `ai-sparql-builder:8080` |

## License

Copyright &copy; 2026 Telicent Limited
