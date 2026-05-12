# Infraestructura Jarvis

Despliegue automatizado de Jarvis en AWS o Azure mediante **Terraform** + **Docker Compose**. Una sola VM económica ejecuta el backend (Spring Boot) y el frontend (Angular + nginx) como contenedores Docker.

## Índice

- [Arquitectura del despliegue](#arquitectura-del-despliegue)
- [Costes estimados](#costes-estimados)
- [Estructura de directorios](#estructura-de-directorios)
- [Prerrequisitos](#prerrequisitos)
- [Despliegue en AWS](#despliegue-en-aws)
- [Despliegue en Azure](#despliegue-en-azure)
- [Docker Compose local](#docker-compose-local)
- [Scripts de referencia](#scripts-de-referencia)
- [Configurar webhooks](#configurar-webhooks-tras-el-despliegue)
- [Actualizar la aplicación](#actualizar-la-aplicación)
- [Escalado de instancias](#escalado-de-instancias)
- [Seguridad](#seguridad)
- [Resolución de problemas](#resolución-de-problemas)

---

## Arquitectura del despliegue

```
Internet
   │
   ▼  :80 / :443
 nginx (jarvis-ui)          ← Angular SPA + proxy inverso
   │
   │  /api/*  →  :8080
   │  /webhook/* → :8080
   ▼
 Spring Boot (jarvis)       ← Agentes IA, REST API, webhooks
   │
   ├── Anthropic Claude API
   └── Azure OpenAI API (opcional)
```

- nginx sirve la SPA Angular y hace proxy inverso al backend.
- El backend **no expone puertos al exterior** (solo red Docker interna).
- El arranque del frontend espera a que el backend supere el healthcheck (`/actuator/health`).

---

## Costes estimados

| Nube | Instancia | RAM | Precio aprox. | Free tier |
|------|-----------|-----|---------------|-----------|
| AWS  | `t3.micro` | 1 GB | ~$7.5/mes | Sí — 750 h/mes primer año |
| Azure | `Standard_B1ms` | 2 GB | ~$15/mes | No (crédito inicial) |

Para producción con más comodidad:

| Nube | Instancia | RAM | Precio aprox. |
|------|-----------|-----|---------------|
| AWS  | `t3.small` | 2 GB | ~$15/mes |
| AWS  | `t3.medium` | 4 GB | ~$30/mes |
| Azure | `Standard_B2s` | 4 GB | ~$30/mes |

> El JVM está configurado con `-Xmx384m -Xms128m` para caber en el `t3.micro`. Si usas una instancia mayor puedes subir los límites en `jarvis/Dockerfile`.

---

## Estructura de directorios

```
infra/
├── aws/
│   ├── main.tf                  # VPC, SG, EC2, Elastic IP
│   ├── variables.tf             # Variables con valores por defecto
│   ├── outputs.tf               # IPs, URLs y comandos útiles
│   ├── user-data.sh             # Bootstrap EC2 (templatefile Terraform)
│   └── terraform.tfvars.example # Plantilla de configuración
├── azure/
│   ├── main.tf                  # Resource Group, VNet, NSG, VM
│   ├── variables.tf             # Variables con valores por defecto
│   ├── outputs.tf               # IPs, URLs y comandos útiles
│   ├── cloud-init.sh            # Bootstrap VM Azure (templatefile Terraform)
│   └── terraform.tfvars.example # Plantilla de configuración
└── scripts/
    ├── aws-deploy.sh            # Despliega infraestructura AWS
    ├── aws-destroy.sh           # Destruye infraestructura AWS
    ├── aws-update.sh            # Actualiza código en AWS (git pull + rebuild)
    ├── azure-deploy.sh          # Despliega infraestructura Azure
    ├── azure-destroy.sh         # Destruye infraestructura Azure
    └── azure-update.sh          # Actualiza código en Azure (git pull + rebuild)
```

---

## Prerrequisitos

### Herramientas

```bash
# Terraform >= 1.6
terraform -version

# AWS CLI (solo si despliegas en AWS)
aws --version

# Azure CLI (solo si despliegas en Azure)
az --version

# Docker (solo si ejecutas localmente)
docker --version
docker compose version
```

### Clave SSH

Necesitas un par de claves SSH existente (o créalo con `ssh-keygen`):

```bash
# Genera un par nuevo si no tienes uno
ssh-keygen -t rsa -b 4096 -f ~/.ssh/id_rsa -N ""
```

---

## Despliegue en AWS

### 1. Configurar credenciales AWS

```bash
# Opción A — Variables de entorno
export AWS_ACCESS_KEY_ID=AKIA...
export AWS_SECRET_ACCESS_KEY=...
export AWS_DEFAULT_REGION=us-east-1

# Opción B — Perfil configurado
aws configure
```

### 2. Crear `terraform.tfvars`

```bash
cd infra/aws
cp terraform.tfvars.example terraform.tfvars
```

Edita `terraform.tfvars`:

```hcl
# ── AWS ────────────────────────────────────────────────────────────────────────
aws_region          = "us-east-1"
instance_type       = "t3.micro"        # free tier (1 GB RAM)
ssh_public_key_path = "~/.ssh/id_rsa.pub"
ssh_allowed_cidr    = "1.2.3.4/32"     # tu IP pública — más seguro que 0.0.0.0/0

# ── Repositorio ────────────────────────────────────────────────────────────────
repo_url = "https://github.com/tu-usuario/jarvis.git"

# ── Anthropic (obligatorio) ────────────────────────────────────────────────────
anthropic_api_key       = "sk-ant-..."
jarvis_default_provider = "ANTHROPIC"   # ANTHROPIC | AZURE

# ── Azure AI Foundry (opcional) ────────────────────────────────────────────────
azure_openai_api_key          = ""
azure_openai_endpoint         = ""      # https://mi-recurso.openai.azure.com/
azure_orchestrator_deployment = "gpt-4o"
azure_agent_deployment        = "gpt-4o-mini"

# ── Telegram (opcional) ────────────────────────────────────────────────────────
telegram_bot_token      = ""
telegram_webhook_secret = ""

# ── WhatsApp (opcional) ────────────────────────────────────────────────────────
whatsapp_access_token    = ""
whatsapp_phone_number_id = ""
whatsapp_verify_token    = ""
```

### 3. Desplegar

```bash
chmod +x infra/scripts/aws-deploy.sh
./infra/scripts/aws-deploy.sh
```

El script ejecuta:
1. Verifica `terraform` y `aws` CLI instalados y con sesión activa
2. `terraform init -upgrade`
3. `terraform validate`
4. `terraform plan` — muestra los recursos que se crearán
5. Pide confirmación (`yes`)
6. `terraform apply` — aprovisiona toda la infraestructura

Al finalizar Terraform imprime los outputs:

```
app_url                  = "http://1.2.3.4"
ssh_command              = "ssh -i ~/.ssh/id_rsa ec2-user@1.2.3.4"
register_telegram_webhook = "curl 'https://api.telegram.org/bot<TOKEN>/setWebhook?...'"
```

> **Nota:** El arranque inicial puede tardar **5-10 minutos** (build Docker incluido). Sigue el progreso con:
> ```bash
> ssh -i ~/.ssh/id_rsa ec2-user@<IP> 'tail -f /var/log/jarvis-setup.log'
> ```

### Recursos AWS que se crean

| Recurso | Detalle |
|---------|---------|
| VPC | `10.0.0.0/16`, DNS habilitado |
| Internet Gateway | Adjunto a la VPC |
| Subnet | `10.0.1.0/24`, AZ `<region>a`, IP pública automática |
| Route Table | Ruta por defecto al IGW |
| Security Group | Inbound: 22 (tu CIDR), 80/443 (0.0.0.0/0). Outbound: todo |
| Key Pair | Importado desde `ssh_public_key_path` |
| EC2 Instance | AMI: Amazon Linux 2023, disco gp3 20 GB |
| Elastic IP | IP estática fija (sobrevive reinicios) |

---

## Despliegue en Azure

### 1. Autenticarse en Azure

```bash
az login
az account show   # verifica la suscripción activa
```

### 2. Crear `terraform.tfvars`

```bash
cd infra/azure
cp terraform.tfvars.example terraform.tfvars
```

Edita `terraform.tfvars`:

```hcl
# ── Azure ──────────────────────────────────────────────────────────────────────
location            = "westeurope"      # eastus, northeurope, etc.
resource_group_name = "jarvis-rg"
key_vault_name      = "jarvis-kv-<sufijo-unico>"  # debe ser globalmente único
vm_size             = "Standard_B1ms"   # 1 vCPU / 2 GB RAM, ~$15/mes
admin_username      = "azureuser"
ssh_public_key_path = "~/.ssh/id_rsa.pub"
ssh_allowed_cidr    = "1.2.3.4/32"     # tu IP pública

# ── Repositorio ────────────────────────────────────────────────────────────────
repo_url = "https://github.com/tu-usuario/jarvis.git"

# ── Anthropic (obligatorio) ────────────────────────────────────────────────────
anthropic_api_key       = ""             # opcional: si está vacío, Terraform crea placeholder en Key Vault
jarvis_default_provider = "ANTHROPIC"   # ANTHROPIC | AZURE

# ── Azure AI Foundry (opcional) ────────────────────────────────────────────────
azure_openai_api_key          = ""
azure_openai_endpoint         = ""      # https://mi-recurso.openai.azure.com/
azure_orchestrator_deployment = "gpt-4o"
azure_agent_deployment        = "gpt-4o-mini"

# ── Telegram (opcional) ────────────────────────────────────────────────────────
telegram_bot_token      = ""
telegram_webhook_secret = ""

# ── WhatsApp (opcional) ────────────────────────────────────────────────────────
whatsapp_access_token    = ""
whatsapp_phone_number_id = ""
whatsapp_verify_token    = ""
```

### 3. Desplegar

```bash
chmod +x infra/scripts/azure-deploy.sh
./infra/scripts/azure-deploy.sh
```

El script ejecuta:
1. Verifica `terraform` y `az` CLI instalados y con sesión activa
2. `terraform init -upgrade`
3. `terraform validate`
4. `terraform plan` — muestra los recursos que se crearán
5. Pide confirmación (`yes`)
6. `terraform apply` — aprovisiona toda la infraestructura

> **Nota:** El arranque inicial puede tardar **5-10 minutos** (build Docker incluido). Sigue el progreso con:
> ```bash
> ssh -i ~/.ssh/id_rsa azureuser@<IP> 'tail -f /var/log/jarvis-setup.log'
> ```

### Recursos Azure que se crean

| Recurso | Detalle |
|---------|---------|
| Resource Group | Nombre configurable, tag `Project=jarvis` |
| Virtual Network | `10.0.0.0/16` |
| Subnet | `10.0.1.0/24` |
| Public IP | Static, SKU Standard |
| NSG | Inbound: 22 (tu CIDR), 80/443 (Any). Outbound: todo |
| Network Interface | NIC + asociación al NSG |
| Key Vault | Contiene secretos de app (se crean placeholders iniciales) |
| Linux VM | Ubuntu 24.04 LTS, disco Standard_LRS 30 GB |

### Rellenar secretos en Key Vault (Portal)

Tras el `terraform apply`, usa los outputs para abrir el vault correcto:

```bash
cd infra/azure
terraform output key_vault_name
terraform output key_vault_secret_names
```

Luego en Azure Portal:
1. **Key Vaults** → tu vault
2. **Objects** → **Secrets**
3. Edita cada secreto con su valor real (los placeholders aparecen como `__PENDING_SET_IN_PORTAL__`)

---

## Docker Compose local

Para ejecutar la pila completa en tu máquina sin desplegar en la nube:

### 1. Crear `.env` en la raíz del repositorio

```bash
cat > .env << 'EOF'
# Obligatorio
ANTHROPIC_API_KEY=sk-ant-...

# Proveedor por defecto
JARVIS_DEFAULT_PROVIDER=ANTHROPIC

# Azure AI Foundry (opcional)
AZURE_OPENAI_API_KEY=
AZURE_OPENAI_ENDPOINT=
AZURE_ORCHESTRATOR_DEPLOYMENT=gpt-4o
AZURE_AGENT_DEPLOYMENT=gpt-4o-mini

# Telegram (opcional)
TELEGRAM_BOT_TOKEN=
TELEGRAM_WEBHOOK_SECRET=

# WhatsApp (opcional)
WHATSAPP_ACCESS_TOKEN=
WHATSAPP_PHONE_NUMBER_ID=
WHATSAPP_VERIFY_TOKEN=
EOF
```

### 2. Construir y levantar

```bash
# Desde la raíz del repositorio
docker compose up -d --build

# Ver logs
docker compose logs -f

# Estado de los contenedores
docker compose ps
```

La UI estará disponible en `http://localhost`.

### Contenedores

| Servicio | Imagen | Puerto externo | Puerto interno |
|----------|--------|---------------|----------------|
| `jarvis` | Spring Boot (JRE 21 Alpine) | — | 8080 |
| `jarvis-ui` | nginx Alpine | 80 | 80 |

El backend **no expone ningún puerto al host** — todo el tráfico externo pasa por nginx.

---

## Scripts de referencia

Todos los scripts están en `infra/scripts/`. Dales permisos de ejecución la primera vez:

```bash
chmod +x infra/scripts/*.sh
```

| Script | Qué hace |
|--------|----------|
| `aws-deploy.sh` | `terraform init → validate → plan → apply` en AWS |
| `aws-destroy.sh` | Destruye toda la infraestructura AWS (pide confirmación) |
| `aws-update.sh` | SSH a la instancia EC2, `git pull` + `docker compose up --build` |
| `azure-deploy.sh` | `terraform init → validate → plan → apply` en Azure |
| `azure-destroy.sh` | Destruye toda la infraestructura Azure (pide confirmación) |
| `azure-update.sh` | SSH a la VM, `git pull` + `docker compose up --build` |

### Variable `SSH_KEY_PATH`

Los scripts de actualización intentan deducir la clave privada SSH desde el estado de Terraform. Si falla o quieres usar una ruta diferente:

```bash
SSH_KEY_PATH=~/.ssh/mi-clave ./infra/scripts/aws-update.sh
```

---

## Configurar webhooks tras el despliegue

Tras el despliegue obtienes la IP pública desde los outputs de Terraform:

```bash
# Desde infra/aws/ o infra/azure/
terraform output public_ip
```

### Telegram

```bash
# Registra el webhook
curl "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/setWebhook\
?url=http://<IP>/webhook/telegram\
&secret_token=${TELEGRAM_WEBHOOK_SECRET}"

# Verifica el registro
curl "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/getWebhookInfo"
```

> Para usar HTTPS (recomendado en producción) necesitas un dominio y certificado TLS (p. ej. Certbot + Let's Encrypt).

### WhatsApp

1. Ve a [developers.facebook.com](https://developers.facebook.com) → tu app → **WhatsApp → Configuration → Webhook**
2. URL de callback: `http://<IP>/webhook/whatsapp`
3. Verify token: el valor de `WHATSAPP_VERIFY_TOKEN`
4. Haz clic en **Verify and Save** — Meta enviará un GET y Jarvis responderá automáticamente si el token coincide
5. Suscríbete al campo `messages`

---

## Actualizar la aplicación

Cuando hagas cambios en el código y los subas al repositorio:

```bash
# AWS
./infra/scripts/aws-update.sh

# Azure
./infra/scripts/azure-update.sh
```

El script hace:
1. Lee la IP pública desde el estado de Terraform
2. SSH a la instancia/VM
3. `cd /opt/jarvis && git pull`
4. `docker compose up -d --build --remove-orphans`
5. Muestra el estado de los contenedores

---

## Escalado de instancias

Cambia `instance_type` (AWS) o `vm_size` (Azure) en `terraform.tfvars` y aplica:

```bash
# AWS
cd infra/aws
terraform apply -var 'instance_type=t3.small'

# Azure
cd infra/azure
terraform apply -var 'vm_size=Standard_B2s'
```

> Terraform reemplazará la instancia (downtime breve). La IP pública se mantiene gracias al Elastic IP (AWS) y la IP Estática (Azure).

Si cambias a una instancia con más RAM, actualiza también los flags JVM en `jarvis/Dockerfile`:

```dockerfile
# Para 2 GB RAM (t3.small / Standard_B1ms con 2 GB):
ENTRYPOINT ["java", "-Xmx768m", "-Xms256m", "-XX:+UseContainerSupport", "-jar", "app.jar"]

# Para 4 GB RAM (t3.medium / Standard_B2s):
ENTRYPOINT ["java", "-Xmx1536m", "-Xms512m", "-XX:+UseContainerSupport", "-jar", "app.jar"]
```

---

## Seguridad

### SSH restringido

**Importante:** el valor por defecto de `ssh_allowed_cidr` es `0.0.0.0/0` (acceso SSH desde cualquier IP). Para producción, cámbialo por tu IP pública:

```hcl
ssh_allowed_cidr = "1.2.3.4/32"   # tu IP
```

### Secretos

- `terraform.tfvars` contiene las API keys — **nunca lo subas al repositorio**.
- El `.gitignore` del proyecto excluye `terraform.tfvars`, `*.tfstate` y el directorio `.terraform/`.
- En la instancia, el fichero `/opt/jarvis/.env` tiene permisos `600` (solo root puede leerlo).
- Las variables sensibles en Terraform están marcadas con `sensitive = true` (no aparecen en el plan ni en los logs).

### Actuator

El endpoint `/actuator/health` está disponible públicamente (lo usa el healthcheck Docker). Los detalles del estado están ocultos (`show-details=never`). El resto de endpoints de Actuator no están expuestos.

---

## Resolución de problemas

### Ver el log de arranque de la instancia

```bash
# AWS
ssh -i ~/.ssh/id_rsa ec2-user@<IP> 'tail -100 /var/log/jarvis-setup.log'

# Azure
ssh -i ~/.ssh/id_rsa azureuser@<IP> 'tail -100 /var/log/jarvis-setup.log'
```

### Estado de los contenedores

```bash
ssh ... 'cd /opt/jarvis && docker compose ps'
```

### Logs de los servicios

```bash
# Backend Spring Boot
ssh ... 'cd /opt/jarvis && docker compose logs jarvis --tail=100'

# Frontend nginx
ssh ... 'cd /opt/jarvis && docker compose logs jarvis-ui --tail=50'
```

### El healthcheck del backend no pasa

Spring Boot tarda en arrancar en instancias con 1 GB RAM. El compose espera hasta 90 segundos (`start_period: 90s`) antes de marcar el servicio como unhealthy. Si sigue fallando:

```bash
# Ver si el backend está ejecutándose
ssh ... 'cd /opt/jarvis && docker compose logs jarvis --tail=200'

# Revisar uso de memoria
ssh ... 'free -h && docker stats --no-stream'
```

### Limpiar y rebuildar todo

```bash
ssh ... 'cd /opt/jarvis && docker compose down && docker compose up -d --build'
```

### Error de permisos Docker en EC2

Si ves `permission denied` al ejecutar `docker` como `ec2-user` justo después del bootstrap, es porque el grupo Docker se aplica en la próxima sesión SSH:

```bash
# Reconecta la sesión SSH
exit
ssh -i ~/.ssh/id_rsa ec2-user@<IP>
```

### Terraform no encuentra la IP pública

```bash
# Asegúrate de estar en el directorio correcto
cd infra/aws   # o infra/azure
terraform output public_ip

# Si el estado está vacío, la infra no está desplegada
terraform show
```
