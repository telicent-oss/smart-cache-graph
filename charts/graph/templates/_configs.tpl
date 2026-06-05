{{/*
Copyright (C) 2026 Telicent Limited
*/}}

{{/*
Create the name of the config map
*/}}
{{- define "graph.envConfigMapName" -}}
{{- if .Values.configMap.existingEnvConfigMap }}
{{- .Values.configMap.existingEnvConfigMap }}
{{- else }}
{{- printf "tc-%s-%s" .Chart.Name "env" }}
{{- end }}
{{- end }}


{{/*
Create a fuseki config name to use
*/}}
{{- define "graph.fusekiConfigMapName" -}}
{{- if .Values.configMap.existingFusekiConfigMap }}
{{- .Values.configMap.existingFusekiConfigMap }}
{{- else }}
{{- printf "tc-%s-%s" .Chart.Name "fuseki" }}
{{- end }}
{{- end }}

{{/*
Create Kafka Auth Config name to use
*/}}
{{- define "graph.kafkaAuthConfig" -}}
{{- printf "tc-%s-%s" .Chart.Name "kafka-config" }}
{{- end }}
