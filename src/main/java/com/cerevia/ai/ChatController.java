package com.cerevia.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ChatController() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        this.objectMapper = new ObjectMapper();
    }

    @PostMapping
    public Map<String, String> chat(
            @RequestBody Map<String, String> request) {

        String message = request.get("message");

        if (message == null || message.isBlank()) {
            return Map.of("reply", "Please enter a message.");
        }

        String apiKey = System.getenv("OPENAI_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            return Map.of(
                    "reply",
                    "API key is missing. Please restart the server with OPENAI_API_KEY set."
            );
        }

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", "gpt-5-mini",
                    "instructions",
                    "You are Cerevia AI, a helpful and friendly AI assistant. "
                            + "Answer clearly, accurately, and naturally. "
                            + "Respond in the language used by the user.",
                    "input", message
            );

            String jsonBody =
                    objectMapper.writeValueAsString(requestBody);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(
                            "https://api.openai.com/v1/responses"))
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString()
            );

            JsonNode responseJson =
                    objectMapper.readTree(response.body());

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {

                String errorMessage = responseJson
                        .path("error")
                        .path("message")
                        .asText("Unknown API error");

                System.err.println(
                        "OpenAI API error (HTTP "
                                + response.statusCode()
                                + "): " + errorMessage
                );

                return Map.of(
                        "reply",
                        "The AI service returned an error (HTTP "
                                + response.statusCode()
                                + "). Please check your API key, billing, "
                                + "and model access."
                );
            }

            StringBuilder answer = new StringBuilder();

            JsonNode output = responseJson.path("output");

            for (JsonNode item : output) {
                if ("message".equals(
                        item.path("type").asText())) {

                    for (JsonNode content : item.path("content")) {
                        if ("output_text".equals(
                                content.path("type").asText())) {

                            answer.append(
                                    content.path("text").asText()
                            );
                        }
                    }
                }
            }

            if (answer.isEmpty()) {
                return Map.of(
                        "reply",
                        "The AI returned no text. Please try again."
                );
            }

            return Map.of("reply", answer.toString());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            return Map.of(
                    "reply",
                    "The request was interrupted. Please try again."
            );

        } catch (IOException e) {
            System.err.println(
                    "OpenAI connection error: " + e.getMessage()
            );

            return Map.of(
                    "reply",
                    "Could not connect to the AI service. "
                            + "Please check your internet connection."
            );
        }
    }
}