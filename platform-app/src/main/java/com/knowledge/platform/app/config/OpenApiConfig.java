package com.knowledge.platform.app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI description of the backend's REST contract. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI knowledgePlatformOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Knowledge Platform API")
                        .version("v1")
                        .description("""
                                Read models, search and knowledge-graph APIs for the knowledge
                                publishing platform.

                                The backend returns JSON read models only. Article bodies arrive as a
                                structured block tree rather than HTML: presentation belongs to the
                                frontend, and keeping markup out of the API is also what keeps the
                                frontend from becoming a sanitisation boundary.

                                Public read endpoints are anonymous. Everything under /api/studio
                                requires the AUTHOR role. Webhook endpoints are anonymous at the HTTP
                                layer and authenticated by provider signature.
                                """))
                .components(new Components().addSecuritySchemes("basic",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")));
    }
}
