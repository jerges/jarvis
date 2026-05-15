# Jarvis — Agente Personal Multi-Modelo

Sistema de agentes inteligentes construido con **Java 21**, **Spring Boot 4** y **Spring AI**. Un agente orquestador analiza cada petición y la enruta automáticamente al agente especializado más adecuado. Soporta múltiples proveedores de IA (**Anthropic** y **Azure AI Foundry**) y múltiples canales de entrada (**REST API**, **Telegram** y **WhatsApp**).

## Arquitectura

```
Telegram Bot  ──►  TelegramWebhookController  ──►  TelegramService  ─┐
WhatsApp API  ──►  WhatsAppWebhookController  ──►  WhatsAppService  ─┤
REST API      ──►  JarvisController           ────────────────────────┤
                                                                       ▼
                                                                 JarvisAgent
                                                      (ANTHROPIC → Claude Opus 4.7)
                                                      (AZURE     → gpt-4o)
                                                               │
                                              ┌────────────────┼────────────────┐
                                              │                │                │
                                  SecretaryAgent / SecurityAgent / SocialMediaAgent / DeveloperAgent / DevOpsAgent / FrontendAgent
                                    (Sonnet 4.6 / gpt-4o-mini)
```

### Proveedores soportados

| Provider | Orquestador | Subagentes | Activación |
|----------|-------------|------------|------------|
| `ANTHROPIC` | `claude-opus-4-7` | `claude-sonnet-4-6` | Por defecto |
| `AZURE` | deployment `gpt-4o` (configurable) | deployment `gpt-4o-mini` (configurable) | Requiere credenciales Azure |

El proveedor puede cambiarse **por petición** con el campo `"provider"` en el JSON, o de forma global con la variable de entorno `JARVIS_DEFAULT_PROVIDER`.

### Canales de entrada

| Canal | Endpoint | Conversación |
|-------|----------|--------------|
| REST API | `POST /api/jarvis/chat` | por `conversationId` en el body |
| Telegram | `POST /webhook/telegram` | por `chat_id` de Telegram |
| WhatsApp | `POST /webhook/whatsapp` | por número de teléfono |

### Componentes clave

| Clase | Responsabilidad |
|-------|-----------------|
| `JarvisAgent` | Clasifica la petición con el modelo orquestador del provider elegido |
| `ChatClientFactory` | Crea y gestiona los `ChatClient` para cada provider (Anthropic / Azure) |
| `TelegramService` | Recibe updates de Telegram, llama al orquestador y devuelve la respuesta |
| `WhatsAppService` | Recibe mensajes de WhatsApp Cloud API, llama al orquestador y devuelve la respuesta |
| `MessagingConfig` | Crea los `RestClient` para Telegram y WhatsApp, habilita `@Async` |
| `ModelProvider` | Enum `ANTHROPIC` \| `AZURE` |
| Subagentes | `SecretaryAgent`, `SecurityAgent`, `SocialMediaAgent`, `DeveloperAgent`, `DevOpsAgent`, `FrontendAgent` |

### Funcionalidades

- **Multi-canal** — REST API, Telegram Bot y WhatsApp Cloud API
- **Multi-proveedor** — selección de provider por petición o por configuración global
- **Enrutamiento automático** — el orquestador clasifica la petición con output estructurado (`RoutingDecision`)
- **Memoria por conversación** — cada `chat_id` de Telegram y número de WhatsApp mantiene su propio contexto (últimos 20 mensajes)
- **Respuesta asíncrona** — los webhooks retornan HTTP 200 inmediatamente; el procesamiento ocurre en background para cumplir los timeouts de Telegram y WhatsApp
- **Streaming SSE** — endpoint `/stream` (solo REST) emite tokens en tiempo real
- **MCP tools** — soporte para herramientas MCP; se inyectan automáticamente a los subagentes si hay servidores configurados

## Requisitos

- Java 21+
- Gradle 9+ (el proyecto incluye wrapper con Gradle 9.4.1)
- Spring Boot 4.0.6 / Spring AI 2.0.0-M5
- API key de Anthropic (**obligatoria**)
- Credenciales Azure AI Foundry (**opcionales**, solo si se usa provider AZURE)
- Token de Telegram Bot (**opcional**, solo si se activa el canal Telegram)
- Credenciales WhatsApp Cloud API (**opcionales**, solo si se activa el canal WhatsApp)

## Configuración

### Anthropic (obligatorio)

```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

### Anthropic local OAuth (Claude CLI, pruebas locales)

Si quieres probar un flujo tipo OAuth en local (sin guardar manualmente la key), usa el helper para resolver el token:

```bash
chmod +x ./scripts/run-local-anthropic-oauth.sh
export ANTHROPIC_API_KEY="$(CLAUDE_OAUTH_TOKEN_COMMAND='claude auth print-token' ./scripts/run-local-anthropic-oauth.sh)"
```

Después puedes arrancar Jarvis por tu cuenta con el comando que prefieras.

También soporta:

```bash
# token ya exportado
export ANTHROPIC_OAUTH_TOKEN=<token>
export ANTHROPIC_API_KEY="$(./scripts/run-local-anthropic-oauth.sh)"

# token en fichero
export ANTHROPIC_OAUTH_TOKEN_FILE="$HOME/.config/claude/token"
export ANTHROPIC_API_KEY="$(./scripts/run-local-anthropic-oauth.sh)"
```

> Nota: este flujo es para desarrollo local. En producción, usa secretos gestionados y rotación.

> Importante: si `claude auth status` indica `authMethod: "claude.ai"`, esa sesión suele servir para Claude Code, pero no implica que exista un token/API key reutilizable para `Spring AI`. En ese caso tendrás que inyectar `ANTHROPIC_API_KEY` manualmente o usar un comando propio que te entregue un token real.

### Azure AI Foundry (opcional)

1. Crea un recurso **Azure OpenAI Service** en el portal de Azure.
2. Despliega los modelos que quieras usar (p. ej. `gpt-4o` y `gpt-4o-mini`) y apunta los nombres de deployment.
3. Exporta las variables:

```bash
export AZURE_OPENAI_API_KEY=<tu-api-key-azure>
export AZURE_OPENAI_ENDPOINT=https://<tu-recurso>.openai.azure.com/

# Opcional: nombres de deployment (por defecto: gpt-4o y gpt-4o-mini)
export AZURE_ORCHESTRATOR_DEPLOYMENT=gpt-4o
export AZURE_AGENT_DEPLOYMENT=gpt-4o-mini
```

O añádelos a `src/main/resources/application.properties`:

```properties
spring.ai.azure.openai.api-key=...
spring.ai.azure.openai.endpoint=https://<tu-recurso>.openai.azure.com/
jarvis.azure.orchestrator-deployment=gpt-4o
jarvis.azure.agent-deployment=gpt-4o-mini
```

### Proveedor por defecto

```bash
# ANTHROPIC (default) o AZURE
export JARVIS_DEFAULT_PROVIDER=AZURE
```

O en `application.properties`:

```properties
jarvis.ai.default-provider=AZURE
```

### Telegram Bot (opcional)

**Paso 1 — Crear el bot:**
1. Abre Telegram y busca `@BotFather`
2. Envía `/newbot` y sigue las instrucciones
3. Guarda el token que te proporciona

**Paso 2 — Configurar variables:**
```bash
export TELEGRAM_BOT_TOKEN=<token-de-BotFather>
# Recomendado: secreto para validar que el webhook viene de Telegram
export TELEGRAM_WEBHOOK_SECRET=<cadena-aleatoria-que-elijas>
```

**Paso 3 — Registrar el webhook** (una vez el servidor sea públicamente accesible):
```bash
curl "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/setWebhook\
?url=https://<tu-dominio>/webhook/telegram\
&secret_token=${TELEGRAM_WEBHOOK_SECRET}"
```

> Para desarrollo local puedes usar [ngrok](https://ngrok.com) para exponer el puerto 8080.

---

### WhatsApp Cloud API (opcional)

**Paso 1 — Crear la app en Meta:**
1. Ve a [developers.facebook.com](https://developers.facebook.com) → Crear app → Tipo: Business
2. Añade el producto **WhatsApp** a tu app
3. En **API Setup**, copia el **Phone Number ID** y genera un **token temporal** (o crea un token de sistema permanente en Meta Business Manager para producción)

**Paso 2 — Configurar variables:**
```bash
export WHATSAPP_ACCESS_TOKEN=<token-de-Meta>
export WHATSAPP_PHONE_NUMBER_ID=<phone-number-id>
# Token que tú defines — lo usarás en el siguiente paso
export WHATSAPP_VERIFY_TOKEN=<cadena-que-elijas>
```

**Paso 3 — Configurar el webhook en Meta:**
1. En el portal de Meta, ve a **WhatsApp → Configuration → Webhook**
2. URL de callback: `https://<tu-dominio>/webhook/whatsapp`
3. Verify token: el mismo valor de `WHATSAPP_VERIFY_TOKEN`
4. Suscríbete al campo `messages`

> Meta verificará la URL con un GET al endpoint `/webhook/whatsapp` — Jarvis responde automáticamente si el token coincide.

---

### MCP (opcional)

```properties
spring.ai.mcp.client.transport=stdio
spring.ai.mcp.client.stdio.command=npx
spring.ai.mcp.client.stdio.args=-y,@modelcontextprotocol/server-filesystem,/tmp
```

### Google Drive + Gmail OAuth (opcional)

1. Crea credenciales OAuth 2.0 en Google Cloud Console (tipo Web application).
2. Añade como redirect URI:
   - `http://localhost:8080/api/google/oauth/callback`
3. Exporta variables:

```bash
export GOOGLE_OAUTH_CLIENT_ID=<google-client-id>
export GOOGLE_OAUTH_CLIENT_SECRET=<google-client-secret>
export GOOGLE_OAUTH_REDIRECT_URI=http://localhost:8080/api/google/oauth/callback
export GOOGLE_OAUTH_SHARED_CONNECTION=true
```

Scopes por defecto:
- Drive metadata readonly: `https://www.googleapis.com/auth/drive.metadata.readonly`
- Gmail readonly: `https://www.googleapis.com/auth/gmail.readonly`

Cuando una conversación está conectada, los agentes pueden usar contexto resumido de Drive/Gmail durante sus respuestas.

`GOOGLE_OAUTH_SHARED_CONNECTION=true` (default) hace que la conexión OAuth se comparta en memoria entre todas las conversaciones.

## Ejecución

```bash
./gradlew bootRun
```

La aplicación arranca en `http://localhost:8080`.

## Endpoints

### `POST /api/jarvis/chat`

Respuesta completa con información de enrutamiento y proveedor utilizado.

**Request:**
```json
{
  "message": "cómo hago un deployment en Kubernetes con rolling update",
  "conversationId": "session-123",
  "provider": "AZURE"
}
```

> El campo `"provider"` es opcional. Si se omite, se usa el proveedor por defecto configurado.

**Response:**
```json
{
  "routedTo": "DEVOPS",
  "reasoning": "La pregunta trata sobre Kubernetes, que es dominio de infraestructura/DevOps",
  "response": "Para un rolling update en Kubernetes...",
  "provider": "AZURE"
}
```

### `POST /api/jarvis/stream`

Streaming en tiempo real (SSE). No incluye `routedTo`, `reasoning` ni `provider` — solo tokens.

```bash
curl -X POST http://localhost:8080/api/jarvis/stream \
  -H "Content-Type: application/json" \
  -d '{"message": "cómo optimizo una query SQL lenta", "conversationId": "abc", "provider": "ANTHROPIC"}' \
  --no-buffer
```

### Google OAuth / Workspace endpoints

```bash
# 1) Obtener URL de autorización para una conversación
curl "http://localhost:8080/api/google/oauth/url?conversationId=session-123"

# 2) Abrir la URL devuelta, autorizar en Google y completar callback

# 3) Ver estado de conexión
curl "http://localhost:8080/api/google/status?conversationId=session-123"

# 4) Consultar ficheros Drive
curl "http://localhost:8080/api/google/drive/files?conversationId=session-123&limit=5"

# 5) Consultar correos Gmail
curl "http://localhost:8080/api/google/gmail/messages?conversationId=session-123&limit=5"
```

### Memoria de conversación

```bash
# Primera pregunta (Azure)
curl -X POST http://localhost:8080/api/jarvis/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "estoy montando un pipeline de CI/CD con GitHub Actions", "conversationId": "mi-sesion", "provider": "AZURE"}'

# Seguimiento — el agente recuerda el contexto
curl -X POST http://localhost:8080/api/jarvis/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "cómo añado un paso de escaneo de seguridad?", "conversationId": "mi-sesion", "provider": "AZURE"}'
```

## Modelos utilizados

### Anthropic

| Rol | Modelo |
|-----|--------|
| Orquestador | `claude-opus-4-7` |
| Subagentes | `claude-sonnet-4-6` |

### Azure AI Foundry

| Rol | Deployment (configurable) |
|-----|--------------------------|
| Orquestador | `gpt-4o` (`AZURE_ORCHESTRATOR_DEPLOYMENT`) |
| Subagentes | `gpt-4o-mini` (`AZURE_AGENT_DEPLOYMENT`) |

> Los nombres de deployment deben coincidir exactamente con los configurados en tu recurso de Azure AI Foundry.

## Estructura del proyecto

```
src/main/java/es/com/adakadavra/agent/jarvis/
├── JarvisApplication.java
├── agent/
│   ├── Agent.java                  # Interfaz base (process/stream reciben ModelProvider)
│   ├── JarvisAgent.java            # Router principal — acepta AgentRequest completo
│   ├── SecretaryAgent.java
│   ├── SecurityAgent.java
│   ├── SocialMediaAgent.java
│   ├── DeveloperAgent.java
│   ├── DevOpsAgent.java
│   └── FrontendAgent.java
├── api/
│   ├── JarvisController.java       # Endpoints REST
│   ├── TelegramWebhookController.java  # POST /webhook/telegram
│   └── WhatsAppWebhookController.java  # GET+POST /webhook/whatsapp
├── config/
│   ├── AgentConfig.java            # Inyecta modelos tipados en ChatClientFactory
│   ├── ChatClientFactory.java      # Gestiona ChatClients por proveedor
│   └── MessagingConfig.java        # RestClient beans para Telegram/WhatsApp + @EnableAsync
├── messaging/
│   ├── TelegramService.java        # Procesa updates de Telegram (@Async)
│   └── WhatsAppService.java        # Procesa mensajes de WhatsApp (@Async)
└── model/
    ├── AgentType.java              # Enum: JARVIS, SECRETARY, SECURITY, SOCIAL_MEDIA, DEVELOPER, DEVOPS, FRONTEND
    ├── ModelProvider.java          # Enum: ANTHROPIC, AZURE
    ├── AgentRequest.java           # Record: message, conversationId, provider
    ├── AgentResponse.java          # Record: routedTo, reasoning, response, provider
    └── RoutingDecision.java        # Output estructurado del orquestador
```
