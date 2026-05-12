# language: es
Característica: Webhook de Telegram

  Escenario: Webhook con secreto correcto es aceptado
    Dado el secreto de Telegram configurado es "test-webhook-secret"
    Cuando llega un webhook de Telegram con secreto "test-webhook-secret"
    Entonces el servicio de Telegram procesa el update

  Escenario: Webhook con secreto incorrecto es rechazado
    Dado el secreto de Telegram configurado es "test-webhook-secret"
    Cuando llega un webhook de Telegram con secreto "secreto-incorrecto"
    Entonces el servicio de Telegram no procesa el update

  Escenario: Webhook sin cabecera de secreto es rechazado cuando hay secreto configurado
    Dado el secreto de Telegram configurado es "test-webhook-secret"
    Cuando llega un webhook de Telegram sin cabecera de secreto
    Entonces el servicio de Telegram no procesa el update
