variable "cluster_name" {
  description = "EKS cluster name for authentication"
  type        = string
}

variable "cluster_endpoint" {
  description = "EKS cluster API endpoint"
  type        = string
}

variable "cluster_ca_certificate" {
  description = "Base64-encoded CA certificate for the EKS cluster"
  type        = string
}

variable "ecr_controller_url" {
  description = "ECR repository URL for the controller image"
  type        = string
}

variable "ecr_worker_url" {
  description = "ECR repository URL for the worker image"
  type        = string
}

variable "worker_replicas" {
  description = "Number of jMeter worker pod replicas"
  type        = number
  default     = 3
}

variable "environment" {
  description = "Environment name"
  type        = string
}
