package com.mealpilot.api.config;

import com.mealpilot.api.error.ApiErrorResponse;
import com.mealpilot.api.error.ApiFieldError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "MealPilot API",
        version = "v1",
        description = "Decision-first food ordering assistant (JWT-protected APIs)"
    )
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
public class OpenApiConfig {

  static {
    // Records should be treated as schemas consistently.
    SpringDocUtils.getConfig().addResponseTypeToIgnore(org.springframework.http.ResponseEntity.class);
  }

  @Bean
  OpenAPI mealpilotOpenApi() {
    return new OpenAPI()
        .components(new Components())
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
  }

  @Bean
  OpenApiCustomizer defaultErrorResponsesCustomizer() {
    return openApi -> {
      if (openApi.getComponents() == null) {
        openApi.setComponents(new Components());
      }

      // Springdoc does not automatically add schemas that are only referenced via a manual $ref.
      // Ensure ApiErrorResponse exists under components/schemas so toolchains (e.g. openapi-typescript)
      // can resolve it.
      ModelConverters.getInstance()
          .read(ApiErrorResponse.class)
          .forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));

        // ApiErrorResponse references ApiFieldError for fieldErrors[]. Register it too so $refs resolve.
        ModelConverters.getInstance()
          .read(ApiFieldError.class)
          .forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));

      // Ensure the error schema exists for $ref usage.
      // Springdoc will generate it, but we also reference it below.
      Schema<?> errorSchemaRef = new Schema<>().$ref("#/components/schemas/ApiErrorResponse");

      openApi.getPaths().forEach((path, pathItem) -> {
        pathItem.readOperations().forEach(op -> {
          ApiResponses responses = op.getResponses();
          if (responses == null) {
            responses = new ApiResponses();
            op.setResponses(responses);
          }

          ensureJsonError(responses, "400", "Bad Request", errorSchemaRef);
          ensureJsonError(responses, "401", "Unauthorized", errorSchemaRef);
          ensureJsonError(responses, "403", "Forbidden", errorSchemaRef);
          ensureJsonError(responses, "404", "Not Found", errorSchemaRef);
          ensureJsonError(responses, "500", "Internal Server Error", errorSchemaRef);
        });
      });
    };
  }

  private static void ensureJsonError(ApiResponses responses, String status, String description, Schema<?> schemaRef) {
    if (responses.containsKey(status)) {
      return;
    }

    Content content = new Content()
        .addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
            new MediaType().schema(schemaRef));

    responses.addApiResponse(status, new ApiResponse().description(description).content(content));
  }
}
