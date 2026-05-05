package com.renesas.swtbot.assistant.agent.fix;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of applying fixes to code.
 */
public class FixResult {
    
    private final String originalCode;
    private final String fixedCode;
    private final List<String> appliedFixes;
    private final int fixCount;

    public FixResult(String originalCode, String fixedCode, List<String> appliedFixes) {
        this.originalCode = originalCode;
        this.fixedCode = fixedCode;
        this.appliedFixes = appliedFixes != null ? appliedFixes : Collections.emptyList();
        this.fixCount = this.appliedFixes.size();
    }

    public static FixResult noChanges(String code) {
        return new FixResult(code, code, Collections.emptyList());
    }

    // Getters
    public String getOriginalCode() { return originalCode; }
    public String getFixedCode() { return fixedCode; }
    public List<String> getAppliedFixes() { return appliedFixes; }
    public int getFixCount() { return fixCount; }
    public boolean hasChanges() { return fixCount > 0; }
}
