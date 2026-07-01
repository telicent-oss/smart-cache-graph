{{/*
Copyright (C) 2026 Telicent Limited
*/}}

{{/*
Returns the principal used for Ingress traffic by the Istio AuthorizationPolicy
*/}}
{{- define "graph.ingressPrincipal" -}}
{{- printf "- cluster.local/ns/%s/sa/%s" .Release.Namespace ( include "graph.serviceAccountTraefikProxy" .) -}}
{{- end -}}

{{/*
Returns the principal used for Paperback Writer traffic by the Istio AuthorizationPolicy
*/}}
{{- define "graph.paperbackWriterPrincipal" -}}
{{- if .Values.global.enterprise -}}
{{- printf "- cluster.local/ns/%s/sa/%s" .Release.Namespace ( include "graph.serviceAccountPaperbackWriter" .) -}}
{{- end -}}
{{- end -}}

{{/*
Returns the principal used for AI Sparql Builder traffic by the Istio AuthorizationPolicy
*/}}
{{- define "graph.aiSparqlBuilderPrincipal" -}}
{{- if .Values.global.enterprise -}}
{{- printf "- cluster.local/ns/%s/sa/%s" .Release.Namespace ( include "graph.serviceAccountAISparqlBuilder" .) -}}
{{- end -}}
{{- end -}}

{{/*
Returns the principal used for Catalogue API traffic by the Istio AuthorizationPolicy
*/}}
{{- define "graph.cataloguePrincipal" -}}
{{- if .Values.global.enterprise -}}
{{- printf "- cluster.local/ns/%s/sa/%s" .Release.Namespace ( include "graph.serviceAccountCatalogue" .) -}}
{{- end -}}
{{- end -}}

{{/*
Returns the principal used for API Builder API traffic by the Istio AuthorizationPolicy
*/}}
{{- define "graph.apiBuilderPrincipal" -}}
{{- if .Values.global.enterprise -}}
{{- printf "- cluster.local/ns/%s/sa/%s" .Release.Namespace ( include "graph.serviceAccountAPIBuilder" .) -}}
{{- end -}}
{{- end -}}

{{/*
Returns the principal used for The Management API traffic by the Istio AuthorizationPolicy
*/}}
{{- define "graph.theManagementPrincipal" -}}
{{- if .Values.global.enterprise -}}
{{- printf "- cluster.local/ns/%s/sa/%s" .Release.Namespace ( include "graph.serviceAccountTheManagement" .) -}}
{{- end -}}
{{- end -}}