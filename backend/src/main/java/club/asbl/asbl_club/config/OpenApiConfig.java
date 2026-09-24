package club.asbl.asbl_club.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI asblClubOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("asbl.club API")
                        .version("v1")
                        .description("Public read API exposing associations and their public events. "
                                + "Reads are open with CORS for showcase sites. Everything else requires a JWT "
                                + "access token from POST /api/v1/auth/login, sent as a Bearer token."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
