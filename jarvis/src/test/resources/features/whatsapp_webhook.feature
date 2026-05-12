# language: es
Característica: Webhook de WhatsApp

  Escenario: Meta verifica el webhook con el token correcto
    Cuando Meta verifica el webhook de WhatsApp con token "test-verify-token" y challenge "rnd_challenge_456"
    Entonces la respuesta contiene el challenge "rnd_challenge_456"

  Escenario: Meta verifica el webhook con token incorrecto es rechazado
    Cuando Meta verifica el webhook de WhatsApp con token "token-erroneo" y challenge "rnd_challenge_789"
    Entonces la verificación de WhatsApp es rechazada

  Escenario: Mensaje entrante de WhatsApp es procesado
    Cuando llega un mensaje de WhatsApp
    Entonces el servicio de WhatsApp procesa el mensaje
