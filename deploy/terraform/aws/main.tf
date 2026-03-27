provider "aws" {
  region = var.region

  default_tags {
    tags = merge(
      {
        Project     = var.project_name
        Environment = var.environment
        ManagedBy   = "terraform"
      },
      var.tags
    )
  }
}

data "aws_caller_identity" "current" {}
data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  cluster_name = "${var.project_name}-${var.environment}"
  azs          = slice(data.aws_availability_zones.available.names, 0, 3)
}

# --- VPC ---
module "vpc" {
  source = "./modules/vpc"

  project_name       = var.project_name
  environment        = var.environment
  vpc_cidr           = var.vpc_cidr
  availability_zones = local.azs
  cluster_name       = local.cluster_name
  single_nat_gateway = var.single_nat_gateway
  enable_flow_logs   = var.enable_vpc_flow_logs
}

# --- ECR ---
module "ecr" {
  source = "./modules/ecr"

  project_name = var.project_name
  environment  = var.environment
}

# --- EKS ---
module "eks" {
  source = "./modules/eks"

  project_name            = var.project_name
  environment             = var.environment
  cluster_name            = local.cluster_name
  cluster_version         = var.eks_cluster_version
  vpc_id                  = module.vpc.vpc_id
  private_subnet_ids      = module.vpc.private_subnet_ids
  controller_instance_type = var.controller_instance_type
  worker_instance_type    = var.eks_node_instance_type
  worker_min_size         = var.eks_node_min_size
  worker_max_size         = var.eks_node_max_size
  worker_desired_size     = var.eks_node_desired_size
  enable_spot_instances   = var.enable_spot_instances
  enable_public_endpoint  = var.enable_public_endpoint
  enable_cluster_logging  = var.enable_cluster_logging
}

# --- Helm ---
module "helm" {
  source = "./modules/helm"

  cluster_name             = local.cluster_name
  cluster_endpoint         = module.eks.cluster_endpoint
  cluster_ca_certificate   = module.eks.cluster_ca_certificate
  ecr_controller_url       = module.ecr.controller_repository_url
  ecr_worker_url           = module.ecr.worker_repository_url
  worker_replicas          = var.worker_replicas
  environment              = var.environment
}
