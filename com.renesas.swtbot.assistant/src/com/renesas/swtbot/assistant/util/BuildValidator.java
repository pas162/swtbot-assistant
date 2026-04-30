package com.renesas.swtbot.assistant.util;

/**
 * Validates build configuration at runtime.
 * 
 * This class can be called to diagnose common build issues:
 * 1. Missing target platform
 * 2. Missing dependencies
 * 3. Class loading issues
 * 
 * Usage: Call printDiagnostics() from anywhere to see what's working
 */
public class BuildValidator {

    public static void printDiagnostics() {
        System.out.println("=== AI SWTBot Assistant - Build Diagnostics ===\n");
        
        // Check Java version
        System.out.println("Java Version: " + System.getProperty("java.version"));
        System.out.println("Java Home: " + System.getProperty("java.home"));
        
        // Check Eclipse dependencies
        checkClass("org.eclipse.ui.part.ViewPart", "Eclipse UI");
        checkClass("org.eclipse.swt.widgets.Table", "SWT");
        checkClass("org.eclipse.jface.dialogs.MessageDialog", "JFace");
        checkClass("org.eclipse.core.runtime.IProgressMonitor", "Core Runtime");
        checkClass("org.eclipse.jdt.core.IJavaProject", "JDT Core");
        
        // Check external libraries
        checkClass("com.google.gson.Gson", "Gson");
        checkClass("com.google.gson.JsonObject", "Gson Json");
        
        // Check HTTP client (Java 11+)
        checkClass("java.net.http.HttpClient", "Java HTTP Client");
        
        System.out.println("\n=== End Diagnostics ===");
    }
    
    private static void checkClass(String className, String description) {
        try {
            Class.forName(className);
            System.out.println("✓ " + description + " - OK");
        } catch (ClassNotFoundException e) {
            System.out.println("✗ " + description + " - MISSING (" + className + ")");
        } catch (NoClassDefFoundError e) {
            System.out.println("✗ " + description + " - ERROR (" + e.getMessage() + ")");
        }
    }
    
    /**
     * Can be called from Activator to verify at startup
     */
    public static boolean isBuildValid() {
        try {
            Class.forName("org.eclipse.ui.part.ViewPart");
            Class.forName("com.google.gson.Gson");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
