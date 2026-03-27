output "cluster_name" {
  description = "EKS cluster name"
  value       = module.eks.cluster_name
}

output "cluster_endpoint" {
  description = "EKS cluster API endpoint"
  value       = module.eks.cluster_endpoint
}

output "controller_url" {
  description = "ALB URL for the jMeter Next controller UI"
  value       = module.helm.controller_url
}

output "ecr_controller_url" {
  description = "ECR repository URL for the controller image"
  value       = module.ecr.controller_repository_url
}

output "ecr_worker_url" {
  description = "ECR repository URL for the worker image"
  value       = module.ecr.worker_repository_url
}

output "kubeconfig_command" {
  description = "Command to configure kubectl for this cluster"
  value       = "aws eks update-kubeconfig --name ${local.cluster_name} --region ${var.region}"
}

output "vpc_id" {
  description = "VPC ID"
  value       = module.vpc.vpc_id
}
