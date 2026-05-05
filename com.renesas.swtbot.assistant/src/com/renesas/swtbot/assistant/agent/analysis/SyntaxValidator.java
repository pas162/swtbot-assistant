package com.renesas.swtbot.assistant.agent.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates Java syntax without full compilation.
 * Uses pattern matching to detect common errors.
 */
public class SyntaxValidator {

    // Patterns to detect common errors
    private static final Pattern UNDEFINED_METHOD_PATTERN = Pattern.compile(
        "(\\w+)\\s*\\([^)]*\\)\\s*;", Pattern.MULTILINE);
    
    private static final Pattern BOT_METHOD_PATTERN = Pattern.compile(
        "bot\\.(\\w+)\\s*\\(");

    private static final Pattern COMMON_BOT_METHODS = Pattern.compile(
        "bot\\.(button|text|menu|tree|table|combo|shell|canvas|styledText|toolbar|toolbarButton|cTabItem|label|CLabel|group|checkBox|radio|spinner|slider|scale|progressBar|browser|list|tabFolder)\\s*\\(");

    private static final Pattern SHELL_BOT_PATTERN = Pattern.compile(
        "bot\\.shell\\s*\\(");

    /**
     * Validates code for common SWTBot errors.
     * Note: This is a lightweight validator. Full compilation requires Eclipse JDT.
     */
    public ValidationResult validate(String code, String projectName) {
        List<CompileError> errors = new ArrayList<>();

        if (code == null || code.trim().isEmpty()) {
            errors.add(new CompileError(0, 0, "Generated code is empty", 
                CompileError.ErrorType.SYNTAX_ERROR));
            return new ValidationResult(false, errors);
        }

        // Check 1: Basic class structure
        if (!code.contains("class")) {
            errors.add(new CompileError(1, 0, "Missing class declaration", 
                CompileError.ErrorType.SYNTAX_ERROR));
        }

        // Check 2: Import statements
        checkImports(code, errors);

        // Check 3: Common bot method typos
        checkBotMethodTypos(code, errors);

        // Check 4: Shell operations (common mistake)
        checkShellOperations(code, errors);

        // Check 5: Missing assertions or waits
        checkForWaitsAndAssertions(code, errors);

        return new ValidationResult(errors.isEmpty(), errors);
    }

    private void checkImports(String code, List<CompileError> errors) {
        String[] requiredImports = {
            "org.eclipse.swtbot.swt.finder.SWTBot",
            "org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner"
        };

        for (String required : requiredImports) {
            if (!code.contains(required)) {
                // This is a warning-level issue, not critical
                // errors.add(new CompileError(0, 0, 
                //     "May be missing import: " + required, 
                //     CompileError.ErrorType.MISSING_IMPORT));
            }
        }
    }

    private void checkBotMethodTypos(String code, List<CompileError> errors) {
        // Find all bot.method() calls
        Matcher matcher = BOT_METHOD_PATTERN.matcher(code);
        
        while (matcher.find()) {
            String methodName = matcher.group(1);
            
            // Check if it's a common typo
            String[] commonTypos = {"buton", "txt", "meun", "buttton", "lable"};
            String[] corrections = {"button", "text", "menu", "button", "label"};
            
            for (int i = 0; i < commonTypos.length; i++) {
                if (methodName.equalsIgnoreCase(commonTypos[i])) {
                    errors.add(new CompileError(
                        getLineNumber(code, matcher.start()),
                        0,
                        "Possible typo: 'bot." + methodName + "()' should be 'bot." + corrections[i] + "()'",
                        CompileError.ErrorType.UNDEFINED_METHOD,
                        "bot." + methodName + "()"
                    ));
                    break;
                }
            }
        }
    }

    private void checkShellOperations(String code, List<CompileError> errors) {
        // Check if shell is used without proper activation
        if (SHELL_BOT_PATTERN.matcher(code).find()) {
            // Check for activate() call
            if (!code.contains(".activate()")) {
                errors.add(new CompileError(
                    findLineWithPattern(code, "bot.shell"),
                    0,
                    "Shell operation detected but no activate() call found. " +
                    "Consider: bot.shell(\"Title\").activate()",
                    CompileError.ErrorType.SYNTAX_ERROR
                ));
            }
        }
    }

    private void checkForWaitsAndAssertions(String code, List<CompileError> errors) {
        // Good SWTBot tests should have wait conditions or assertions
        boolean hasAssert = code.contains("assert") || code.contains("Assert");
        boolean hasWait = code.contains("wait") || code.contains("sleep") || 
                          code.contains("waitUntil");
        boolean hasTimeout = code.contains("timeout");

        if (!hasAssert && !hasWait) {
            // Warning only, not error
            // errors.add(new CompileError(0, 0, 
            //     "Code lacks assertions or wait conditions",
            //     CompileError.ErrorType.SYNTAX_ERROR));
        }
    }

    private int getLineNumber(String code, int position) {
        int line = 1;
        for (int i = 0; i < position; i++) {
            if (code.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private int findLineWithPattern(String code, String pattern) {
        int index = code.indexOf(pattern);
        return index >= 0 ? getLineNumber(code, index) : 0;
    }
}
