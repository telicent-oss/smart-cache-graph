{{/*
Copyright (C) 2026 Telicent Limited
*/}}

{{/* traefik-proxy - returns host ('service:port') and serviceAccount */}}
{{- define "deletion-worker.hostTraefikProxy" -}}
{{- printf "%s" (include "common.discoverHost" (list . .Values.hosts.traefikProxy )) -}}
{{- end -}}
{{- define "deletion-worker.serviceAccountTraefikProxy" -}}
{{- printf "%s" (include "common.discoverServiceAccount" (list . .Values.hosts.traefikProxy )) -}}
{{- end -}}