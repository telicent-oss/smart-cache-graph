{{/*
Copyright (C) 2026 Telicent Limited
*/}}

{{/*
Expand the name of the chart.
*/}}
{{- define "deletion-worker.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Allow the release namespace to be overridden.
*/}}
{{- define "deletion-worker.namespace" -}}
{{- if .Values.namespaceOverride -}}
{{- .Values.namespaceOverride -}}
{{- else -}}
{{- .Release.Namespace -}}
{{- end -}}
{{- end -}}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "deletion-worker.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "deletion-worker.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "deletion-worker.selectorLabels" -}}
app.kubernetes.io/name: {{ include "deletion-worker.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "deletion-worker.labels" -}}
helm.sh/chart: {{ include "deletion-worker.chart" . }}
{{ include "deletion-worker.selectorLabels" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/version: {{ include "deletion-worker.version" . | quote }}
app: {{ include "deletion-worker.name" . }}
telicent.io/resource: "true"
{{- range $key, $value := .Values.commonLabels }}
{{ $key }}: {{ $value | quote }}
{{- end }}
{{- end }}

{{/*
Create the name of the service account to use (based on the fullname).
*/}}
{{- define "deletion-worker.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "deletion-worker.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- .Values.serviceAccount.name | default "default" }}
{{- end }}
{{- end }}

{{/*
Create the name of the service to use (based on the fullname).
*/}}
{{- define "deletion-worker.serviceName" -}}
{{- if .Values.service.name }}
{{- .Values.service.name -}}
{{- else }}
{{- include "deletion-worker.fullname" . }}
{{- end }}
{{- end }}

{{/*
Create the name of the env configmap.
*/}}
{{- define "deletion-worker.envConfigMapName" -}}
{{- printf "%s-env" (include "deletion-worker.fullname" .) }}
{{- end }}

{{/*
Return the image to use.
*/}}
{{- define "deletion-worker.version" -}}
{{- .Values.image.tag | default .Chart.AppVersion }}
{{- end -}}

{{- define "deletion-worker.imageRegistry" -}}
{{- .Values.global.imageRegistry | default .Values.image.registry }}
{{- end -}}

{{- define "deletion-worker.image" -}}
{{- printf "%s/%s:%s" (include "deletion-worker.imageRegistry" .) .Values.image.repository (include "deletion-worker.version" .) }}
{{- end -}}