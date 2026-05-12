# ── Identificadores generales ───────────────────────────────────────────────────

output "resource_group" {
  description = "Nombre del Resource Group de Foundry"
  value       = azurerm_resource_group.foundry.name
}

output "location" {
  description = "Región del Resource Group"
  value       = azurerm_resource_group.foundry.location
}

# ── Azure OpenAI ────────────────────────────────────────────────────────────────

output "openai_account_name" {
  description = "Nombre del recurso Azure OpenAI"
  value       = azurerm_cognitive_account.openai.name
}

output "openai_endpoint" {
  description = "Endpoint del servicio Azure OpenAI (ej: https://jarvis-oai-abc123.openai.azure.com/)"
  value       = azurerm_cognitive_account.openai.endpoint
}

output "openai_key" {
  description = "Primary API key del servicio Azure OpenAI"
  value       = azurerm_cognitive_account.openai.primary_access_key
  sensitive   = true
}

output "openai_location" {
  description = "Región donde está desplegado el Azure OpenAI"
  value       = azurerm_cognitive_account.openai.location
}

# ── Model Deployments ───────────────────────────────────────────────────────────

output "deployment_orchestrator" {
  description = "Nombre del deployment para el orquestador (gpt-4o-mini)"
  value       = azurerm_cognitive_deployment.gpt4o_mini.name
}

output "deployment_agent" {
  description = "Nombre del deployment para los sub-agentes (gpt-35-turbo)"
  value       = azurerm_cognitive_deployment.gpt35_turbo.name
}

output "deployment_embeddings" {
  description = "Nombre del deployment para embeddings (text-embedding-3-small)"
  value       = azurerm_cognitive_deployment.embeddings.name
}

# ── AI Foundry ──────────────────────────────────────────────────────────────────

output "foundry_hub_id" {
  description = "Resource ID del AI Foundry Hub"
  value       = azurerm_ai_foundry.hub.id
}

output "foundry_project_id" {
  description = "Resource ID del AI Foundry Project"
  value       = azurerm_ai_foundry_project.jarvis.id
}

output "foundry_portal_url" {
  description = "URL directa al proyecto en AI Foundry Portal"
  value       = "https://ai.azure.com/build/overview?wsid=${azurerm_ai_foundry_project.jarvis.id}"
}

# ── Key Vault ───────────────────────────────────────────────────────────────────

output "key_vault_name" {
  description = "Nombre del Key Vault de Foundry"
  value       = azurerm_key_vault.foundry.name
}

output "key_vault_uri" {
  description = "URI del Key Vault para recuperar secretos"
  value       = azurerm_key_vault.foundry.vault_uri
}

# ── Snippet .env para la app Jarvis ─────────────────────────────────────────────
# Copia este bloque en el .env de la VM o en infra/azure/terraform.tfvars

output "env_snippet" {
  description = "Variables de entorno listas para pegar en el .env de Jarvis"
  sensitive   = true
  value       = <<-ENV
    # ── Azure OpenAI — generado por infra/foundry ────────────────────────
    AZURE_OPENAI_ENDPOINT=${azurerm_cognitive_account.openai.endpoint}
    AZURE_OPENAI_API_KEY=${azurerm_cognitive_account.openai.primary_access_key}
    AZURE_ORCHESTRATOR_DEPLOYMENT=${azurerm_cognitive_deployment.gpt4o_mini.name}
    AZURE_AGENT_DEPLOYMENT=${azurerm_cognitive_deployment.gpt35_turbo.name}
    AZURE_EMBEDDING_DEPLOYMENT=${azurerm_cognitive_deployment.embeddings.name}
    JARVIS_DEFAULT_PROVIDER=AZURE
  ENV
}

