# deletion-worker

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

Contains Java configuration parameters to be used by the *Deletion Worker* application

| Name              | Description                     | Value                       |
| ----------------- | ------------------------------- | --------------------------- |
| `java.jvmOptions` | JVM options for the application | `-XX:MaxRAMPercentage=80.0` |

### Application Parameters - Deletion Worker

Contains configuration parameters that configure aspects of the *Deletion Worker* application behaviour.

| Name                   | Description                     | Value       |
| ---------------------- | ------------------------------- | ----------- |
| `deletionWorker.topic` | Kafka topic for deletion events | `knowledge` |

### ConfigMap Parameters

| Name                                | Description                                                                       | Value |
| ----------------------------------- | --------------------------------------------------------------------------------- | ----- |
| `configMap.existingEnvConfigMap`    | Name of existing configmap containing *Deletion Worker* Environment Configuration | `""`  |
| `configMap.existingFusekiConfigMap` | Name of existing configmap containing Fuseki Configuration                        | `""`  |

### Common Parameters

| Name                | Description                                                            | Value |
| ------------------- | ---------------------------------------------------------------------- | ----- |
| `nameOverride`      | String to partially override fullname (will maintain the release name) | `""`  |
| `fullnameOverride`  | String to fully override the generated release name                    | `""`  |
| `namespaceOverride` | String to fully override all deployed resources namespace              | `""`  |
| `commonLabels`      | Add labels to all the deployed resources                               | `{}`  |

### Statefulset Parameters

| Name                   | Description                                                            | Value |
| ---------------------- | ---------------------------------------------------------------------- | ----- |
| `replicas`             | Number of *Deletion Worker* replicas to deploy                         | `1`   |
| `revisionHistoryLimit` | Number of controller revisions to keep                                 | `5`   |
| `annotations`          | Add extra annotations to the Statefulset object                        | `{}`  |
| `podLabels`            | Add extra labels to the *Deletion Worker* pod                          | `{}`  |
| `podAnnotations`       | Add extra annotations to the *Deletion Worker* pod                     | `{}`  |
| `extraEnvVars`         | Array with extra environment variables to add to *Deletion Worker* pod | `[]`  |
| `extraVolumes`         | Optionally specify extra list of additional volumes                    | `[]`  |
| `extraVolumeMounts`    | Optionally specify extra list of additional volumeMounts               | `[]`  |
| `initContainers`       | Add init containers to the pod                                         | `[]`  |
| `sidecars`             | Add sidecars to the pod                                                | `[]`  |

### Statefulset Image Parameters

| Name                | Description                                                                      | Value                          |
| ------------------- | -------------------------------------------------------------------------------- | ------------------------------ |
| `image.registry`    | *Deletion Worker* image registry                                                 | `quay.io`                      |
| `image.repository`  | *Deletion Worker* image name                                                     | `telicent/scg-deletion-worker` |
| `image.tag`         | *Deletion Worker* image tag. If not set, a tag is generated using the appVersion | `""`                           |
| `image.pullPolicy`  | *Deletion Worker* image pull policy                                              | `IfNotPresent`                 |
| `image.pullSecrets` | Specify registry secret names as an array                                        | `[]`                           |

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

| Name        | Description                                | Value |
| ----------- | ------------------------------------------ | ----- |
| `resources` | Resources for *Deletion Worker* containers | `{}`  |

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


### Service Account Parameters

| Name                         | Description                                                                           | Value  |
| ---------------------------- | ------------------------------------------------------------------------------------- | ------ |
| `serviceAccount.create`      | Specifies whether a service account should be created                                 | `true` |
| `serviceAccount.name`        | Name of the ServiceAccount to use. If not set, a name is generated using the fullname | `""`   |
| `serviceAccount.annotations` | Additional custom annotations for the ServiceAccount                                  | `{}`   |
| `serviceAccount.automount`   | Automatically mount a ServiceAccount's API credentials                                | `true` |

### Traffic Exposure Parameters

| Name           | Description                                                                        | Value       |
| -------------- | ---------------------------------------------------------------------------------- | ----------- |
| `service.name` | *Deletion Worker* service name. If not set, a name is generated using the fullname | `""`        |
| `service.port` | *Deletion Worker* service port                                                     | `8080`      |
| `service.type` | *Deletion Worker* service type                                                     | `ClusterIP` |

### Metrics (Prometheus) Exposure Parameters


### Host(s) Parameters - Contains host information for applications deployed via *telicent-core* chart.

*Deletion Worker* interacts directly with other Telicent Applications using their default service/serviceAccount and port.
If either of those details changes, you can use this section to correctly refer to those applications.

| Name                      | Description                                                                               | Value                |
| ------------------------- | ----------------------------------------------------------------------------------------- | -------------------- |
| `hosts.enableAutoCorrect` | Allow for the release name to be automatically pre-fixed to each host value               | `true`               |
| `hosts.traefikProxy`      | Traefik Proxy application default host value, as defined by 'service/serviceAccount:port' | `traefik-proxy:8080` |
