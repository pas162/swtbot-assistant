package com.renesas.swtbot.assistant.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class LlmClient {

    private final String endpoint;
    private final String apiKey;
    private final HttpClient httpClient;

    public LlmClient(String endpoint, String apiKey) {
        this.endpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public String generate(String model, String systemPrompt, String userPrompt) throws Exception {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("temperature", 0.2);
        requestBody.addProperty("max_tokens", 4096);

        JsonArray messages = new JsonArray();

        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", systemPrompt);
        messages.add(systemMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userPrompt);
        messages.add(userMsg);

        requestBody.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("LLM API error: HTTP " + response.statusCode() + " - " + response.body());
        }

        JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray choices = responseJson.getAsJsonArray("choices");

        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("LLM returned no choices");
        }

        JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        return message.get("content").getAsString();
    }

    public java.util.List<String> fetchModels() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/models"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch models: HTTP " + response.statusCode());
        }

        JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray data = responseJson.getAsJsonArray("data");

        java.util.List<String> models = new java.util.ArrayList<>();
        if (data != null) {
            for (int i = 0; i < data.size(); i++) {
                JsonObject model = data.get(i).getAsJsonObject();
                String id = model.get("id").getAsString();
                models.add(id);
            }
        }
        return models;
    }
}
