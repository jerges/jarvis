@api
Feature: Contrato REST del endpoint /api/jarvis/chat

  Background:
    * url baseUrl
    * def endpoint = '/api/jarvis/chat'

  Scenario: POST al endpoint de chat devuelve 200 y la estructura esperada
    Given path endpoint
    And request { message: 'How do I write a Java stream?', conversationId: 'karate-test-1' }
    When method POST
    Then status 200
    And match response == { routedTo: '#string', reasoning: '#string', response: '#string', provider: '#string' }

  Scenario: El campo routedTo contiene un tipo de agente válido
    Given path endpoint
    And request { message: 'Deploy to Kubernetes', conversationId: 'karate-test-2' }
    When method POST
    Then status 200
    And match response.routedTo == '#? _ == "DEVELOPER" || _ == "DEVOPS" || _ == "FRONTEND" || _ == "SOCIAL_MEDIA"'

  Scenario: El campo provider contiene un proveedor válido
    Given path endpoint
    And request { message: 'Explain React hooks', conversationId: 'karate-test-3' }
    When method POST
    Then status 200
    And match response.provider == '#? _ == "ANTHROPIC" || _ == "AZURE"'

  Scenario: Se puede especificar provider ANTHROPIC explícitamente
    Given path endpoint
    And request { message: 'How do I center a div?', conversationId: 'karate-test-4', provider: 'ANTHROPIC' }
    When method POST
    Then status 200
    And match response.provider == 'ANTHROPIC'

  Scenario: Se puede especificar provider AZURE explícitamente
    Given path endpoint
    And request { message: 'How do I center a div?', conversationId: 'karate-test-5', provider: 'AZURE' }
    When method POST
    Then status 200
    And match response.provider == 'AZURE'

  Scenario: El campo response no está vacío
    Given path endpoint
    And request { message: 'What is TypeScript?', conversationId: 'karate-test-6' }
    When method POST
    Then status 200
    And assert response.response.length > 0

  Scenario: La memoria de conversación mantiene contexto entre mensajes
    Given path endpoint
    And request { message: 'I am working on a Spring Boot project', conversationId: 'karate-memory-test' }
    When method POST
    Then status 200
    And def firstRoutedTo = response.routedTo

    Given path endpoint
    And request { message: 'How do I add a new endpoint to it?', conversationId: 'karate-memory-test' }
    When method POST
    Then status 200

  Scenario: Content-Type incorrecto devuelve 415
    Given path endpoint
    And header Content-Type = 'text/plain'
    And request 'plain text body'
    When method POST
    Then status 415
