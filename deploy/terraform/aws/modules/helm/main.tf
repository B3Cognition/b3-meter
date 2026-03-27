provider "kubernetes" {
  host                   = var.cluster_endpoint
  cluster_ca_certificate = base64decode(var.cluster_ca_certificate)

  exec {
    api_version = "client.authentication.k8s.io/v1beta1"
    command     = "aws"
    args        = ["eks", "get-token", "--cluster-name", var.cluster_name]
  }
}

provider "helm" {
  kubernetes {
    host                   = var.cluster_endpoint
    cluster_ca_certificate = base64decode(var.cluster_ca_certificate)

    exec {
      api_version = "client.authentication.k8s.io/v1beta1"
      command     = "aws"
      args        = ["eks", "get-token", "--cluster-name", var.cluster_name]
    }
  }
}

# --- jMeter Next Namespace ---
resource "kubernetes_namespace" "jmeter" {
  metadata {
    name = "jmeter"

    labels = {
      app         = "jmeter-next"
      environment = var.environment
    }
  }
}

# --- Helm Release ---
resource "helm_release" "jmeter_next" {
  name             = "jmeter-next"
  chart            = "${path.module}/../../../helm/jmeter-next"
  namespace        = kubernetes_namespace.jmeter.metadata[0].name
  create_namespace = false
  wait             = true
  timeout          = 600

  set {
    name  = "controller.image.repository"
    value = var.ecr_controller_url
  }

  set {
    name  = "worker.image.repository"
    value = var.ecr_worker_url
  }

  set {
    name  = "worker.replicaCount"
    value = var.worker_replicas
  }

  set {
    name  = "worker.autoscaling.enabled"
    value = "true"
  }

  set {
    name  = "worker.nodeSelector.role"
    value = "jmeter-worker"
  }

  set {
    name  = "worker.tolerations[0].key"
    value = "workload"
  }

  set {
    name  = "worker.tolerations[0].value"
    value = "loadtest"
  }

  set {
    name  = "worker.tolerations[0].effect"
    value = "NoSchedule"
  }

  set {
    name  = "controller.nodeSelector.role"
    value = "jmeter-controller"
  }
}

# --- Controller Service (LoadBalancer for UI access) ---
data "kubernetes_service" "controller" {
  metadata {
    name      = "jmeter-next-controller"
    namespace = kubernetes_namespace.jmeter.metadata[0].name
  }

  depends_on = [helm_release.jmeter_next]
}
