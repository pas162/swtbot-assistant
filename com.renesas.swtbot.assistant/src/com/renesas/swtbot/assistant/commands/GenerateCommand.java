package com.renesas.swtbot.assistant.commands;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IPreferencesService;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;

import com.renesas.swtbot.assistant.indexer.WorkspaceIndexer;
import com.renesas.swtbot.assistant.jira.model.TicketData;
import com.renesas.swtbot.assistant.llm.LlmClient;
import com.renesas.swtbot.assistant.llm.SwtbotPromptBuilder;
import com.renesas.swtbot.assistant.preferences.PreferenceConstants;
import com.renesas.swtbot.assistant.views.TicketView;

public class GenerateCommand extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (window == null) {
			return null;
		}

		// Get ticket from TicketView
		TicketData ticket = getCurrentTicket(window);
		if (ticket == null) {
			MessageDialog.openError(window.getShell(), "Error", "No ticket loaded. Please fetch a ticket first.");
			return null;
		}

		// Get LLM config from preferences
		IPreferencesService prefs = Platform.getPreferencesService();
		String llmEndpoint = prefs.getString("com.renesas.swtbot.assistant", PreferenceConstants.LLM_ENDPOINT, "",
				null);
		String llmApiKey = prefs.getString("com.renesas.swtbot.assistant", PreferenceConstants.LLM_API_KEY, "", null);

		// Get model from TicketView
		String llmModel = getSelectedModel(window);
		if (llmModel == null || llmModel.isEmpty()) {
			MessageDialog.openError(window.getShell(), "Error",
					"No model selected. Please select a model from the dropdown.");
			return null;
		}

		if (llmEndpoint.isEmpty() || llmApiKey.isEmpty()) {
			MessageDialog.openError(window.getShell(), "Error",
					"LLM not configured. Please set endpoint and API key in Preferences.");
			return null;
		}

		// Get active project
		IProject project = getActiveProject();
		if (project == null) {
			MessageDialog.openError(window.getShell(), "Error", "No active project selected.");
			return null;
		}

		// Get all UI data BEFORE starting background thread
		String commonPluginsStr = getCommonPlugins(window);
		String targetPackage = getTargetPackage(window);

		// Generate in background
		Thread genThread = new Thread(() -> {
			try {
				// 1. Find few-shot examples
				WorkspaceIndexer indexer = new WorkspaceIndexer();
				SwtbotPromptBuilder promptBuilder = new SwtbotPromptBuilder();

				java.util.List<String> keywords = promptBuilder.extractKeywords(ticket);
				
				// Parse common plugins (already fetched on UI thread)
				java.util.List<String> commonPlugins = null;
				if (commonPluginsStr != null && !commonPluginsStr.isEmpty()) {
					commonPlugins = java.util.Arrays.asList(commonPluginsStr.split(","));
				}
				
				java.util.List<String> examples = indexer.findRelevantExamples(project, "src-test", keywords, commonPlugins);

				// 2. Build prompt
				String systemPrompt = promptBuilder.getSystemPrompt();
				String userPrompt = promptBuilder.buildUserPrompt(ticket, examples);

				// 3. Call LLM
				LlmClient llmClient = new LlmClient(llmEndpoint, llmApiKey);
				String generatedCode = llmClient.generate(llmModel, systemPrompt, userPrompt);

				// 4. Save to file (targetPackage already fetched on UI thread)
				String fileName = ticket.getKey().replace("-", "_") + "Test.java";
				IFile file = saveGeneratedFile(project, targetPackage, fileName, generatedCode);

				// 5. Open in editor (UI thread)
				window.getShell().getDisplay().asyncExec(() -> {
					try {
						if (file != null) {
							IDE.openEditor(window.getActivePage(), file);
							MessageDialog.openInformation(window.getShell(), "Success", "Generated: " + fileName);
						}
					} catch (PartInitException e) {
						MessageDialog.openError(window.getShell(), "Error", "Failed to open editor: " + e.getMessage());
					}
				});

			} catch (Exception e) {
				window.getShell().getDisplay().asyncExec(() -> {
					MessageDialog.openError(window.getShell(), "Error", "Generation failed: " + e.getMessage());
				});
			}
		});
		genThread.start();

		return null;
	}

	private TicketData getCurrentTicket(IWorkbenchWindow window) {
		IWorkbenchPage page = window.getActivePage();
		if (page == null)
			return null;

		IViewPart view = page.findView("com.renesas.swtbot.assistant.views.TicketView");
		if (view instanceof TicketView) {
			return ((TicketView) view).getCurrentTicket();
		}
		return null;
	}

	private String getSelectedModel(IWorkbenchWindow window) {
		IWorkbenchPage page = window.getActivePage();
		if (page == null)
			return null;

		IViewPart view = page.findView("com.renesas.swtbot.assistant.views.TicketView");
		if (view instanceof TicketView) {
			return ((TicketView) view).getSelectedModel();
		}
		return null;
	}

	private String getTargetPackage(IWorkbenchWindow window) {
		IWorkbenchPage page = window.getActivePage();
		if (page == null)
			return "com.example.e2studio.test";

		IViewPart view = page.findView("com.renesas.swtbot.assistant.views.TicketView");
		if (view instanceof TicketView) {
			return ((TicketView) view).getTargetPackage();
		}
		return "com.example.e2studio.test";
	}

	private String getCommonPlugins(IWorkbenchWindow window) {
		IWorkbenchPage page = window.getActivePage();
		if (page == null)
			return "";

		IViewPart view = page.findView("com.renesas.swtbot.assistant.views.TicketView");
		if (view instanceof TicketView) {
			return ((TicketView) view).getCommonPlugins();
		}
		return "";
	}

	private IProject getActiveProject() {
		// Try to get from current selection in Package Explorer or Project Explorer
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (window != null && window.getSelectionService() != null) {
			org.eclipse.jface.viewers.ISelection selection = window.getSelectionService().getSelection();
			if (selection instanceof org.eclipse.jface.viewers.IStructuredSelection) {
				Object firstElement = ((org.eclipse.jface.viewers.IStructuredSelection) selection).getFirstElement();
				if (firstElement instanceof IProject) {
					return (IProject) firstElement;
				}
				if (firstElement instanceof org.eclipse.core.resources.IResource) {
					return ((org.eclipse.core.resources.IResource) firstElement).getProject();
				}
			}
		}

		// Fallback: use first project in workspace
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IProject[] projects = root.getProjects();
		for (IProject project : projects) {
			if (project.isOpen()) {
				return project;
			}
		}
		return null;
	}

	private IFile saveGeneratedFile(IProject project, String packageName, String fileName, String content) throws Exception {
		// Convert package to folder path
		String packagePath = packageName.replace(".", "/");

		// Get source folder (src or src/test/java)
		org.eclipse.core.resources.IFolder srcFolder = project.getFolder("src");
		if (!srcFolder.exists()) {
			srcFolder = project.getFolder("src/test/java");
		}
		if (!srcFolder.exists()) {
			srcFolder.create(true, true, null);
		}

		// Create package folders if needed
		org.eclipse.core.resources.IContainer parent = srcFolder;
		if (!packagePath.isEmpty()) {
			String[] segments = packagePath.split("/");
			for (String segment : segments) {
				org.eclipse.core.resources.IFolder child = ((org.eclipse.core.resources.IFolder) parent).getFolder(segment);
				if (!child.exists()) {
					child.create(true, true, null);
				}
				parent = child;
			}
		}

		// Update package declaration in content
		String updatedContent = content.replaceFirst("package\\s+[a-zA-Z_][a-zA-Z0-9_.]*;", "package " + packageName + ";");

		IFile file = ((org.eclipse.core.resources.IFolder) parent).getFile(fileName);
		ByteArrayInputStream stream = new ByteArrayInputStream(updatedContent.getBytes(StandardCharsets.UTF_8));

		if (file.exists()) {
			file.setContents(stream, true, false, null);
		} else {
			file.create(stream, true, null);
		}

		return file;
	}
}
