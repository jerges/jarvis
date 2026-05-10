package es.com.adakadavra.agent.jarvis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestClient;

@Configuration
@EnableAsync
public class MessagingConfig {

    @Bean("telegramRestClient")
    public RestClient telegramRestClient() {
        return RestClient.builder()
                .baseUrl("https://api.telegram.org")
                .build();
    }

    @Bean("whatsappRestClient")
    public RestClient whatsappRestClient() {
        return RestClient.builder()
                .baseUrl("https://graph.facebook.com/v21.0")
                .build();
    }
}
