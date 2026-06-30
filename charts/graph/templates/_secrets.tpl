{{/*
Copyright (C) 2026 Telicent Limited
*/}}

{{/*
Create the name of the backup encryption secret
*/}}
{{- define "graph.backupEncryptionSecretName" -}}
{{- if .Values.graph.enableBackupEncryption -}}
{{- required "graph.backupEncryption.existingSecret is required when graph.enableBackupEncryption is true" .Values.graph.backupEncryption.existingSecret }}
{{- end -}}
{{- end -}}
