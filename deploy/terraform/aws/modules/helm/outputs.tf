output "controller_url" {
  description = "URL for the jMeter Next controller UI"
  value       = try(data.kubernetes_service.controller.status[0].load_balancer[0].ingress[0].hostname, "pending")
}

output "namespace" {
  description = "Kubernetes namespace where jMeter Next is deployed"
  value       = kubernetes_namespace.jmeter.metadata[0].name
}

output "release_name" {
  description = "Helm release name"
  value       = helm_release.jmeter_next.name
}
