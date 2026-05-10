# Jarvis — Agente Personal Multi-Modelo

Sistema de agentes inteligentes construido con **Java 21**, **Spring Boot 4** y **Spring AI**. Un agente orquestador analiza cada petición y la enruta automáticamente al agente especializado más adecuado.

## Arquitectura

```
POST /api/jarvis/chat  ──►  OrchestratorAgent (Claude Opus 4.7)
POST /api/jarvis/stream      │
                             │  clasifica la petición
                             │
             ┌───────────────┼───────────────┬─────────────────┐
             │               │               │                 │
     SocialMediaAgent  DeveloperAgent   DevOpsAgent      FrontendAgent
      (Sonnet 4.6)    (Sonnet 4.6)    (Sonnet 4.6)     (Sonnet 4.6)
     WhatsApp/Telegram  Código/APIs    K8s/CI/CD         React/UI/UX
     Redes sociales     Arquitectura   Cloud/IaC          Diseño web
```

### Componentes

| Clase | Responsabilidad |
|-------|-----------------|
| `OrchestratorAgent` | Analiza la petición con Claude Opus y decide qué agente la maneja |
| `SocialMediaAgent` | Experto en redes sociales, WhatsApp, Telegram, bots |
| `DeveloperAgent` | Desarrollador senior: código, arquitectura, debugging |
| `DevOpsAgent` | Infraestructura, Kubernetes, CI/CD, cloud |
| `FrontendAgent` | Frontend y diseño: React, CSS, UI/UX, Figma |
| `AgentConfig` | Define los `ChatClient` beans: `orchestratorClient` (Opus 4.7) y `agentClient` compartido por todos los subagentes (Sonnet 4.6) |

### Funcionalidades

- **Enrutamiento automático** — el orquestador clasifica la petición con output estructurado (`RoutingDecision`)
- **Memoria por conversación** — `MessageChatMemoryAdvisor` con `InMemoryChatMemory`, mantiene los últimos 20 mensajes por `conversationId`
- **Streaming SSE** — endpoint `/stream` emite tokens en tiempo real vía Server-Sent Events (sin metadatos de enrutamiento)
- **MCP tools** — soporte para herramientas MCP client y server; los tools se inyectan automáticamente a los subagentes si hay servidores configurados

## Requisitos

- Java 21+
- Gradle 9+ (el proyecto incluye wrapper con Gradle 9.4.1)
- Spring Boot 4.0.6 / Spring AI 2.0.0-M5
- API key de Anthropic

## Configuración

Exporta la variable de entorno con tu API key:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

O añádela a `src/main/resources/application.properties`:

```properties
spring.ai.anthropic.api-key=sk-ant-...
```

### MCP (opcional)

Para conectar servidores MCP externos (client), descomenta y ajusta en `application.properties`:

```properties
spring.ai.mcp.client.transport=stdio
spring.ai.mcp.client.stdio.command=npx
spring.ai.mcp.client.stdio.args=-y,@modelcontextprotocol/server-filesystem,/tmp
```

## Ejecución

```bash
./gradlew bootRun
```

La aplicación arranca en `http://localhost:8080`.

## Endpoints

### `POST /api/jarvis/chat`

Respuesta completa con información de enrutamiento.

**Request:**
```json
{
  "message": "cómo hago un deployment en Kubernetes con rolling update",
  "conversationId": "session-123"
}
```

**Response:**
```json
{
  "routedTo": "DEVOPS",
  "reasoning": "La pregunta trata sobre Kubernetes, que es dominio de infraestructura/DevOps",
  "response": "Para un rolling update en Kubernetes..."
}
```

### `POST /api/jarvis/stream`

Streaming de la respuesta en tiempo real (Server-Sent Events). Emite solo los tokens del agente seleccionado; no incluye `routedTo` ni `reasoning`.

**Request:**
```json
{
  "message": "escríbeme un componente React con Tailwind para un formulario de login",
  "conversationId": "session-456"
}
```

**Response:** `text/event-stream` con los tokens del agente seleccionado.

```bash
# Ejemplo con curl
curl -X POST http://localhost:8080/api/jarvis/stream \
  -H "Content-Type: application/json" \
  -d '{"message": "cómo optimizo una query SQL lenta", "conversationId": "abc"}' \
  --no-buffer
```

### Memoria de conversación

Usa el mismo `conversationId` en peticiones sucesivas para mantener el contexto:

```bash
# Primera pregunta
curl -X POST http://localhost:8080/api/jarvis/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "estoy montando un pipeline de CI/CD con GitHub Actions", "conversationId": "mi-sesion"}'

# Pregunta de seguimiento — el agente recuerda el contexto anterior
curl -X POST http://localhost:8080/api/jarvis/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "cómo añado un paso de escaneo de seguridad?", "conversationId": "mi-sesion"}'
```

## Modelos utilizados

| Rol | Modelo | Por qué |
|-----|--------|---------|
| Orquestador | `claude-opus-4-7` | Mayor capacidad de razonamiento para clasificar correctamente |
| Subagentes | `claude-sonnet-4-6` | Equilibrio entre velocidad y calidad para respuestas especializadas |

## Estructura del proyecto

```
src/main/java/es/com/adakadavra/agent/jarvis/
├── JarvisApplication.java
├── agent/
│   ├── Agent.java                  # Interfaz base
│   ├── OrchestratorAgent.java      # Router principal
│   ├── SocialMediaAgent.java
│   ├── DeveloperAgent.java
│   ├── DevOpsAgent.java
│   └── FrontendAgent.java
├── api/
│   └── JarvisController.java       # Endpoints REST
├── config/
│   └── AgentConfig.java            # Beans de ChatClient y memoria
└── model/
    ├── AgentType.java              # Enum de tipos de agente
    ├── AgentRequest.java           # Record de entrada
    ├── AgentResponse.java          # Record de salida
    └── RoutingDecision.java        # Output estructurado del orquestador
```
