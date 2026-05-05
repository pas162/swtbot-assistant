package com.renesas.swtbot.assistant.agent.fix;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.renesas.swtbot.assistant.agent.analysis.CompileError;
import com.renesas.swtbot.assistant.agent.analysis.ErrorAnalysis;
import com.renesas.swtbot.assistant.indexer.WorkspaceIndexer;

/**
 * Fixes code errors using available helper methods and patterns.
 */
public class CodeFixer {

    private final WorkspaceIndexer indexer;
    
    // Common SWTBot typos and their fixes
    private static final Map<String, String> BOT_TYPO_FIXES = Map.ofEntries(
        Map.entry("buton", "button"),
        Map.entry("txt", "text"),
        Map.entry("meun", "menu"),
        Map.entry("buttton", "button"),
        Map.entry("lable", "label"),
        Map.entry("clabel", "cLabel"),
        Map.entry("tabel", "table"),
        Map.entry("tabels", "tables"),
        Map.entry("tre", "tree"),
        Map.entry("comb", "combo"),
        Map.entry("toolbr", "toolbar")
    );

    // Common missing imports
    private static final Map<String, String> COMMON_IMPORTS = Map.ofEntries(
        Map.entry("SWTBot", "org.eclipse.swtbot.swt.finder.SWTBot"),
        Map.entry("SWTBotButton", "org.eclipse.swtbot.swt.finder.widgets.SWTBotButton"),
        Map.entry("SWTBotText", "org.eclipse.swtbot.swt.finder.widgets.SWTBotText"),
        Map.entry("SWTBotMenu", "org.eclipse.swtbot.swt.finder.widgets.SWTBotMenu"),
        Map.entry("SWTBotTree", "org.eclipse.swtbot.swt.finder.widgets.SWTBotTree"),
        Map.entry("SWTBotTable", "org.eclipse.swtbot.swt.finder.widgets.SWTBotTable"),
        Map.entry("SWTBotShell", "org.eclipse.swtbot.swt.finder.widgets.SWTBotShell"),
        Map.entry("SWTWorkbenchBot", "org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot")
    );

    public CodeFixer(WorkspaceIndexer indexer) {
        this.indexer = indexer;
    }

    /**
     * Attempts to fix code based on error analysis.
     */
    public FixResult fix(String code, ErrorAnalysis analysis, String projectName) {
        List<String> appliedFixes = new ArrayList<>();
        String fixedCode = code;

        // Fix 1: Correct bot method typos
        if (analysis.hasUndefinedMethods()) {
            String afterTypoFix = fixBotTypos(fixedCode, appliedFixes);
            if (!afterTypoFix.equals(fixedCode)) {
                fixedCode = afterTypoFix;
            }
        }

        // Fix 2: Add missing imports
        if (analysis.hasMissingImports()) {
            String afterImportFix = fixMissingImports(fixedCode, analysis.getMissingImports(), appliedFixes);
            if (!afterImportFix.equals(fixedCode)) {
                fixedCode = afterImportFix;
            }
        }

        // Fix 3: Try to find helper methods for undefined calls
        String afterHelperFix = tryFindHelperMethods(fixedCode, analysis, projectName, appliedFixes);
        if (!afterHelperFix.equals(fixedCode)) {
            fixedCode = afterHelperFix;
        }

        // Fix 4: Fix common shell pattern issues
        String afterShellFix = fixShellPatterns(fixedCode, appliedFixes);
        if (!afterShellFix.equals(fixedCode)) {
            fixedCode = afterShellFix;
        }

        return new FixResult(code, fixedCode, appliedFixes);
    }

    /**
     * Fixes common typos in bot.method() calls.
     */
    private String fixBotTypos(String code, List<String> appliedFixes) {
        String result = code;
        Pattern pattern = Pattern.compile("bot\\.(\\w+)\\s*\\(");
        Matcher matcher = pattern.matcher(code);
        
        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;
        
        while (matcher.find()) {
            String methodName = matcher.group(1);
            String lowerName = methodName.toLowerCase();
            
            if (BOT_TYPO_FIXES.containsKey(lowerName)) {
                String correctName = BOT_TYPO_FIXES.get(lowerName);
                if (!methodName.equals(correctName)) {
                    sb.append(code, lastEnd, matcher.start(1));
                    sb.append(correctName);
                    lastEnd = matcher.end(1);
                    appliedFixes.add("Fixed typo: bot." + methodName + "() → bot." + correctName + "()");
                }
            }
        }
        
        if (lastEnd > 0) {
            sb.append(code.substring(lastEnd));
            return sb.toString();
        }
        
        return result;
    }

    /**
     * Adds missing import statements.
     */
    private String fixMissingImports(String code, Set<String> missingClasses, List<String> appliedFixes) {
        StringBuilder newImports = new StringBuilder();
        
        for (String className : missingClasses) {
            String fullImport = COMMON_IMPORTS.get(className);
            if (fullImport != null && !code.contains(fullImport)) {
                newImports.append("import ").append(fullImport).append(";\n");
                appliedFixes.add("Added import: " + fullImport);
            }
        }
        
        if (newImports.length() > 0) {
            // Find package declaration and insert after it
            int insertPos = code.indexOf("package");
            if (insertPos >= 0) {
                int packageEnd = code.indexOf(";", insertPos) + 1;
                return code.substring(0, packageEnd) + "\n" + newImports + code.substring(packageEnd);
            } else {
                // No package, insert at beginning
                return newImports + "\n" + code;
            }
        }
        
        return code;
    }

    /**
     * Tries to find and suggest helper methods from the codebase.
     */
    private String tryFindHelperMethods(String code, ErrorAnalysis analysis, 
                                        String projectName, List<String> appliedFixes) {
        String result = code;
        
        // Find undefined method calls
        Set<String> undefinedMethods = analysis.getUndefinedMethods();
        
        for (String methodName : undefinedMethods) {
            // Search in indexer for similar method
            // This is a simplified version - could be enhanced with actual method search
            if (methodName.toLowerCase().contains("ddr")) {
                // Suggest DDR helper
                appliedFixes.add("Note: Consider using DDRHelper class for DDR operations");
            } else if (methodName.toLowerCase().contains("pin")) {
                appliedFixes.add("Note: Consider using PinHelper class for pin operations");
            } else if (methodName.toLowerCase().contains("board")) {
                appliedFixes.add("Note: Consider using BoardHelper class for board operations");
            }
        }
        
        return result;
    }

    /**
     * Fixes common shell operation patterns.
     */
    private String fixShellPatterns(String code, List<String> appliedFixes) {
        // Pattern: bot.shell("Title").click() should be bot.shell("Title").bot().button().click()
        Pattern wrongShellPattern = Pattern.compile(
            "bot\\.shell\\s*\\(\"([^\"]+)\"\\)\\.click\\s*\\(\\)");
        
        Matcher matcher = wrongShellPattern.matcher(code);
        if (matcher.find()) {
            String fixed = matcher.replaceAll("bot.shell(\"$1\").activate();\n" +
                "        bot.shell(\"$1\").bot().button().click()");
            appliedFixes.add("Fixed shell pattern: added activate() and proper bot() chain");
            return fixed;
        }
        
        // Pattern: shell without activate
        Pattern shellWithoutActivate = Pattern.compile(
            "SWTBotShell\\s+(\\w+)\\s*=\\s*bot\\.shell\\s*\\([^)]+\\)\\s*;");
        
        Matcher activateMatcher = shellWithoutActivate.matcher(code);
        StringBuffer sb = new StringBuffer();
        boolean found = false;
        
        while (activateMatcher.find()) {
            String varName = activateMatcher.group(1);
            String replacement = "SWTBotShell " + varName + " = bot.shell($1).activate();";
            activateMatcher.appendReplacement(sb, replacement);
            found = true;
        }
        
        if (found) {
            activateMatcher.appendTail(sb);
            appliedFixes.add("Fixed: Added activate() to shell operations");
            return sb.toString();
        }
        
        return code;
    }
}
