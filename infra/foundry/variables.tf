# ── Región principal (Hub, Storage, Key Vault, Resource Group) ─────────────────

variable "location" {
  description = "Región principal para el Resource Group, AI Hub y Storage"
  type        = string
  default     = "westeurope"
  # Alternativas con soporte AI Foundry Hub: swedencentral, eastus, westus2
}

variable "openai_location" {
  description = <<-EOT
    Región del servicio Azure OpenAI.
    Puede ser distinta a 'location' para aprovechar mayor disponibilidad de modelos.
    swedencentral = catálogo más amplio en la UE.
    eastus        = máxima disponibilidad global.
  EOT
  type    = string
  default = "swedencentral"
}

variable "resource_group_name" {
  description = "Nombre del Resource Group de Foundry (SEPARADO del de la VM)"
  type        = string
  default     = "jarvis-foundry-rg"
}

# ── AI Foundry ─────────────────────────────────────────────────────────────────

variable "hub_name" {
  description = "Nombre del AI Foundry Hub"
  type        = string
  default     = "jarvis-foundry-hub"
}

variable "project_name" {
  description = "Nombre del Proyecto dentro del Hub"
  type        = string
  default     = "jarvis-project"
}

# ── Model Deployments ──────────────────────────────────────────────────────────
# Estos valores se usan como nombre del deployment Y como valor en los secretos
# del Key Vault del terraform de la VM (infra/azure/terraform.tfvars).

variable "orchestrator_deployment" {
  description = <<-EOT
    Nombre del deployment Azure OpenAI para el orquestador del agente.
    Coincide con AZURE_ORCHESTRATOR_DEPLOYMENT en el .env de Jarvis.
  EOT
  type    = string
  default = "gpt-4o-mini"
}

variable "agent_deployment" {
  description = <<-EOT
    Nombre del deployment Azure OpenAI para los sub-agentes.
    Coincide con AZURE_AGENT_DEPLOYMENT en el .env de Jarvis.
  EOT
  type    = string
  default = "gpt-35-turbo"
}

