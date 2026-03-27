variable "project_name" {
  description = "Project name for resource naming"
  type        = string
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "cluster_name" {
  description = "EKS cluster name"
  type        = string
}

variable "cluster_version" {
  description = "Kubernetes version"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID"
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnet IDs for EKS nodes"
  type        = list(string)
}

variable "controller_instance_type" {
  description = "Instance type for controller node group"
  type        = string
  default     = "t3.large"
}

variable "worker_instance_type" {
  description = "Instance type for worker node group"
  type        = string
  default     = "t3.xlarge"
}

variable "worker_min_size" {
  description = "Minimum number of worker nodes"
  type        = number
  default     = 2
}

variable "worker_max_size" {
  description = "Maximum number of worker nodes"
  type        = number
  default     = 10
}

variable "worker_desired_size" {
  description = "Desired number of worker nodes"
  type        = number
  default     = 3
}

variable "enable_spot_instances" {
  description = "Use spot instances for workers"
  type        = bool
  default     = true
}

variable "enable_public_endpoint" {
  description = "Enable public access to the EKS API"
  type        = bool
  default     = true
}

variable "enable_cluster_logging" {
  description = "Enable EKS control plane logging"
  type        = bool
  default     = false
}
