{{/*
Copyright (C) 2026 Telicent Limited
*/}}

{{/*
Returns the version
*/}}
{{- define "graph.version" -}}
{{- .Values.image.tag | default .Chart.AppVersion }}
{{- end -}}

{{/*
Returns the image registry
*/}}
{{- define "graph.imageRegistry" -}}
{{- .Values.global.imageRegistry | default .Values.image.registry }}
{{- end -}}

{{/*
Returns the image
*/}}
{{- define "graph.image" -}}
{{- printf "%s/%s:%s" (include "graph.imageRegistry" .) .Values.image.repository  (include "graph.version" .) }}
{{- end -}}

{{/*
Returns the registration sidecar image
*/}}
{{- define "graph.registrationSidecarImage" -}}
{{- $registry := .Values.global.imageRegistry | default .Values.registrationSidecar.image.registry -}}
{{- printf "%s/%s:%s" $registry .Values.registrationSidecar.image.repository .Values.registrationSidecar.image.tag }}
{{- end -}}
