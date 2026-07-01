{{/*
Copyright (C) 2026 Telicent Limited
*/}}

{{/*
Create the name of the config map
*/}}
{{- define "deletion-worker.envConfigMapName" -}}
{{- if .Values.configMap.existingEnvConfigMap }}
{{- .Values.configMap.existingEnvConfigMap }}
{{- else }}
{{- printf "tc-%s-%s" .Chart.Name "env" }}
{{- end }}
{{- end }}


{{/*
Create Kafka Auth Config name to use
*/}}
{{- define "deletion-worker.kafkaAuthConfig" -}}
{{- printf "tc-%s-%s" .Chart.Name "kafka-config" }}
{{- end }}
