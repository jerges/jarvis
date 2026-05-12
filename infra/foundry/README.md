# Jarvis — Azure AI Foundry Infrastructure

> **Separado** del Terraform de la VM (`infra/azure/`).  
> Tiene su propio `terraform.tfstate` y Resource Group independiente.

## Qué despliega

| Recurso | Nombre | Coste estimado |
|---|---|---|
| Resource Group | `jarvis-foundry-rg` | Gratis |
| Storage Account (LRS) | `jfoundry<sufijo>` | ~\$0.02/GB/mes |
| Key Vault | `jfoundry-kv-<sufijo>` | ~\$0.03/10K ops |
| Azure OpenAI S0 | `jarvis-oai-<sufijo>` | Pago por uso |
| **gpt-4o-mini** GlobalStandard 30K TPM | `gpt-4o-mini` | \$0.15/M tokens input |
| **gpt-35-turbo 0125** GlobalStandard 30K TPM | `gpt-35-turbo` | \$0.50/M tokens input |
| **text-embedding-3-small** GlobalStandard 10K TPM | `text-embedding-3-small` | \$0.02/M tokens |
| AI Foundry Hub | `jarvis-foundry-hub` | Gratis (pagas los recursos adjuntos) |
| AI Foundry Project | `jarvis-project` | Gratis |

> **Consejo de coste**: GlobalStandard es pago por uso sin capacidad reservada.  
> Para dev/testing con tráfico bajo el coste total suele ser < \$5/mes.

## Prerequisitos

```bash
terraform -version    # >= 1.6
az --version          # cualquier versión reciente
az login              # sesión activa
az account show       # verifica suscripción correcta
```

## Despliegue rápido

```bash
# Desde la raíz del proyecto:
bash infra/scripts/foundry-deploy.sh

# O manualmente:
cd infra/foundry
cp terraform.tfvars.example terraform.tfvars   # ya existe uno por defecto
terraform init -upgrade
terraform plan -out=tfplan
terraform apply tfplan
```

## Obtener las variables para la app

```bash
cd infra/foundry

# Ver el endpoint y nombres de deployment (no sensibles)
terraform output openai_endpoint
terraform output deployment_orchestrator
terraform output deployment_agent

# Ver el snippet completo para .env (marcado como sensitive, usa -raw)
terraform output -raw env_snippet
```

Copia el output `env_snippet` directamente en:
- `.env` de la VM → `infra/azure/cloud-init.sh` los lee al arrancar Docker Compose
- `infra/azure/terraform.tfvars` → variables `azure_openai_endpoint`, `azure_openai_api_key`, etc.

## Conectar el OpenAI al Proyecto Foundry (portal)

La conexión entre el recurso Azure OpenAI y el Hub/Proyecto se finaliza en el portal
(no hay recurso Terraform estable para esto aún):

1. Ve a [https://ai.azure.com](https://ai.azure.com)
2. Selecciona **`jarvis-foundry-hub`**
3. En el menú izquierdo: **Settings → Connections → + New connection**
4. Tipo: **Azure OpenAI**
5. Endpoint: pega el valor de `terraform output openai_endpoint`
6. Asigna a todos los proyectos del hub ✓

## Destruir

```bash
bash infra/scripts/foundry-destroy.sh
```

> ⚠️ Esto elimina **todos** los recursos incluyendo el servicio Azure OpenAI y los deployments.

