package com.renesas.swtbot.assistant.agent.analysis;

/**
 * Represents a single compilation error.
 */
public class CompileError {
    private final int line;
    private final int column;
    private final String message;
    private final ErrorType type;
    private final String offendingCode;

    public enum ErrorType {
        UNDEFINED_METHOD,      // method not found
        UNDEFINED_VARIABLE,    // variable not declared
        MISSING_IMPORT,        // class not imported
        SYNTAX_ERROR,          // general syntax
        TYPE_MISMATCH,         // incompatible types
        UNKNOWN                // other
    }

    public CompileError(int line, int column, String message, ErrorType type) {
        this(line, column, message, type, null);
    }

    public CompileError(int line, int column, String message, ErrorType type, String offendingCode) {
        this.line = line;
        this.column = column;
        this.message = message;
        this.type = type;
        this.offendingCode = offendingCode;
    }

    // Getters
    public int getLine() { return line; }
    public int getColumn() { return column; }
    public String getMessage() { return message; }
    public ErrorType getType() { return type; }
    public String getOffendingCode() { return offendingCode; }

    @Override
    public String toString() {
        return String.format("Line %d: [%s] %s", line, type, message);
    }
}
