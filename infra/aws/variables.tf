variable "aws_region" {
  description = "Región AWS donde desplegar"
  type        = string
  default     = "us-east-1"
}

variable "instance_type" {
  description = "Tipo de instancia EC2. t3.micro es free-tier (1 GB RAM, ajustado para testing)"
  type        = string
  default     = "t3.micro"
  # Alternativas económicas:
  # t3.micro  → 1 GB RAM, ~$7.5/mes  (o gratis con free tier)
  # t3.small  → 2 GB RAM, ~$15/mes   (más cómodo)
  # t3.medium → 4 GB RAM, ~$30/mes   (producción ligera)
}

variable "ssh_public_key_path" {
  description = "Ruta a la clave SSH pública para acceder a la instancia"
  type        = string
  default     = "~/.ssh/id_rsa.pub"
}

variable "ssh_allowed_cidr" {
  description = "CIDR desde el que se permite SSH. Por defecto permite todo (cambia por tu IP para mayor seguridad)"
  type        = string
  default     = "0.0.0.0/0"
}

variable "repo_url" {
  description = "URL del repositorio git (debe ser accesible desde la instancia)"
  type        = string
}

# ── AI Providers ───────────────────────────────────────────────────────────────

variable "anthropic_api_key" {
  description = "API key de Anthropic"
  type        = string
  sensitive   = true
}

variable "jarvis_default_provider" {
  description = "Proveedor de IA por defecto: ANTHROPIC | AZURE"
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

# ── Telegram ───────────────────────────────────────────────────────────────────

variable "telegram_bot_token" {
  description = "Token del bot de Telegram (opcional)"
  type        = string
  sensitive   = true
  default     = ""
}

variable "telegram_webhook_secret" {
  description = "Secreto para verificar webhooks de Telegram (opcional)"
  type        = string
  sensitive   = true
  default     = ""
}

# ── WhatsApp ───────────────────────────────────────────────────────────────────

variable "whatsapp_access_token" {
  description = "Token de acceso de WhatsApp Cloud API (opcional)"
  type        = string
  sensitive   = true
  default     = ""
}

variable "whatsapp_phone_number_id" {
  description = "Phone Number ID de WhatsApp (opcional)"
  type        = string
  default     = ""
}

variable "whatsapp_verify_token" {
  description = "Token de verificación del webhook de WhatsApp (opcional)"
  type        = string
  sensitive   = true
  default     = ""
}
