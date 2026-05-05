package com.renesas.swtbot.assistant.agent.analysis;

import java.util.*;

/**
 * Analysis of compilation errors to determine fix strategy.
 */
public class ErrorAnalysis {
    
    private final List<CompileError> errors;
    private final Map<CompileError.ErrorType, List<CompileError>> errorsByType;
    private final Set<String> undefinedMethods;
    private final Set<String> missingImports;
    private final String summary;

    public ErrorAnalysis(List<CompileError> errors) {
        this.errors = errors;
        this.errorsByType = categorizeErrors(errors);
        this.undefinedMethods = extractUndefinedMethods(errors);
        this.missingImports = extractMissingImports(errors);
        this.summary = generateSummary();
    }

    private Map<CompileError.ErrorType, List<CompileError>> categorizeErrors(List<CompileError> errors) {
        Map<CompileError.ErrorType, List<CompileError>> map = new HashMap<>();
        for (CompileError.ErrorType type : CompileError.ErrorType.values()) {
            map.put(type, new ArrayList<>());
        }
        for (CompileError error : errors) {
            map.get(error.getType()).add(error);
        }
        return map;
    }

    private Set<String> extractUndefinedMethods(List<CompileError> errors) {
        Set<String> methods = new HashSet<>();
        for (CompileError error : errors) {
            if (error.getType() == CompileError.ErrorType.UNDEFINED_METHOD) {
                // Extract method name from message or offending code
                String code = error.getOffendingCode();
                if (code != null) {
                    // bot.methodName() -> methodName
                    int dotIndex = code.indexOf('.');
                    int parenIndex = code.indexOf('(');
                    if (dotIndex > 0 && parenIndex > dotIndex) {
                        methods.add(code.substring(dotIndex + 1, parenIndex));
                    }
                }
            }
        }
        return methods;
    }

    private Set<String> extractMissingImports(List<CompileError> errors) {
        Set<String> imports = new HashSet<>();
        for (CompileError error : errors) {
            if (error.getType() == CompileError.ErrorType.MISSING_IMPORT) {
                // Extract class name from error message
                String msg = error.getMessage();
                // Simple extraction - can be improved
                if (msg.contains("cannot find symbol")) {
                    int idx = msg.indexOf("symbol:");
                    if (idx > 0) {
                        imports.add(msg.substring(idx + 7).trim());
                    }
                }
            }
        }
        return imports;
    }

    private String generateSummary() {
        StringBuilder sb = new StringBuilder();
        for (CompileError.ErrorType type : CompileError.ErrorType.values()) {
            int count = errorsByType.get(type).size();
            if (count > 0) {
                sb.append(count).append(" ").append(type).append(" error(s), ");
            }
        }
        return sb.length() > 0 ? sb.substring(0, sb.length() - 2) : "No errors";
    }

    // Getters
    public List<CompileError> getErrors() { return errors; }
    public List<CompileError> getErrorsByType(CompileError.ErrorType type) {
        return errorsByType.get(type);
    }
    public Set<String> getUndefinedMethods() { return undefinedMethods; }
    public Set<String> getMissingImports() { return missingImports; }
    public String getSummary() { return summary; }
    public boolean hasUndefinedMethods() { return !undefinedMethods.isEmpty(); }
    public boolean hasMissingImports() { return !missingImports.isEmpty(); }
}
