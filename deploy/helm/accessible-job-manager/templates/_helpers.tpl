{{- define "ajm.name" -}}{{ .Chart.Name }}{{- end -}}

{{- define "ajm.fullname" -}}
{{- printf "%s-%s" .Release.Name .Chart.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "ajm.labels" -}}
app.kubernetes.io/name: {{ include "ajm.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end -}}

{{- define "ajm.selectorLabels" -}}
app.kubernetes.io/name: {{ include "ajm.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{/* JDBC-URL: CNPG-Service oder externe DB */}}
{{- define "ajm.dbUrl" -}}
{{- if .Values.postgres.deployCnpg -}}
jdbc:postgresql://{{ include "ajm.fullname" . }}-pg-rw:5432/manager
{{- else -}}
{{ required "postgres.external.url ist erforderlich" .Values.postgres.external.url }}
{{- end -}}
{{- end -}}

{{/* Name des Secrets mit DB-Credentials */}}
{{- define "ajm.dbSecret" -}}
{{- if .Values.postgres.deployCnpg -}}
{{ include "ajm.fullname" . }}-pg-app
{{- else -}}
{{ required "postgres.external.existingSecret ist erforderlich" .Values.postgres.external.existingSecret }}
{{- end -}}
{{- end -}}
