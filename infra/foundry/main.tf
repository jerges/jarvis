# ────────────────────────────────────────────────────────────────────────────────
# Jarvis — Azure AI Foundry Infrastructure
#
# Estado : INDEPENDIENTE del terraform de la VM (infra/azure/).
#          Tiene su propio terraform.tfstate.
#
# Qué crea:
#   • Resource Group    jarvis-foundry-rg
#   • Azure OpenAI      (swedencentral — mayor disponibilidad de modelos en EU)
#       - gpt-4o-mini         → orquestador del agente (barato y capaz)
#       - gpt-35-turbo 0125   → sub-agentes / tareas sencillas (el más barato)
#       - text-embedding-3-small → vectorización (el más barato)
#   • Storage Account   (LRS, sin replicación geo — solo dev)
#   • Key Vault         (guarda el endpoint y la key del OpenAI automáticamente)
#   • AI Foundry Hub    (workspace central que agrupa proyectos)
#   • AI Foundry Project "jarvis-project"
#
# Requisitos:
#   • Terraform >= 1.6
#   • azurerm provider >= 4.3  (azurerm_ai_foundry requiere >= 4.3)
#   • az login con suscripción activa
#   • Script: infra/scripts/foundry-deploy.sh
# ────────────────────────────────────────────────────────────────────────────────

terraform {
  required_version = ">= 1.6"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.3"   # azurerm_ai_foundry y azurerm_ai_foundry_project requieren >= 4.3
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
  }
}

provider "azurerm" {
  features {
    key_vault {
      # Purga automáticamente en terraform destroy (evita bloqueo 7 días)
      purge_soft_delete_on_destroy    = true
      recover_soft_deleted_key_vaults = false
    }
    cognitive_account {
      # Purga el Azure OpenAI en destroy (evita soft-delete)
      purge_soft_delete_on_destroy = true
    }
  }
}

data "azurerm_client_config" "current" {}

# Sufijo de 6 chars para nombres globalmente únicos (Storage, Key Vault, OpenAI)
resource "random_string" "suffix" {
  length  = 6
  upper   = false
  special = false
}

locals {
  sfx = random_string.suffix.result

  tags = {
    Project     = "jarvis-foundry"
    Environment = "development"
    ManagedBy   = "terraform"
  }
}

# ── Resource Group ──────────────────────────────────────────────────────────────

resource "azurerm_resource_group" "foundry" {
  name     = var.resource_group_name
  location = var.location
  tags     = local.tags
}

# ── Storage Account (requerida por el AI Hub) ───────────────────────────────────
# LRS = opción más barata; sin replicación geográfica (ok para dev/testing)

resource "azurerm_storage_account" "foundry" {
  name                     = "jfoundry${local.sfx}"   # solo minúsculas+dígitos, max 24 chars
  resource_group_name      = azurerm_resource_group.foundry.name
  location                 = azurerm_resource_group.foundry.location
  account_tier             = "Standard"
  account_replication_type = "LRS"
  tags                     = local.tags
}

# ── Key Vault (requerida por el AI Hub + almacena el API key del OpenAI) ─────────

resource "azurerm_key_vault" "foundry" {
  name                       = "jfoundry-kv-${local.sfx}"   # max 24 chars
  resource_group_name        = azurerm_resource_group.foundry.name
  location                   = azurerm_resource_group.foundry.location
  tenant_id                  = data.azurerm_client_config.current.tenant_id
  sku_name                   = "standard"
  soft_delete_retention_days = 7
  purge_protection_enabled   = false
  tags                       = local.tags
}

# Política de acceso para el usuario que ejecuta terraform (tú)
resource "azurerm_key_vault_access_policy" "deployer" {
  key_vault_id = azurerm_key_vault.foundry.id
  tenant_id    = data.azurerm_client_config.current.tenant_id
  object_id    = data.azurerm_client_config.current.object_id

  secret_permissions = ["Get", "List", "Set", "Delete", "Purge", "Recover", "Backup", "Restore"]
}

# ── Azure OpenAI Service ────────────────────────────────────────────────────────
# swedencentral = mayor catálogo de modelos Azure OpenAI en la UE.
# Si prefieres eastus (más barato en egress) ajusta var.openai_location.

resource "azurerm_cognitive_account" "openai" {
  name                = "jarvis-oai-${local.sfx}"
  resource_group_name = azurerm_resource_group.foundry.name
  location            = var.openai_location
  kind                = "OpenAI"
  sku_name            = "S0"   # único SKU disponible para Azure OpenAI
  tags                = local.tags
}

# Almacena el endpoint y la primary key en Key Vault automáticamente
resource "azurerm_key_vault_secret" "openai_endpoint" {
  depends_on   = [azurerm_key_vault_access_policy.deployer]
  name         = "azure-openai-endpoint"
  value        = azurerm_cognitive_account.openai.endpoint
  key_vault_id = azurerm_key_vault.foundry.id
}

resource "azurerm_key_vault_secret" "openai_key" {
  depends_on   = [azurerm_key_vault_access_policy.deployer]
  name         = "azure-openai-key"
  value        = azurerm_cognitive_account.openai.primary_access_key
  key_vault_id = azurerm_key_vault.foundry.id
}

# ── Model Deployments ───────────────────────────────────────────────────────────
# Los deployments se crean en serie (depends_on) para evitar throttling del API.
# capacity = miles de tokens por minuto (KTPMs).
# GlobalStandard = pago por uso sin capacidad reservada = más barato para dev.

# gpt-4o-mini — el más capaz por coste; perfecto para el orquestador del agente
resource "azurerm_cognitive_deployment" "gpt4o_mini" {
  name                 = var.orchestrator_deployment
  cognitive_account_id = azurerm_cognitive_account.openai.id

  model {
    format  = "OpenAI"
    name    = "gpt-4o-mini"
    version = "2024-07-18"
  }

  sku {
    name     = "GlobalStandard"
    capacity = 30   # 30K TPM — suficiente para dev/testing
  }
}

# gpt-35-turbo — el más barato de todos; para las tareas de sub-agentes más simples
resource "azurerm_cognitive_deployment" "gpt35_turbo" {
  depends_on           = [azurerm_cognitive_deployment.gpt4o_mini]
  name                 = var.agent_deployment
  cognitive_account_id = azurerm_cognitive_account.openai.id

  model {
    format  = "OpenAI"
    name    = "gpt-35-turbo"
    version = "0125"
  }

  sku {
    name     = "GlobalStandard"
    capacity = 30
  }
}

# text-embedding-3-small — embeddings; el más barato para el vector store
resource "azurerm_cognitive_deployment" "embeddings" {
  depends_on           = [azurerm_cognitive_deployment.gpt35_turbo]
  name                 = "text-embedding-3-small"
  cognitive_account_id = azurerm_cognitive_account.openai.id

  model {
    format  = "OpenAI"
    name    = "text-embedding-3-small"
    version = "1"
  }

  sku {
    name     = "GlobalStandard"
    capacity = 10
  }
}

# ── Azure AI Foundry Hub ────────────────────────────────────────────────────────
# El Hub es el workspace central de AI Foundry; agrupa proyectos y recursos.
# Requiere azurerm >= 4.3

resource "azurerm_ai_foundry" "hub" {
  name                = var.hub_name
  resource_group_name = azurerm_resource_group.foundry.name
  location            = azurerm_resource_group.foundry.location
  storage_account_id  = azurerm_storage_account.foundry.id
  key_vault_id        = azurerm_key_vault.foundry.id
  tags                = local.tags

  identity {
    type = "SystemAssigned"
  }
}

# Permite que el Hub acceda a los secretos del Key Vault
resource "azurerm_key_vault_access_policy" "hub" {
  key_vault_id = azurerm_key_vault.foundry.id
  tenant_id    = data.azurerm_client_config.current.tenant_id
  object_id    = azurerm_ai_foundry.hub.identity[0].principal_id

  secret_permissions = ["Get", "List"]
}

# ── Azure AI Foundry Project ────────────────────────────────────────────────────
# El Project es el espacio de trabajo del equipo dentro del Hub.
# Desde aquí puedes conectar el OpenAI, ejecutar prompts, eval, etc.
#
# NOTA: La conexión del OpenAI al Project/Hub se hace en el Portal de Foundry:
#   https://ai.azure.com → Hub → Settings → Connections → + New connection
#   Type: Azure OpenAI  |  Endpoint: <output openai_endpoint>
#   Esta conexión no tiene recurso Terraform estable todavía (preview API).

resource "azurerm_ai_foundry_project" "jarvis" {
  name               = var.project_name
  location           = azurerm_resource_group.foundry.location
  ai_services_hub_id = azurerm_ai_foundry.hub.id
  tags               = local.tags

  identity {
    type = "SystemAssigned"
  }
}

