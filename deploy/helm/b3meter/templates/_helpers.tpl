{{/*
Expand the name of the chart.
*/}}
{{- define "jmeter-next.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "jmeter-next.fullname" -}}
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
{{- define "jmeter-next.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "jmeter-next.labels" -}}
helm.sh/chart: {{ include "jmeter-next.chart" . }}
{{ include "jmeter-next.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "jmeter-next.selectorLabels" -}}
app.kubernetes.io/name: {{ include "jmeter-next.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Controller labels
*/}}
{{- define "jmeter-next.controller.labels" -}}
{{ include "jmeter-next.labels" . }}
app.kubernetes.io/component: controller
{{- end }}

{{/*
Controller selector labels
*/}}
{{- define "jmeter-next.controller.selectorLabels" -}}
{{ include "jmeter-next.selectorLabels" . }}
app.kubernetes.io/component: controller
{{- end }}

{{/*
Worker labels
*/}}
{{- define "jmeter-next.worker.labels" -}}
{{ include "jmeter-next.labels" . }}
app.kubernetes.io/component: worker
{{- end }}

{{/*
Worker selector labels
*/}}
{{- define "jmeter-next.worker.selectorLabels" -}}
{{ include "jmeter-next.selectorLabels" . }}
app.kubernetes.io/component: worker
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "jmeter-next.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "jmeter-next.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Controller fullname
*/}}
{{- define "jmeter-next.controller.fullname" -}}
{{- printf "%s-controller" (include "jmeter-next.fullname" .) }}
{{- end }}

{{/*
Worker fullname
*/}}
{{- define "jmeter-next.worker.fullname" -}}
{{- printf "%s-worker" (include "jmeter-next.fullname" .) }}
{{- end }}

{{/*
Worker headless service name
*/}}
{{- define "jmeter-next.worker.headlessServiceName" -}}
{{- printf "%s-worker-headless" (include "jmeter-next.fullname" .) }}
{{- end }}
