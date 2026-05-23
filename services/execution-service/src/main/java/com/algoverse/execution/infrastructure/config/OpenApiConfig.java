package com.algoverse.execution.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI 3 configuration.
 *
 * <p>Exposes Swagger UI at {@code /swagger-ui/index.html} and the raw spec at
 * {@code /v3/api-docs}. All secured endpoints require a Bearer JWT.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "BearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AlgoVerse Execution Service")
                        .description("Code submission judging and sandbox execution API. "
                                + "Accepts code submissions, runs them against test cases in an "
                                + "isolated gVisor sandbox, and streams real-time results via WebSocket.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("AlgoVerse Engineering")
                                .email("engineering@algoverse.io"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://algoverse.io")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .name(BEARER_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("RS256-signed JWT issued by the auth-service. "
                                        + "Pass in the Authorization header as: Bearer <token>")));
    }
}
