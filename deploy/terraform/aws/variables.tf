variable "region" {
  description = "AWS region for all resources"
  type        = string
  default     = "eu-west-1"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "dev"

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be one of: dev, staging, prod."
  }
}

variable "project_name" {
  description = "Project name used for resource naming and tagging"
  type        = string
  default     = "jmeter-next"
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "eks_cluster_version" {
  description = "Kubernetes version for the EKS cluster"
  type        = string
  default     = "1.29"
}

variable "controller_instance_type" {
  description = "EC2 instance type for the controller node group"
  type        = string
  default     = "t3.large"
}

variable "eks_node_instance_type" {
  description = "EC2 instance type for worker node group"
  type        = string
  default     = "t3.xlarge"
}

variable "eks_node_min_size" {
  description = "Minimum number of worker nodes"
  type        = number
  default     = 2
}

variable "eks_node_max_size" {
  description = "Maximum number of worker nodes"
  type        = number
  default     = 10
}

variable "eks_node_desired_size" {
  description = "Desired number of worker nodes"
  type        = number
  default     = 3
}

variable "worker_replicas" {
  description = "Number of jMeter worker pod replicas"
  type        = number
  default     = 3
}

variable "enable_spot_instances" {
  description = "Use spot instances for worker nodes (cost savings for non-production)"
  type        = bool
  default     = true
}

variable "enable_vpc_flow_logs" {
  description = "Enable VPC flow logs for network monitoring"
  type        = bool
  default     = false
}

variable "enable_cluster_logging" {
  description = "Enable EKS control plane logging to CloudWatch"
  type        = bool
  default     = false
}

variable "enable_public_endpoint" {
  description = "Enable public access to the EKS API endpoint"
  type        = bool
  default     = true
}

variable "single_nat_gateway" {
  description = "Use a single NAT Gateway (cost-optimized for dev). Set false for HA in production."
  type        = bool
  default     = true
}

variable "tags" {
  description = "Common tags applied to all resources"
  type        = map(string)
  default     = {}
}
