package com.renesas.swtbot.assistant.agent;

import java.util.ArrayList;
import java.util.List;

import com.renesas.swtbot.assistant.agent.analysis.*;
import com.renesas.swtbot.assistant.agent.fix.*;
import com.renesas.swtbot.assistant.indexer.WorkspaceIndexer;
import com.renesas.swtbot.assistant.jira.model.TicketData;
import com.renesas.swtbot.assistant.llm.LlmClient;
import com.renesas.swtbot.assistant.llm.SwtbotPromptBuilder;

/**
 * Self-healing agent that generates SWTBot tests and fixes errors automatically.
 * 
 * Workflow:
 * 1. Generate initial code from test steps
 * 2. Validate for compilation errors
 * 3. Analyze errors (missing imports, undefined methods, etc.)
 * 4. Fix using available helper methods
 * 5. Retry up to MAX_ITERATIONS
 */
public class SelfHealingAgent {

    private static final int MAX_ITERATIONS = 3;

    private final LlmClient llmClient;
    private final String model;
    private final WorkspaceIndexer indexer;
    private final SyntaxValidator validator;
    private final ErrorAnalyzer errorAnalyzer;
    private final CodeFixer codeFixer;
    private final SwtbotPromptBuilder promptBuilder;

    public SelfHealingAgent(String endpoint, String apiKey, String model, WorkspaceIndexer indexer) {
        this.llmClient = new LlmClient(endpoint, apiKey);
        this.model = model;
        this.indexer = indexer;
        this.validator = new SyntaxValidator();
        this.errorAnalyzer = new ErrorAnalyzer();
        this.codeFixer = new CodeFixer(indexer);
        this.promptBuilder = new SwtbotPromptBuilder();
    }

    /**
     * Generate and self-heal test code.
     * 
     * @param ticket Jira test case data
     * @param projectName Eclipse project name
     * @return Result with final code, fixes applied, and any remaining errors
     */
    public GenerationResult generateAndFix(TicketData ticket, String projectName, String testSourceFolder) {
        List<String> iterationLog = new ArrayList<>();
        String currentCode = null;

        // Iteration 0: Initial generation
        iterationLog.add("=== Iteration 0: Initial Generation ===");
        
        // Build prompt with examples from workspace
        List<String> keywords = promptBuilder.extractKeywords(ticket);
        List<String> examples = indexer.findRelevantExamples(null, testSourceFolder, keywords, null);
        String userPrompt = promptBuilder.buildUserPrompt(ticket, examples);
        String systemPrompt = promptBuilder.getSystemPrompt();
        
        try {
            currentCode = llmClient.generate(model, systemPrompt, userPrompt);
        } catch (Exception e) {
            iterationLog.add("✗ LLM generation failed: " + e.getMessage());
            return new GenerationResult("", iterationLog, false, null);
        }
        
        for (int i = 1; i <= MAX_ITERATIONS; i++) {
            iterationLog.add("\n=== Iteration " + i + ": Validation & Fix ===");
            
            // Validate current code
            ValidationResult validation = validator.validate(currentCode, projectName);
            
            if (validation.isValid()) {
                iterationLog.add("✓ Code is valid! No errors found.");
                return new GenerationResult(currentCode, iterationLog, true, null);
            }

            // Log errors found
            iterationLog.add("Found " + validation.getErrors().size() + " error(s):");
            for (CompileError error : validation.getErrors()) {
                iterationLog.add("  - Line " + error.getLine() + ": " + error.getMessage());
            }

            // Analyze errors
            ErrorAnalysis analysis = errorAnalyzer.analyze(validation.getErrors(), currentCode);
            iterationLog.add("Analysis: " + analysis.getSummary());

            // Try to fix
            FixResult fixResult = codeFixer.fix(currentCode, analysis, projectName);
            
            if (!fixResult.hasChanges()) {
                iterationLog.add("✗ Cannot auto-fix. Requires manual review.");
                return new GenerationResult(currentCode, iterationLog, false, validation.getErrors());
            }

            iterationLog.add("✓ Applied " + fixResult.getFixCount() + " fix(es)");
            for (String fixDescription : fixResult.getAppliedFixes()) {
                iterationLog.add("  - " + fixDescription);
            }
            
            currentCode = fixResult.getFixedCode();
        }

        // Max iterations reached, do final validation
        ValidationResult finalValidation = validator.validate(currentCode, projectName);
        if (finalValidation.isValid()) {
            return new GenerationResult(currentCode, iterationLog, true, null);
        } else {
            return new GenerationResult(currentCode, iterationLog, false, finalValidation.getErrors());
        }
    }

    /**
     * Result of the generation and healing process.
     */
    public static class GenerationResult {
        private final String finalCode;
        private final List<String> iterationLog;
        private final boolean success;
        private final List<CompileError> remainingErrors;

        public GenerationResult(String finalCode, List<String> iterationLog, 
                                boolean success, List<CompileError> remainingErrors) {
            this.finalCode = finalCode;
            this.iterationLog = iterationLog;
            this.success = success;
            this.remainingErrors = remainingErrors;
        }

        public String getFinalCode() { return finalCode; }
        public List<String> getIterationLog() { return iterationLog; }
        public boolean isSuccess() { return success; }
        public List<CompileError> getRemainingErrors() { return remainingErrors; }
        
        public String getFormattedLog() {
            return String.join("\n", iterationLog);
        }
    }
}
