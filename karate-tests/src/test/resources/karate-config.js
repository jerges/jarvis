function fn() {
    var env = karate.env || 'local';
    karate.log('Karate env:', env);

    var baseUrl = karate.properties['baseUrl'] || 'http://localhost:8080';
    var uiUrl   = karate.properties['uiUrl']   || 'http://localhost';

    var config = {
        baseUrl: baseUrl,
        uiUrl:   uiUrl,
        chatEndpoint:             baseUrl + '/api/jarvis/chat',
        streamEndpoint:           baseUrl + '/api/jarvis/stream',
        telegramWebhookEndpoint:  baseUrl + '/webhook/telegram',
        whatsappWebhookEndpoint:  baseUrl + '/webhook/whatsapp',
        telegramWebhookSecret:    'test-webhook-secret',
        whatsappVerifyToken:      'test-verify-token'
    };

    karate.configure('connectTimeout', 10000);
    karate.configure('readTimeout',    30000);

    return config;
}
