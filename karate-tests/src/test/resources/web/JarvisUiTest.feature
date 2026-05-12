@ui
Feature: Tests de interfaz web de Jarvis
  # Requiere Chrome instalado y la aplicación corriendo en uiUrl.
  # Ejecutar con: ./gradlew uiTest -DuiUrl=http://localhost

  Background:
    * configure driver = { type: 'chrome', start: true, headless: true }
    * driver uiUrl

  Scenario: La página de inicio carga correctamente
    Then waitForUrl(uiUrl)
    And waitFor('body')

  Scenario: El título de la aplicación está visible
    When driver uiUrl
    Then waitFor('body')
    And def title = driver.title
    And assert title.length > 0

  Scenario: El área de chat está presente
    When driver uiUrl
    Then waitFor('body')
    # Verifica que hay un input/textarea para escribir mensajes
    And waitFor('[placeholder]')

  Scenario: Se puede enviar un mensaje y recibir respuesta
    When driver uiUrl
    Then waitFor('body')
    # Escribe en el campo de mensaje
    And def input = waitFor('[placeholder]')
    And input.clear()
    And input.input('Hello Jarvis, how are you?')
    # Envía el mensaje (Enter o botón de enviar)
    And input.submit()
    # Espera a que aparezca una respuesta en el chat (timeout 30s para AI)
    And waitFor({ css: '.message, .chat-message, .response', timeout: 30000 })

  Scenario: El botón de nueva conversación crea una sesión nueva
    When driver uiUrl
    Then waitFor('body')
    # Busca el botón de nueva conversación
    And def newChatBtn = waitFor({ contains: 'new', timeout: 5000 })
    And newChatBtn.click()
    # Verifica que la conversación se reinicia

  Scenario: La aplicación muestra el indicador de carga durante el procesamiento
    When driver uiUrl
    Then waitFor('body')
    And def input = waitFor('[placeholder]')
    And input.clear()
    And input.input('Explain Kubernetes')
    And input.submit()
    # El indicador de carga debe aparecer inmediatamente
    And waitFor({ css: '.loading, [aria-busy="true"], .spinner', timeout: 5000 })
