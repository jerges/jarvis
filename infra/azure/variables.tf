variable "location" {
  description = "Región de Azure"
  type        = string
  default     = "westeurope"
  # Alternativas económicas: eastus, northeurope, germanywestcentral
}

variable "resource_group_name" {
  description = "Nombre del Resource Group"
  type        = string
  default     = "jarvis-rg"
}

variable "key_vault_name" {
  description = "Nombre globalmente único para el Key Vault"
  type        = string
  default     = "jarvis-kv-jb"
}

variable "vm_size" {
  description = "Tamaño de la VM"
  type        = string
  default     = "Standard_B1ms"
  # Opciones económicas:
  # Standard_B1ms → 1 vCPU / 2 GB RAM — ~$15/mes  (testing)
  # Standard_B2s  → 2 vCPU / 4 GB RAM — ~$30/mes  (producción ligera)
}

variable "admin_username" {
  description = "Usuario administrador de la VM"
  type        = string
  default     = "azureuser"
}

variable "ssh_public_key_path" {
  description = "Ruta a la clave SSH pública"
  type        = string
  default     = "~/.ssh/id_rsa.pub"
}

variable "ssh_allowed_cidr" {
  description = "CIDR desde el que se permite SSH. Cambia por tu IP para mayor seguridad"
  type        = string
  default     = "0.0.0.0/0"
}

variable "repo_url" {
  description = "URL del repositorio git"
  type        = string
}

# ── AI Providers ───────────────────────────────────────────────────────────────

variable "anthropic_api_key" {
  description = "API key de Anthropic"
  type        = string
  sensitive   = true
  default     = ""
}

variable "jarvis_default_provider" {
  description = "Proveedor de IA por defecto: ANTHROPIC | AZURE | OLLAMA"
  type        = string
  default     = "ANTHROPIC"
}

# ── Azure AI Foundry (opcional) ────────────────────────────────────────────────

variable "azure_openai_api_key" {
  description = "API key de Azure OpenAI (opcional — deja vacío si usas solo Anthropic)"
  type        = string
  sensitive   = true
  default     = ""
}

variable "azure_openai_endpoint" {
  description = "Endpoint de Azure OpenAI, ej: https://mi-recurso.openai.azure.com/ (opcional)"
  type        = string
  default     = ""
}

variable "azure_orchestrator_deployment" {
  description = "Nombre del deployment Azure para el orquestador"
  type        = string
  default     = "gpt-4o"
}

variable "azure_agent_deployment" {
  description = "Nombre del deployment Azure para los agentes"
  type        = string
  default     = "gpt-4o-mini"
}

# ── Ollama (opcional) ──────────────────────────────────────────────────────────

variable "ollama_orchestrator_model" {
  description = "Modelo de Ollama para el orquestador"
  type        = string
  default     = "gemma4"
}

variable "ollama_agent_model" {
  description = "Modelo de Ollama para los agentes (programación, etc)"
  type        = string
  default     = "qwen3-coder:30b"
}

# ── Telegram ───────────────────────────────────────────────────────────────────

variable "telegram_bot_token" {
  type      = string
  sensitive = true
  default   = ""
}

variable "telegram_webhook_secret" {
  type      = string
  sensitive = true
  default   = ""
}

# ── WhatsApp ───────────────────────────────────────────────────────────────────

variable "whatsapp_access_token" {
  type      = string
  sensitive = true
  default   = ""
}

variable "whatsapp_phone_number_id" {
  type    = string
  default = "+34670305179"
}

variable "whatsapp_verify_token" {
  type      = string
  sensitive = true
  default   = ""
}
