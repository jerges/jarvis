package es.com.adakadavra.agent.jarvis.cucumber.config;

import es.com.adakadavra.agent.jarvis.agent.OrchestratorAgent;
import es.com.adakadavra.agent.jarvis.messaging.TelegramService;
import es.com.adakadavra.agent.jarvis.messaging.WhatsAppService;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class CucumberSpringConfig {

    @MockitoBean
    OrchestratorAgent orchestratorAgent;

    @MockitoBean
    TelegramService telegramService;

    @MockitoBean
    WhatsAppService whatsAppService;
}
