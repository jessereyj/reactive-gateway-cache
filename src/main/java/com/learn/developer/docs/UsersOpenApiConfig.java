package com.learn.developer.docs;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

@Configuration
public class UsersOpenApiConfig {

    @Bean
    public OpenAPI usersProxyOpenAPI() {
        // Schemas (simple examples; adjust to your actual payload)
        Schema<?> userSchema = new Schema<Map<String, Object>>()
                .type("object")
                .addProperty("id", new Schema<String>().type("string").example("123"))
                .addProperty("name", new Schema<String>().type("string").example("Jane Doe"))
                .addProperty("email", new Schema<String>().type("string").example("jane@example.com"))
                .addProperty("createdAt", new Schema<String>().type("string").example("2025-08-27T06:35:12Z"));

        Schema<?> userCreateSchema = new Schema<Map<String, Object>>()
                .type("object")
                .addRequiredItem("name")
                .addRequiredItem("email")
                .addProperty("name", new Schema<String>().type("string").example("Jane Doe"))
                .addProperty("email", new Schema<String>().type("string").example("jane@example.com"));

        Content arrayJson = new Content().addMediaType(
                org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                new MediaType().schema(new Schema<>().type("array").items(userSchema)));
        Content oneJson = new Content().addMediaType(
                org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                new MediaType().schema(userSchema));
        Content createJson = new Content().addMediaType(
                org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                new MediaType().schema(userCreateSchema));

        // /users GET
        Operation listUsers = new Operation()
                .summary("List users (proxied by Spring Cloud Gateway)")
                .addTagsItem("Users")
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse().description("OK").content(arrayJson)));

        // /users POST
        Operation createUser = new Operation()
                .summary("Create user (proxied by Spring Cloud Gateway)")
                .addTagsItem("Users")
                .requestBody(new io.swagger.v3.oas.models.parameters.RequestBody()
                        .required(true)
                        .content(createJson))
                .responses(new ApiResponses()
                        .addApiResponse("201", new ApiResponse().description("Created").content(oneJson)));

        // /users/{id} GET
        Operation getUser = new Operation()
                .summary("Get user by id (proxied by Spring Cloud Gateway)")
                .addTagsItem("Users")
                .addParametersItem(new Parameter()
                        .name("id").in("path").required(true).schema(new Schema<String>().type("string")))
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse().description("OK").content(oneJson))
                        .addApiResponse("404", new ApiResponse().description("Not Found")));

        Paths paths = new Paths()
                .addPathItem("/users", new PathItem().get(listUsers).post(createUser))
                .addPathItem("/users/{id}", new PathItem().get(getUser));

        return new OpenAPI()
                .info(new Info().title("Users API (Proxied by Spring Cloud Gateway)").version("1.0.0"))
                .paths(paths);
    }
}
