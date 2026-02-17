package com.codeops.registry.controller;

import com.codeops.registry.config.JwtProperties;
import com.codeops.registry.dto.response.ConfigTemplateResponse;
import com.codeops.registry.entity.enums.ConfigTemplateType;
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.exception.ValidationException;
import com.codeops.registry.service.ConfigEngineService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProperties jwtProperties;

    @MockBean
    private ConfigEngineService configEngineService;

    private static final UUID SERVICE_ID = UUID.randomUUID();
    private static final UUID SOLUTION_ID = UUID.randomUUID();
    private static final UUID TEMPLATE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    // ──────────────────────────────────────────────
    // Token builders
    // ──────────────────────────────────────────────

    private String buildToken(String... roles) {
        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes());
        return Jwts.builder()
                .subject(USER_ID.toString())
                .claim("email", "test@test.com")
                .claim("roles", List.of(roles))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private String architectToken() {
        return buildToken("ARCHITECT");
    }

    private String memberToken() {
        return buildToken("MEMBER");
    }

    // ──────────────────────────────────────────────
    // Sample response builders
    // ──────────────────────────────────────────────

    private ConfigTemplateResponse sampleConfigResponse(ConfigTemplateType type) {
        return new ConfigTemplateResponse(
                TEMPLATE_ID, SERVICE_ID, "Test Service", type,
                "local", "generated content", true, "registry-data",
                1, Instant.now(), Instant.now());
    }

    // ──────────────────────────────────────────────
    // Authentication tests — no auth → 401
    // ──────────────────────────────────────────────

    @Test
    void generateConfig_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/registry/services/{serviceId}/config/generate", SERVICE_ID)
                        .param("type", "DOCKER_COMPOSE"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getTemplate_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/registry/services/{serviceId}/config", SERVICE_ID)
                        .param("type", "DOCKER_COMPOSE")
                        .param("environment", "local"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteTemplate_noAuth_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/registry/config/{templateId}", TEMPLATE_ID))
                .andExpect(status().isUnauthorized());
    }

    // ──────────────────────────────────────────────
    // Authorization tests — MEMBER role → 403
    // ──────────────────────────────────────────────

    @Test
    void generateConfig_memberRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/registry/services/{serviceId}/config/generate", SERVICE_ID)
                        .header("Authorization", "Bearer " + memberToken())
                        .param("type", "DOCKER_COMPOSE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTemplatesForService_memberRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/registry/services/{serviceId}/config/all", SERVICE_ID)
                        .header("Authorization", "Bearer " + memberToken()))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────
    // Generate config tests
    // ──────────────────────────────────────────────

    @Test
    void generateDockerCompose_architectRole_returns200() throws Exception {
        when(configEngineService.generateDockerCompose(SERVICE_ID, "local"))
                .thenReturn(sampleConfigResponse(ConfigTemplateType.DOCKER_COMPOSE));

        mockMvc.perform(post("/api/v1/registry/services/{serviceId}/config/generate", SERVICE_ID)
                        .header("Authorization", "Bearer " + architectToken())
                        .param("type", "DOCKER_COMPOSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateType").value("DOCKER_COMPOSE"))
                .andExpect(jsonPath("$.environment").value("local"));
    }

    @Test
    void generateApplicationYml_architectRole_returns200() throws Exception {
        when(configEngineService.generateApplicationYml(SERVICE_ID, "local"))
                .thenReturn(sampleConfigResponse(ConfigTemplateType.APPLICATION_YML));

        mockMvc.perform(post("/api/v1/registry/services/{serviceId}/config/generate", SERVICE_ID)
                        .header("Authorization", "Bearer " + architectToken())
                        .param("type", "APPLICATION_YML"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateType").value("APPLICATION_YML"));
    }

    @Test
    void generateClaudeCodeHeader_architectRole_returns200() throws Exception {
        when(configEngineService.generateClaudeCodeHeader(SERVICE_ID, "local"))
                .thenReturn(sampleConfigResponse(ConfigTemplateType.CLAUDE_CODE_HEADER));

        mockMvc.perform(post("/api/v1/registry/services/{serviceId}/config/generate", SERVICE_ID)
                        .header("Authorization", "Bearer " + architectToken())
                        .param("type", "CLAUDE_CODE_HEADER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateType").value("CLAUDE_CODE_HEADER"));
    }

    @Test
    void generateConfig_unsupportedType_returns400() throws Exception {
        // ENV_FILE is not supported by the generate switch — returns ValidationException → 400
        mockMvc.perform(post("/api/v1/registry/services/{serviceId}/config/generate", SERVICE_ID)
                        .header("Authorization", "Bearer " + architectToken())
                        .param("type", "ENV_FILE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generateConfig_serviceNotFound_returns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(configEngineService.generateDockerCompose(missingId, "local"))
                .thenThrow(new NotFoundException("ServiceRegistration", missingId));

        mockMvc.perform(post("/api/v1/registry/services/{serviceId}/config/generate", missingId)
                        .header("Authorization", "Bearer " + architectToken())
                        .param("type", "DOCKER_COMPOSE"))
                .andExpect(status().isNotFound());
    }

    // ──────────────────────────────────────────────
    // Generate-all and solution tests
    // ──────────────────────────────────────────────

    @Test
    void generateAllConfigs_architectRole_returns200() throws Exception {
        when(configEngineService.generateAllForService(SERVICE_ID, "local"))
                .thenReturn(List.of(
                        sampleConfigResponse(ConfigTemplateType.DOCKER_COMPOSE),
                        sampleConfigResponse(ConfigTemplateType.APPLICATION_YML),
                        sampleConfigResponse(ConfigTemplateType.CLAUDE_CODE_HEADER)));

        mockMvc.perform(post("/api/v1/registry/services/{serviceId}/config/generate-all", SERVICE_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void generateSolutionDockerCompose_architectRole_returns200() throws Exception {
        when(configEngineService.generateSolutionDockerCompose(SOLUTION_ID, "local"))
                .thenReturn(sampleConfigResponse(ConfigTemplateType.DOCKER_COMPOSE));

        mockMvc.perform(post("/api/v1/registry/solutions/{solutionId}/config/docker-compose",
                        SOLUTION_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateType").value("DOCKER_COMPOSE"));
    }

    // ──────────────────────────────────────────────
    // Get template tests
    // ──────────────────────────────────────────────

    @Test
    void getTemplate_architectRole_returns200() throws Exception {
        when(configEngineService.getTemplate(SERVICE_ID, ConfigTemplateType.DOCKER_COMPOSE, "local"))
                .thenReturn(sampleConfigResponse(ConfigTemplateType.DOCKER_COMPOSE));

        mockMvc.perform(get("/api/v1/registry/services/{serviceId}/config", SERVICE_ID)
                        .header("Authorization", "Bearer " + architectToken())
                        .param("type", "DOCKER_COMPOSE")
                        .param("environment", "local"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateType").value("DOCKER_COMPOSE"))
                .andExpect(jsonPath("$.contentText").value("generated content"));
    }

    @Test
    void getTemplate_notFound_returns404() throws Exception {
        when(configEngineService.getTemplate(SERVICE_ID, ConfigTemplateType.APPLICATION_YML, "dev"))
                .thenThrow(new NotFoundException(
                        "ConfigTemplate for service " + SERVICE_ID + " type APPLICATION_YML env dev"));

        mockMvc.perform(get("/api/v1/registry/services/{serviceId}/config", SERVICE_ID)
                        .header("Authorization", "Bearer " + architectToken())
                        .param("type", "APPLICATION_YML")
                        .param("environment", "dev"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTemplatesForService_architectRole_returns200() throws Exception {
        when(configEngineService.getTemplatesForService(SERVICE_ID))
                .thenReturn(List.of(sampleConfigResponse(ConfigTemplateType.DOCKER_COMPOSE)));

        mockMvc.perform(get("/api/v1/registry/services/{serviceId}/config/all", SERVICE_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].templateType").value("DOCKER_COMPOSE"));
    }

    // ──────────────────────────────────────────────
    // Delete template tests
    // ──────────────────────────────────────────────

    @Test
    void deleteTemplate_architectRole_returns204() throws Exception {
        doNothing().when(configEngineService).deleteTemplate(TEMPLATE_ID);

        mockMvc.perform(delete("/api/v1/registry/config/{templateId}", TEMPLATE_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteTemplate_notFound_returns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        doThrow(new NotFoundException("ConfigTemplate", missingId))
                .when(configEngineService).deleteTemplate(missingId);

        mockMvc.perform(delete("/api/v1/registry/config/{templateId}", missingId)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isNotFound());
    }
}
