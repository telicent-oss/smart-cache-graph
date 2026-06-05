{{/*
Copyright (C) 2026 Telicent Limited
*/}}

{{/* ----------------------------------------------------------------- */}}
{{/* Returns the service/serviceAccount with or without a release name */}}
{{- define "common.discoverService" -}}
{{- $envVal := index . 0 -}}
{{- $serviceVal := index . 1 -}}
{{- $name := default $envVal.Chart.Name $envVal.Values.nameOverride -}}
{{- if or (contains $name $envVal.Release.Name) (eq ($envVal.Values.hosts.enableAutoConfigure) false) -}}
{{- printf "%s" $serviceVal -}}
{{- else -}}
{{- printf "%s-%s" $envVal.Release.Name $serviceVal -}}
{{- end -}}
{{- end -}}
{{/* Returns the *host* ('service:port') and serviceAccount */}}
{{- define "common.discoverHost" -}}
{{- $envVal := index . 0 -}}
{{- $hostVal := index . 1 -}}
{{- if $envVal.Values.hosts.enableAutoCorrect -}}
{{- $name := (index (splitList ":" $hostVal) 0 ) -}}
{{- $port := (index (splitList ":" $hostVal) 1 ) -}}
{{- printf "%s.%s:%s" (include "common.discoverService" (list $envVal $name)) $envVal.Release.Namespace $port -}}
{{- else -}}
{{- printf "%s" $hostVal -}}
{{- end -}}
{{- end -}}
{{- define "common.discoverServiceAccount" -}}
{{- $envVal := index . 0 -}}
{{- $hostVal := index . 1 -}}
{{- $name := (index (splitList ":" $hostVal) 0 ) -}}
{{- if $envVal.Values.hosts.enableAutoCorrect -}}
{{- printf "%s" (include "common.discoverService" (list $envVal $name)) -}}
{{- else -}}
{{- printf "%s" $name -}}
{{- end -}}
{{- end -}}

{{/* -------------------------------------------------------------- */}}
{{/* Returns the *preview host* ('service:port') and serviceAccount */}}
{{- define "common.discoverHostPreview" -}}
{{- $envVal := index . 0 -}}
{{- $hostVal := index . 1 -}}
{{- $name := (index (splitList ":" $hostVal) 0 ) -}}
{{- $port := (index (splitList ":" $hostVal) 1 ) -}}
{{- if and $envVal.Values.hostsPreview.enableAutoCorrect $envVal.Values.global.releaseNameTelicentPreview -}}
{{- printf "%s-%s.%s:%s" $envVal.Values.global.releaseNameTelicentPreview $name $envVal.Release.Namespace $port -}}
{{- else -}}
{{- printf "%s.%s:%s" $name $envVal.Release.Namespace $port -}}
{{- end -}}
{{- end -}}
{{- define "common.discoverServiceAccountPreview" -}}
{{- $envVal := index . 0 -}}
{{- $hostVal := index . 1 -}}
{{- $name := (index (splitList ":" $hostVal) 0 ) -}}
{{- if and $envVal.Values.hostsPreview.enableAutoCorrect $envVal.Values.global.releaseNameTelicentPreview -}}
{{- printf "%s-%s" $envVal.Values.global.releaseNameTelicentPreview $name -}}
{{- else -}}
{{- printf "%s" $name -}}
{{- end -}}
{{- end -}}
