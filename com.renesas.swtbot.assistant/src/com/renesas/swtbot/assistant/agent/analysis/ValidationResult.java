package com.renesas.swtbot.assistant.agent.analysis;

import java.util.Collections;
import java.util.List;

/**
 * Result of code validation.
 */
public class ValidationResult {
    private final boolean valid;
    private final List<CompileError> errors;

    public ValidationResult(boolean valid, List<CompileError> errors) {
        this.valid = valid;
        this.errors = errors != null ? errors : Collections.emptyList();
    }

    public boolean isValid() {
        return valid;
    }

    public List<CompileError> getErrors() {
        return errors;
    }

    public int getErrorCount() {
        return errors.size();
    }
}
