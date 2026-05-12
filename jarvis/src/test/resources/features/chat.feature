# language: es
Característica: API de chat de Jarvis

  Escenario: Mensaje de desarrollo es enrutado al agente correcto
    Dado el orquestador responde con agente "DEVELOPER" y mensaje "Aquí te explico cómo usar streams en Java"
    Cuando envío un mensaje de chat "Cómo uso streams en Java?" con conversationId "conv-dev-1"
    Entonces la respuesta HTTP es 200
    Y el campo routedTo es "DEVELOPER"
    Y el campo provider es "ANTHROPIC"
    Y la respuesta contiene un campo response no vacío
    Y la respuesta contiene un campo reasoning

  Escenario: Pregunta de infraestructura es enrutada al agente DevOps
    Dado el orquestador responde con agente "DEVOPS" y mensaje "Usa kubectl apply -f deployment.yaml"
    Cuando envío un mensaje de chat "Cómo hago un deploy en Kubernetes?" con conversationId "conv-k8s-1"
    Entonces la respuesta HTTP es 200
    Y el campo routedTo es "DEVOPS"

  Escenario: Pregunta de frontend es enrutada al agente Frontend
    Dado el orquestador responde con agente "FRONTEND" y mensaje "Usa display: flex y justify-content: center"
    Cuando envío un mensaje de chat "Cómo centro un div?" con conversationId "conv-css-1"
    Entonces la respuesta HTTP es 200
    Y el campo routedTo es "FRONTEND"

  Escenario: Pregunta de redes sociales es enrutada al agente Social Media
    Dado el orquestador responde con agente "SOCIAL_MEDIA" y mensaje "Publica contenido consistente y auténtico"
    Cuando envío un mensaje de chat "Cómo crezco en Instagram?" con conversationId "conv-social-1"
    Entonces la respuesta HTTP es 200
    Y el campo routedTo es "SOCIAL_MEDIA"

  Escenario: Se puede seleccionar el proveedor Azure por petición
    Dado el orquestador responde con agente "DEVELOPER" usando Azure
    Cuando envío un mensaje de chat "Explica el patrón Repository" con provider "AZURE"
    Entonces la respuesta HTTP es 200
    Y el campo provider es "AZURE"

  Escenario: Se puede seleccionar el proveedor Anthropic explícitamente
    Dado el orquestador responde con agente "DEVOPS" y mensaje "Usa Terraform para provisionar infraestructura"
    Cuando envío un mensaje de chat "Cómo uso Terraform?" con provider "ANTHROPIC"
    Entonces la respuesta HTTP es 200
    Y el campo provider es "ANTHROPIC"

  Escenario: Pregunta de ciberseguridad es enrutada al agente Security
    Dado el orquestador responde con agente "SECURITY" y mensaje "Usa Burp Suite para interceptar las peticiones HTTP y analizar las vulnerabilidades"
    Cuando envío un mensaje de chat "Cómo hago un pentest a una aplicación web?" con conversationId "conv-sec-1"
    Entonces la respuesta HTTP es 200
    Y el campo routedTo es "SECURITY"
    Y la respuesta contiene un campo response no vacío
