# jMeter Next - AWS Terraform Deployment

Infrastructure-as-code for deploying jMeter Next on AWS EKS.

## Architecture

- **VPC** with public/private subnets across 3 AZs, NAT gateway(s), optional flow logs
- **EKS** cluster with two node groups:
  - `controller` - single on-demand t3.large for the jMeter controller pod
  - `workers` - auto-scaled spot (or on-demand) t3.xlarge nodes for jMeter workers, with taints to isolate load-test workloads
- **ECR** repositories for controller and worker container images
- **Helm** release deploying the jMeter Next chart with node affinity and tolerations

## Prerequisites

- Terraform >= 1.5
- AWS CLI configured with appropriate credentials
- kubectl
- Helm 3

## Quick Start

```bash
# 1. Copy and edit variables
cp terraform.tfvars.example terraform.tfvars
# edit terraform.tfvars as needed

# 2. Initialize and apply
terraform init
terraform plan
terraform apply

# 3. Configure kubectl
$(terraform output -raw kubeconfig_command)

# 4. Verify
kubectl get nodes -l role=jmeter-controller
kubectl get nodes -l role=jmeter-worker
kubectl get pods -n jmeter
```

## Pushing Images to ECR

```bash
# Authenticate Docker to ECR
aws ecr get-login-password --region $(terraform output -raw region 2>/dev/null || echo eu-west-1) \
  | docker login --username AWS --password-stdin $(terraform output -raw ecr_controller_url | cut -d/ -f1)

# Build and push
docker build -t $(terraform output -raw ecr_controller_url):latest -f ../../Dockerfile.controller ../..
docker push $(terraform output -raw ecr_controller_url):latest

docker build -t $(terraform output -raw ecr_worker_url):latest -f ../../Dockerfile.worker ../..
docker push $(terraform output -raw ecr_worker_url):latest
```

## Module Structure

```
aws/
  main.tf                  # Root module: wires VPC, ECR, EKS, Helm together
  variables.tf             # All input variables with defaults
  outputs.tf               # Cluster info, ECR URLs, kubeconfig command
  versions.tf              # Provider version constraints
  terraform.tfvars.example # Example variable values
  modules/
    vpc/                   # VPC, subnets, NAT, route tables, optional flow logs
    ecr/                   # ECR repositories with lifecycle policies
    eks/                   # EKS cluster, node groups, IAM, security groups, addons
    helm/                  # Kubernetes/Helm providers, namespace, Helm release
```

## Production Considerations

- Set `single_nat_gateway = false` for HA NAT across all AZs
- Set `enable_spot_instances = false` if workers must not be interrupted
- Enable `enable_vpc_flow_logs` and `enable_cluster_logging` for observability
- Restrict `enable_public_endpoint = false` and use a bastion or VPN

## Teardown

```bash
terraform destroy
```
