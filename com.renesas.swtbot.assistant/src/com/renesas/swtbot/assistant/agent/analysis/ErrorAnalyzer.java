package com.renesas.swtbot.assistant.agent.analysis;

import java.util.List;

/**
 * Analyzes compilation errors to determine the best fix strategy.
 */
public class ErrorAnalyzer {

    /**
     * Analyzes errors and returns structured analysis.
     */
    public ErrorAnalysis analyze(List<CompileError> errors, String code) {
        // Enhance errors with context from the code
        for (CompileError error : errors) {
            if (error.getOffendingCode() == null && error.getLine() > 0) {
                String lineContent = extractLine(code, error.getLine());
                // Use reflection to set the offending code (simplified)
                error = new CompileError(
                    error.getLine(),
                    error.getColumn(),
                    error.getMessage(),
                    error.getType(),
                    lineContent
                );
            }
        }
        
        return new ErrorAnalysis(errors);
    }

    private String extractLine(String code, int lineNumber) {
        String[] lines = code.split("\n");
        if (lineNumber > 0 && lineNumber <= lines.length) {
            return lines[lineNumber - 1].trim();
        }
        return "";
    }
}
