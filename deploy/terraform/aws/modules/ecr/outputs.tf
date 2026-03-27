output "controller_repository_url" {
  description = "ECR repository URL for the controller image"
  value       = aws_ecr_repository.controller.repository_url
}

output "worker_repository_url" {
  description = "ECR repository URL for the worker image"
  value       = aws_ecr_repository.worker.repository_url
}

output "controller_repository_arn" {
  description = "ARN of the controller ECR repository"
  value       = aws_ecr_repository.controller.arn
}

output "worker_repository_arn" {
  description = "ARN of the worker ECR repository"
  value       = aws_ecr_repository.worker.arn
}
