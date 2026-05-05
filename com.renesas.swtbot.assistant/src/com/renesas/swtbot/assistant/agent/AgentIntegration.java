package com.renesas.swtbot.assistant.agent;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;

import com.renesas.swtbot.assistant.Activator;
import com.renesas.swtbot.assistant.agent.SelfHealingAgent.GenerationResult;
import com.renesas.swtbot.assistant.indexer.WorkspaceIndexer;
import com.renesas.swtbot.assistant.jira.model.TicketData;
import com.renesas.swtbot.assistant.preferences.PreferenceConstants;

/**
 * Integration point for the SelfHealingAgent into the existing workflow.
 * 
 * Usage example in GenerateCommand or TicketView:
 * <pre>
 * AgentIntegration integration = new AgentIntegration();
 * String finalCode = integration.generateTestWithHealing(ticket, project, monitor);
 * </pre>
 */
public class AgentIntegration {

    private final SelfHealingAgent agent;

    private final String endpoint;
    private final String apiKey;
    private final String model;

    public AgentIntegration() {
        // Get settings from preferences
        this.endpoint = Activator.getDefault().getPreferenceStore()
            .getString(PreferenceConstants.LLM_ENDPOINT);
        this.apiKey = Activator.getDefault().getPreferenceStore()
            .getString(PreferenceConstants.LLM_API_KEY);
        this.model = Activator.getDefault().getPreferenceStore()
            .getString(PreferenceConstants.LLM_MODEL);
        
        WorkspaceIndexer indexer = new WorkspaceIndexer();
        this.agent = new SelfHealingAgent(endpoint, apiKey, model, indexer);
    }

    /**
     * Generates SWTBot test code with self-healing capabilities.
     * 
     * @param ticket Jira test case data
     * @param project Eclipse project
     * @param monitor Progress monitor (can be null)
     * @return The final generated and fixed code
     */
    public String generateTestWithHealing(TicketData ticket, IProject project, IProgressMonitor monitor) {
        if (monitor != null) {
            monitor.beginTask("Generating test with self-healing...", 3);
        }

        try {
            // Run the self-healing agent
            String testSourceFolder = "src/test/java"; // Could be configurable
            GenerationResult result = agent.generateAndFix(ticket, project.getName(), testSourceFolder);

            // Log the healing process
            System.out.println("=== Self-Healing Agent Log ===");
            System.out.println(result.getFormattedLog());
            System.out.println("==============================");

            if (result.isSuccess()) {
                if (monitor != null) {
                    monitor.done();
                }
                return result.getFinalCode();
            } else {
                // Return code with warning comments
                StringBuilder sb = new StringBuilder();
                sb.append("// WARNING: Self-healing completed but some issues remain\n");
                sb.append("// Please review the following code:\n\n");
                sb.append(result.getFinalCode());
                
                if (monitor != null) {
                    monitor.done();
                }
                return sb.toString();
            }
        } catch (Exception e) {
            if (monitor != null) {
                monitor.done();
            }
            // Fallback: return original without healing
            System.err.println("Self-healing failed: " + e.getMessage());
            return "// Error: " + e.getMessage();
        }
    }

    /**
     * Gets the healing log from the last generation.
     * Useful for showing to the user what fixes were applied.
     */
    public String getLastHealingLog() {
        // The agent doesn't store state, so this would need to be enhanced
        // to return the log from the last call
        return "Healing log not available in this version. Check console output.";
    }
}
