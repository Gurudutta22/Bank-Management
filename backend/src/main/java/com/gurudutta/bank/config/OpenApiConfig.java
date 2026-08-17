package com.gurudutta.bank.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI bankOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("NovaBank API")
                        .version("v1")
                        .description("""
                                REST API for the NovaBank bank-management platform.

                                **How to authenticate in this UI**
                                1. Call `POST /api/v1/auth/login` with a demo account
                                   (`admin@novabank.io / Admin@123` or `priya@novabank.io / Customer@123`).
                                2. Copy the `accessToken` from the response.
                                3. Click **Authorize** at the top-right and paste the token.
                                """)
                        .contact(new Contact().name("Gurudutta Pradhan").email("guruduttapradhan140@gmail.com"))
                        .license(new License().name("MIT")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
