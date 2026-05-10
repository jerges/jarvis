# Jarvis UI — Frontend Angular

Interfaz de pruebas para el agente personal Jarvis. Permite enviar peticiones al backend y visualizar en tiempo real qué agente las maneja y la respuesta.

## Requisitos

- Node 18+
- Backend `jarvis` corriendo en `http://localhost:8080`

## Instalación

```bash
npm install
```

## Ejecución

```bash
npm start
```

Abre `http://localhost:4200`. Las llamadas a `/api/*` se proxean automáticamente al backend en `:8080`.

## Funcionalidades

- **Modo Chat** — envía la petición y recibe la respuesta completa con información de enrutamiento (qué agente respondió y por qué)
- **Modo Stream** — recibe los tokens en tiempo real vía SSE con cursor animado
- **Hints de ejemplo** — 4 ejemplos rápidos para probar cada agente
- **Memoria de conversación** — el `conversationId` se mantiene por sesión; "Nueva conversación" genera uno nuevo
- **Badge de agente** — cada respuesta muestra con color qué agente la procesó

## Agentes y colores

| Agente | Color | Descripción |
|--------|-------|-------------|
| 💻 Developer | Azul | Código, arquitectura, APIs |
| ⚙️ DevOps | Naranja | Kubernetes, CI/CD, cloud |
| 📱 Social Media | Verde | WhatsApp, Telegram, redes |
| 🎨 Frontend | Morado | React, CSS, UI/UX |
