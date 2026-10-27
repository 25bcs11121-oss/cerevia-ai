package com.cerevia.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
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

        String apiKey = System.getenv("GEMINI_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            return Map.of(
                    "reply",
                    "Gemini API key is missing. Please check server configuration."
            );
        }

        try {
            String instructions =
                    "You are Cerevia AI, a helpful, intelligent, "
                    + "and friendly AI assistant. "
                    + "Answer clearly, accurately, and naturally. "
                    + "Respond in the language used by the user. "
                    + "For coding questions, provide correct code "
                    + "and explain it when useful.";

            Map<String, Object> requestBody = Map.of(
                    "systemInstruction",
                    Map.of(
                            "parts",
                            List.of(
                                    Map.of("text", instructions)
                            )
                    ),
                    "contents",
                    List.of(
                            Map.of(
                                    "parts",
                                    List.of(
                                            Map.of("text", message)
                                    )
                            )
                    )
            );

            String jsonBody =
                    objectMapper.writeValueAsString(requestBody);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(
                            "https://generativelanguage.googleapis.com/"
                            + "v1beta/models/gemini-3.8-flash:generateContent"))
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
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
                        .asText("Unknown Gemini API error");

                System.err.println(
                        "Gemini API error (HTTP "
                        + response.statusCode()
                        + "): " + errorMessage
                );

                return Map.of(
                        "reply",
                        "The AI service returned an error (HTTP "
                        + response.statusCode()
                        + "). Please check the server logs."
                );
            }

            StringBuilder answer = new StringBuilder();

            JsonNode parts = responseJson
                    .path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts");

            for (JsonNode part : parts) {
                String text = part.path("text").asText("");

                if (!text.isBlank()) {
                    if (!answer.isEmpty()) {
                        answer.append("\n");
                    }

                    answer.append(text);
                }
            }

            if (answer.isEmpty()) {
                System.err.println(
                        "Gemini returned no text: " + response.body()
                );

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
                    "Gemini connection error: " + e.getMessage()
            );

            return Map.of(
                    "reply",
                    "Could not connect to the AI service. Please try again."
            );
        }
    }
}