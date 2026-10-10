package com.transaction.dispute.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Transaction Dispute Portal API",
                version = "1.0.0",
                description = "RESTful API platform for South African Debit Order Dispute resolution (Standard EFT & DebiCheck) featuring automated risk evaluation and AWS SQS integration.",
                contact = @Contact(name = "Dispute Portal Engineering Team")
        ),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Enter JWT Bearer token obtained from /api/v1/auth/login or /api/v1/auth/register"
)
public class OpenApiConfig {
}