package com.test.aieserver.config.swagger;

import com.test.aieserver.config.error.ApiErrorCodeExamples;
import com.test.aieserver.config.error.code.ErrorReason;
import com.test.aieserver.config.error.dto.ErrorResponse;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                .in(SecurityScheme.In.COOKIE).name("_aT");

        Info info = new Info().title("AI career API").version("1.0.0")
                .description("에이아이커리어 api server 입니다.")
                //.termsOfService("http://swagger.io/terms/")
                .contact(new Contact().name("sweun2").url("https://github.com/sweun2").email("sweun3@gmail.com"));
        Server server = new Server();

        return new OpenAPI()
                .components(new Components().addSecuritySchemes("bearerAuth", securityScheme))
                .servers(List.of(server))
                .info(info);
    }
    @Bean
    public OperationCustomizer customize() {
        return (Operation operation, HandlerMethod handlerMethod) -> {

            ApiErrorCodeExamples apiErrorCodeExamples = handlerMethod.getMethodAnnotation(ApiErrorCodeExamples.class);
            if(apiErrorCodeExamples!=null) {
                generateErrorCodeResponseExample(operation,apiErrorCodeExamples);
            }
            return operation;
        };
    }
    private void generateErrorCodeResponseExample(
            Operation operation, ApiErrorCodeExamples apiErrorCodeExamples) {
        ApiResponses responses = operation.getResponses();
        Map<Integer, List<ExampleHolder>> statusWithExampleHolders = Arrays.stream(apiErrorCodeExamples.value())
                .map(apiErrorCodeExample -> {
                    ErrorReason errorReason = apiErrorCodeExample.value().getErrorReason();
                    return ExampleHolder.builder()
                            .holder(getSwaggerExample(apiErrorCodeExample.value().getExplainError(), errorReason))
                            .code(errorReason.getStatus())
                            .name(errorReason.getCode())
                            .build();
                })
                .collect(Collectors.groupingBy(ExampleHolder::getCode));

        addExamplesToResponses(responses, statusWithExampleHolders);
    }
    private void addExamplesToResponses(ApiResponses responses, Map<Integer, List<ExampleHolder>> statusWithExampleHolders) {
        statusWithExampleHolders.forEach(
                (status, v) -> {
                    Content content = new Content();
                    MediaType mediaType = new MediaType();
                    ApiResponse apiResponse = new ApiResponse();
                    v.forEach(
                            exampleHolder -> mediaType.addExamples(
                                    exampleHolder.getName(), exampleHolder.getHolder()));
                    content.addMediaType("application/json", mediaType);
                    apiResponse.setContent(content);
                    responses.addApiResponse(status.toString(), apiResponse);
                });
    }
    private Example getSwaggerExample(String value, ErrorReason errorReason) {
        ErrorResponse errorResponse = new ErrorResponse(errorReason);
        Example example = new Example();
        example.description(value);
        example.setValue(errorResponse);
        return example;
    }
}