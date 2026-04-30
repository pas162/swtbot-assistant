package com.renesas.swtbot.assistant.jira;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.renesas.swtbot.assistant.jira.model.TicketData;
import com.renesas.swtbot.assistant.jira.model.TicketData.TestStep;

public class ZephyrClient {

    private final String baseUrl;
    private final String apiToken;
    private final HttpClient httpClient;

    public ZephyrClient(String baseUrl, String apiToken) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiToken = apiToken;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public TicketData fetchTestCase(String ticketKey) throws Exception {
        String issueUrl = baseUrl + "/rest/api/2/issue/" + ticketKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(issueUrl))
                .header("Authorization", "Bearer " + apiToken)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch ticket: HTTP " + response.statusCode() + " - " + response.body());
        }

        JsonObject issue = JsonParser.parseString(response.body()).getAsJsonObject();
        TicketData ticketData = parseIssue(issue);

        // Fetch Zephyr test steps
        List<TestStep> steps = new ArrayList<>();
        try {
            steps = fetchTestSteps(ticketData.getKey(), issue.get("id").getAsString());
        } catch (Exception e) {
            // Zephyr steps may not exist - try to extract from description
            System.out.println("Zephyr test steps not available: " + e.getMessage());
            steps = extractStepsFromDescription(ticketData.getDescription());
        }
        ticketData.setSteps(steps);

        return ticketData;
    }

    private TicketData parseIssue(JsonObject issue) {
        TicketData data = new TicketData();
        data.setKey(issue.get("key").getAsString());

        JsonObject fields = issue.getAsJsonObject("fields");
        if (fields != null) {
            if (fields.has("summary") && !fields.get("summary").isJsonNull()) {
                data.setName(fields.get("summary").getAsString());
            }
            if (fields.has("description") && !fields.get("description").isJsonNull()) {
                data.setDescription(parseDescription(fields.get("description")));
            }
            if (fields.has("customfield_10007") && !fields.get("customfield_10007").isJsonNull()) {
                data.setPrecondition(fields.get("customfield_10007").getAsString());
            }
        }

        return data;
    }

    private String parseDescription(JsonElement description) {
        if (description.isJsonPrimitive()) {
            return description.getAsString();
        }
        if (description.isJsonObject()) {
            JsonObject descObj = description.getAsJsonObject();
            if (descObj.has("content")) {
                return extractTextFromContent(descObj.getAsJsonArray("content"));
            }
        }
        return description.toString();
    }

    private String extractTextFromContent(JsonArray content) {
        StringBuilder sb = new StringBuilder();
        for (JsonElement elem : content) {
            if (elem.isJsonObject()) {
                JsonObject obj = elem.getAsJsonObject();
                if (obj.has("content")) {
                    sb.append(extractTextFromContent(obj.getAsJsonArray("content")));
                }
                if (obj.has("text")) {
                    sb.append(obj.get("text").getAsString());
                }
                if (obj.has("type") && "hardBreak".equals(obj.get("type").getAsString())) {
                    sb.append("\n");
                }
            }
        }
        return sb.toString();
    }

    private List<TestStep> fetchTestSteps(String issueKey, String issueId) throws Exception {
        // Try multiple Zephyr endpoints
        String[] possibleUrls = {
            baseUrl + "/rest/zapi/latest/teststep/" + issueId,
            baseUrl + "/rest/zephyr/latest/teststep/" + issueId,
            baseUrl + "/rest/zapi/latest/teststeps/" + issueId,
            baseUrl + "/rest/zephyr/latest/test/" + issueId + "/step"
        };

        for (String stepsUrl : possibleUrls) {
            try {
                System.out.println("Trying Zephyr endpoint: " + stepsUrl);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(stepsUrl))
                        .header("Authorization", "Bearer " + apiToken)
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println("Response code: " + response.statusCode());

                if (response.statusCode() == 200) {
                    List<TestStep> steps = parseTestSteps(response.body());
                    if (!steps.isEmpty()) {
                        return steps;
                    }
                }
            } catch (Exception e) {
                System.out.println("Failed endpoint " + stepsUrl + ": " + e.getMessage());
            }
        }

        throw new RuntimeException("No Zephyr test steps found on any endpoint");
    }

    private List<TestStep> parseTestSteps(String jsonBody) {
        List<TestStep> steps = new ArrayList<>();
        System.out.println("Parsing response: " + jsonBody.substring(0, Math.min(500, jsonBody.length())));

        JsonElement parsed = JsonParser.parseString(jsonBody);

        // Handle direct array response
        if (parsed.isJsonArray()) {
            JsonArray array = parsed.getAsJsonArray();
            return parseStepArray(array);
        }

        if (!parsed.isJsonObject()) {
            System.out.println("Response is not a JSON object or array");
            return steps;
        }

        JsonObject root = parsed.getAsJsonObject();

        // Try different JSON structures
        JsonArray stepResults = null;
        if (root.has("stepBeanCollection")) {
            stepResults = root.getAsJsonArray("stepBeanCollection");
            System.out.println("Found stepBeanCollection array");
        } else if (root.has("stepResults")) {
            stepResults = root.getAsJsonArray("stepResults");
            System.out.println("Found stepResults array");
        } else if (root.has("steps")) {
            stepResults = root.getAsJsonArray("steps");
            System.out.println("Found steps array");
        } else if (root.has("testSteps")) {
            stepResults = root.getAsJsonArray("testSteps");
            System.out.println("Found testSteps array");
        } else if (root.has("teststep")) {
            stepResults = root.getAsJsonArray("teststep");
            System.out.println("Found teststep array");
        } else if (root.has("values")) {
            stepResults = root.getAsJsonArray("values");
            System.out.println("Found values array");
        } else {
            // Print all keys in root for debugging
            System.out.println("Root keys: " + root.keySet());
        }

        if (stepResults != null) {
            return parseStepArray(stepResults);
        }

        return steps;
    }

    private List<TestStep> parseStepArray(JsonArray stepResults) {
        List<TestStep> steps = new ArrayList<>();
        System.out.println("Parsing " + stepResults.size() + " steps");

        for (int i = 0; i < stepResults.size(); i++) {
            JsonElement elem = stepResults.get(i);
            if (!elem.isJsonObject()) continue;

            JsonObject stepObj = elem.getAsJsonObject();
            String description = "";
            String expected = "";

            // Try different field names for description/action
            if (stepObj.has("description")) {
                description = getStringValue(stepObj.get("description"));
            } else if (stepObj.has("step")) {
                description = getStringValue(stepObj.get("step"));
            } else if (stepObj.has("action")) {
                description = getStringValue(stepObj.get("action"));
            } else if (stepObj.has("data")) {
                description = getStringValue(stepObj.get("data"));
            }

            // Try different field names for expected result
            if (stepObj.has("expectedResult")) {
                expected = getStringValue(stepObj.get("expectedResult"));
            } else if (stepObj.has("expected")) {
                expected = getStringValue(stepObj.get("expected"));
            } else if (stepObj.has("expectedData")) {
                expected = getStringValue(stepObj.get("expectedData"));
            } else if (stepObj.has("result")) {
                expected = getStringValue(stepObj.get("result"));
            }

            if (!description.isEmpty() || !expected.isEmpty()) {
                steps.add(new TestStep(i + 1, description, expected));
                System.out.println("Parsed step " + (i+1) + ": " + description.substring(0, Math.min(50, description.length())));
            }
        }

        return steps;
    }

    private String getStringValue(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        return element.toString();
    }

    private List<TestStep> extractStepsFromDescription(String description) {
        List<TestStep> steps = new ArrayList<>();
        if (description == null || description.isEmpty()) {
            return steps;
        }

        // Simple pattern matching for numbered steps
        String[] lines = description.split("\n");
        int stepNum = 1;
        for (String line : lines) {
            line = line.trim();
            // Look for patterns like "1.", "Step 1:", "1)" etc.
            if (line.matches("^\\d+[.):]\\s+.+")) {
                String stepText = line.replaceFirst("^\\d+[.):]\\s+", "");
                steps.add(new TestStep(stepNum++, stepText, ""));
            } else if (line.toLowerCase().startsWith("step ") && line.matches("(?i)^step\\s+\\d+[:.)].+")) {
                String stepText = line.replaceFirst("(?i)^step\\s+\\d+[:.)]\\s*", "");
                steps.add(new TestStep(stepNum++, stepText, ""));
            }
        }

        return steps;
    }

    private String encodeCredentials(String apiToken) {
        String credentials = "" + ":" + apiToken;
        return Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
