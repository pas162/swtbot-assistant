package com.renesas.swtbot.assistant.llm;

import java.util.List;

import com.renesas.swtbot.assistant.jira.model.TicketData;

public class SwtbotPromptBuilder {

    private static final String SYSTEM_PROMPT = """
You are an expert SWTBot test automation engineer for the e² studio Eclipse IDE.

Your task is to generate complete, compilable Java SWTBot test files.

RULES:
1. Generate ONLY the Java code, no markdown, no explanations
2. Use standard SWTBot patterns: SWTWorkbenchBot, bot.button(), bot.text(), bot.menu(), etc.
3. Include proper imports, package declaration, and class structure
4. Use JUnit 4 with @RunWith(SWTBotJunit4ClassRunner.class)
5. Add wait conditions and timeouts for UI stability
6. Use meaningful variable names and clear assertions
7. Handle dialogs and popups appropriately
8. Comment each step with the corresponding test step number

OUTPUT FORMAT:
- Package declaration first
- All necessary imports
- Class with @RunWith annotation
- Setup method with @Before
- Test methods with @Test
- One test method per test case scenario
""";

    public String buildUserPrompt(TicketData ticket, List<String> exampleFiles) {
        StringBuilder sb = new StringBuilder();

        sb.append("Generate a SWTBot Java test for the following Zephyr test case:\n\n");

        // Ticket info
        sb.append("TEST CASE: ").append(ticket.getKey()).append("\n");
        sb.append("NAME: ").append(ticket.getName()).append("\n");
        if (ticket.getDescription() != null) {
            sb.append("DESCRIPTION: ").append(ticket.getDescription()).append("\n");
        }
        if (ticket.getPrecondition() != null) {
            sb.append("PRECONDITION: ").append(ticket.getPrecondition()).append("\n");
        }

        // Test steps
        if (ticket.getSteps() != null && !ticket.getSteps().isEmpty()) {
            sb.append("\nTEST STEPS:\n");
            for (TicketData.TestStep step : ticket.getSteps()) {
                sb.append(step.getIndex()).append(". ")
                  .append(step.getDescription()).append("\n");
                if (step.getExpectedResult() != null && !step.getExpectedResult().isEmpty()) {
                    sb.append("   Expected: ").append(step.getExpectedResult()).append("\n");
                }
            }
        }

        // Few-shot examples
        if (!exampleFiles.isEmpty()) {
            sb.append("\n\nREFERENCE EXAMPLES (match the coding style):\n");
            sb.append("=".repeat(50)).append("\n");
            for (int i = 0; i < exampleFiles.size(); i++) {
                sb.append("// Example ").append(i + 1).append(":\n");
                sb.append(exampleFiles.get(i));
                sb.append("\n").append("=".repeat(50)).append("\n");
            }
        }

        sb.append("\n\nGenerate the complete Java test file now.");

        return sb.toString();
    }

    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    public List<String> extractKeywords(TicketData ticket) {
        List<String> keywords = new java.util.ArrayList<>();

        // Add words from name and description
        addWords(keywords, ticket.getName());
        addWords(keywords, ticket.getDescription());

        // Add step descriptions
        if (ticket.getSteps() != null) {
            for (TicketData.TestStep step : ticket.getSteps()) {
                addWords(keywords, step.getDescription());
            }
        }

        return keywords;
    }

    private void addWords(List<String> keywords, String text) {
        if (text == null || text.isEmpty()) return;
        // Split and filter meaningful words
        String[] words = text.toLowerCase().split("[^a-zA-Z0-9]");
        for (String word : words) {
            if (word.length() > 3) {
                keywords.add(word);
            }
        }
    }
}
