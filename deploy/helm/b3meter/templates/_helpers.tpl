{{/*
Expand the name of the chart.
*/}}
{{- define "b3meter.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "b3meter.fullname" -}}
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
{{- define "b3meter.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "b3meter.labels" -}}
helm.sh/chart: {{ include "b3meter.chart" . }}
{{ include "b3meter.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "b3meter.selectorLabels" -}}
app.kubernetes.io/name: {{ include "b3meter.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Controller labels
*/}}
{{- define "b3meter.controller.labels" -}}
{{ include "b3meter.labels" . }}
app.kubernetes.io/component: controller
{{- end }}

{{/*
Controller selector labels
*/}}
{{- define "b3meter.controller.selectorLabels" -}}
{{ include "b3meter.selectorLabels" . }}
app.kubernetes.io/component: controller
{{- end }}

{{/*
Worker labels
*/}}
{{- define "b3meter.worker.labels" -}}
{{ include "b3meter.labels" . }}
app.kubernetes.io/component: worker
{{- end }}

{{/*
Worker selector labels
*/}}
{{- define "b3meter.worker.selectorLabels" -}}
{{ include "b3meter.selectorLabels" . }}
app.kubernetes.io/component: worker
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "b3meter.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "b3meter.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Controller fullname
*/}}
{{- define "b3meter.controller.fullname" -}}
{{- printf "%s-controller" (include "b3meter.fullname" .) }}
{{- end }}

{{/*
Worker fullname
*/}}
{{- define "b3meter.worker.fullname" -}}
{{- printf "%s-worker" (include "b3meter.fullname" .) }}
{{- end }}

{{/*
Worker headless service name
*/}}
{{- define "b3meter.worker.headlessServiceName" -}}
{{- printf "%s-worker-headless" (include "b3meter.fullname" .) }}
{{- end }}
