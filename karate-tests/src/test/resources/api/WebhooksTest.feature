@api
Feature: Contrato REST de los webhooks de Telegram y WhatsApp

  Background:
    * url baseUrl

  # ── Telegram ─────────────────────────────────────────────────────────────────

  Scenario: POST webhook Telegram con secreto correcto devuelve 200
    Given path '/webhook/telegram'
    And header X-Telegram-Bot-Api-Secret-Token = telegramWebhookSecret
    And request { update_id: 1, message: { chat: { id: 123 }, text: 'Hello' } }
    When method POST
    Then status 200

  Scenario: POST webhook Telegram con secreto incorrecto devuelve 401
    Given path '/webhook/telegram'
    And header X-Telegram-Bot-Api-Secret-Token = 'wrong-secret'
    And request { update_id: 1, message: { chat: { id: 123 }, text: 'Hello' } }
    When method POST
    Then status 401

  Scenario: POST webhook Telegram sin cabecera de secreto devuelve 401
    Given path '/webhook/telegram'
    And request { update_id: 1, message: { chat: { id: 456 }, text: 'Hello' } }
    When method POST
    Then status 401

  # ── WhatsApp ─────────────────────────────────────────────────────────────────

  Scenario: GET webhook WhatsApp con token correcto devuelve el challenge
    Given path '/webhook/whatsapp'
    And param hub.mode = 'subscribe'
    And param hub.verify_token = whatsappVerifyToken
    And param hub.challenge = 'challenge-xyz-789'
    When method GET
    Then status 200
    And match response == 'challenge-xyz-789'

  Scenario: GET webhook WhatsApp con token incorrecto devuelve 403
    Given path '/webhook/whatsapp'
    And param hub.mode = 'subscribe'
    And param hub.verify_token = 'wrong-token'
    And param hub.challenge = 'challenge-xyz-789'
    When method GET
    Then status 403

  Scenario: GET webhook WhatsApp sin modo subscribe devuelve 403
    Given path '/webhook/whatsapp'
    And param hub.mode = 'unsubscribe'
    And param hub.verify_token = whatsappVerifyToken
    And param hub.challenge = 'challenge-xyz-789'
    When method GET
    Then status 403

  Scenario: POST webhook WhatsApp con payload válido devuelve 200
    Given path '/webhook/whatsapp'
    And request
    """
    {
      "entry": [{
        "changes": [{
          "value": {
            "messages": [{
              "type": "text",
              "from": "34600000000",
              "text": { "body": "Hola Jarvis" }
            }]
          }
        }]
      }]
    }
    """
    When method POST
    Then status 200

  # ── Actuator ─────────────────────────────────────────────────────────────────

  Scenario: Health check del actuator devuelve UP
    Given path '/actuator/health'
    When method GET
    Then status 200
    And match response.status == 'UP'
