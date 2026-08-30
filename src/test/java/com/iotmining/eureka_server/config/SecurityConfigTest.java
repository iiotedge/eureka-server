package com.iotmining.eureka_server.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the actual auth boundary, not just that the app boots -
 * SecurityConfig reconciles two earlier, disagreeing drafts (see its own
 * doc comment), so this pins down the final, intended answer as a
 * regression test: the registry API and the dashboard both require
 * credentials, health/error don't.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${spring.security.user.name}")
    private String username;

    @Value("${spring.security.user.password}")
    private String password;

    @Test
    void eurekaApi_requiresAuth() throws Exception {
        mockMvc.perform(get("/eureka/apps"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void eurekaApi_notRejectedByAuth_withValidCredentials() throws Exception {
        // Not isOk() - Eureka's actual /eureka/** registry API is
        // implemented via a Jersey servlet registered alongside Spring
        // MVC, not a @RestController, so @AutoConfigureMockMvc's mock
        // dispatcher (Spring MVC handler lookup only) 404s on it
        // regardless of credentials - a real running server routes there
        // correctly via the real Jersey servlet, verified with a live
        // boot instead of MockMvc for that reason. What this test can
        // and should still prove: valid credentials get past Security
        // itself (not a 401), which is this class's actual scope.
        mockMvc.perform(get("/eureka/apps")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic(username, password)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401 || status == 403) {
                        throw new AssertionError("Valid credentials were rejected by security: " + status);
                    }
                });
    }

    @Test
    void dashboard_requiresAuth() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dashboard_succeedsWithValidCredentials() throws Exception {
        mockMvc.perform(get("/")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic(username, password)))
                .andExpect(status().isOk());
    }

    @Test
    void actuatorHealth_isPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
