{{/*
Copyright (C) 2026 Telicent Limited
*/}}

{{/*
This file contains the names of other host/service name(s) and service account name(s) on which this
application relies on. For a full explanation please view '_hosts.tlp' file in the 'auth' sub-chart.
*/}}


{{/* auth - returns host ('service:port') and serviceAccount */}}
{{- define "graph.hostAuth" -}}
{{- printf "%s" (include "common.discoverHost" (list . .Values.hosts.auth )) -}}
{{- end -}}
{{- define "graph.serviceAccountAuth" -}}
{{- printf "%s" (include "common.discoverServiceAccount" (list . .Values.hosts.auth )) -}}
{{- end -}}

{{/* traefik-proxy - returns host ('service:port') and serviceAccount */}}
{{- define "graph.hostTraefikProxy" -}}
{{- printf "%s" (include "common.discoverHost" (list . .Values.hosts.traefikProxy )) -}}
{{- end -}}
{{- define "graph.serviceAccountTraefikProxy" -}}
{{- printf "%s" (include "common.discoverServiceAccount" (list . .Values.hosts.traefikProxy )) -}}
{{- end -}}

{{/* search - returns host ('service:port') and serviceAccount */}}
{{- define "graph.hostSearch" -}}
{{- printf "%s" (include "common.discoverHost" (list . .Values.hosts.search )) -}}
{{- end -}}
{{- define "graph.serviceAccountSearch" -}}
{{- printf "%s" (include "common.discoverServiceAccount" (list . .Values.hosts.search )) -}}
{{- end -}}

{{/* paperback-writer | preview - returns host ('service:port') and serviceAccount */}}
{{- define "graph.hostPaperbackWriter" -}}
{{- printf "%s" (include "common.discoverHostPreview" (list . .Values.hostsPreview.paperbackWriter )) -}}
{{- end -}}
{{- define "graph.serviceAccountPaperbackWriter" -}}
{{- printf "%s" (include "common.discoverServiceAccountPreview" (list . .Values.hostsPreview.paperbackWriter )) -}}
{{- end -}}

{{/* ai-sparql-builder | preview - returns host ('service:port') and serviceAccount */}}
{{- define "graph.hostAISparqlBuilder" -}}
{{- printf "%s" (include "common.discoverHostPreview" (list . .Values.hostsPreview.aiSparqlBuilder )) -}}
{{- end -}}
{{- define "graph.serviceAccountAISparqlBuilder" -}}
{{- printf "%s" (include "common.discoverServiceAccountPreview" (list . .Values.hostsPreview.aiSparqlBuilder )) -}}
{{- end -}}

{{/* catalogue-api | preview - returns host ('service:port') and serviceAccount */}}
{{- define "graph.hostCatalogue" -}}
{{- printf "%s" (include "common.discoverHostPreview" (list . .Values.hostsPreview.catalogue )) -}}
{{- end -}}
{{- define "graph.serviceAccountCatalogue" -}}
{{- printf "%s" (include "common.discoverServiceAccountPreview" (list . .Values.hostsPreview.catalogue )) -}}
{{- end -}}

{{/* api-builder | preview - returns host ('service:port') and serviceAccount */}}
{{- define "graph.hostAPIBuilder" -}}
{{- printf "%s" (include "common.discoverHostPreview" (list . .Values.hostsPreview.apiBuilder )) -}}
{{- end -}}
{{- define "graph.serviceAccountAPIBuilder" -}}
{{- printf "%s" (include "common.discoverServiceAccountPreview" (list . .Values.hostsPreview.apiBuilder )) -}}
{{- end -}}

{{/* the-management | preview - returns host ('service:port') and serviceAccount */}}
{{- define "graph.hostTheManagement" -}}
{{- printf "%s" (include "common.discoverHostPreview" (list . .Values.hostsPreview.theManagement )) -}}
{{- end -}}
{{- define "graph.serviceAccountTheManagement" -}}
{{- printf "%s" (include "common.discoverServiceAccountPreview" (list . .Values.hostsPreview.theManagement )) -}}
{{- end -}}