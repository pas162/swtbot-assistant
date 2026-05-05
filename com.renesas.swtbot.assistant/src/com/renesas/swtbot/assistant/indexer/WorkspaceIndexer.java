package com.renesas.swtbot.assistant.indexer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

public class WorkspaceIndexer {

	private static final int MAX_EXAMPLES = 2;
	private static final int MAX_LINES_PER_FILE = 150;

	public List<String> findRelevantExamples(IProject project, String testSourceFolder, List<String> keywords) {
		return findRelevantExamples(project, testSourceFolder, keywords, null);
	}

	public List<String> findRelevantExamples(IProject project, String testSourceFolder, List<String> keywords, List<String> commonPluginProjects) {
		List<ScoredFile> candidates = new ArrayList<>();

		try {
			// Index main project
			IContainer testContainer = project.getFolder(testSourceFolder);
			if (!testContainer.exists()) {
				testContainer = project.getFolder("src/test/java");
				if (!testContainer.exists()) {
					testContainer = project.getFolder("src");
				}
			}
			if (testContainer.exists()) {
				collectJavaFiles(testContainer, candidates);
			}

			// Index common plugin projects if specified
			if (commonPluginProjects != null) {
				for (String pluginName : commonPluginProjects) {
					IProject pluginProject = project.getWorkspace().getRoot().getProject(pluginName.trim());
					if (pluginProject.exists() && pluginProject.isOpen()) {
						IContainer pluginSrc = pluginProject.getFolder("src");
						if (pluginSrc.exists()) {
							collectJavaFiles(pluginSrc, candidates);
						}
					}
				}
			}
		} catch (CoreException e) {
			return Collections.emptyList();
		}

		// Score files by keyword overlap
		Set<String> keywordSet = new HashSet<>(keywords.stream().map(String::toLowerCase).collect(Collectors.toList()));

		for (ScoredFile candidate : candidates) {
			candidate.score = calculateScore(candidate.content, keywordSet);
		}

		// Sort by score descending
		candidates.sort(Comparator.comparingInt((ScoredFile f) -> f.score).reversed());

		// Return top N files content
		return candidates.stream().limit(MAX_EXAMPLES).map(f -> f.content).collect(Collectors.toList());
	}

	private void collectJavaFiles(IContainer container, List<ScoredFile> candidates) throws CoreException {
		for (IResource resource : container.members()) {
			if (resource instanceof IContainer) {
				collectJavaFiles((IContainer) resource, candidates);
			} else if (resource instanceof IFile && resource.getName().endsWith(".java")) {
				String content = readFileContent((IFile) resource);
				if (isSwtbotTest(content)) {
					candidates.add(new ScoredFile(resource.getName(), truncate(content, MAX_LINES_PER_FILE)));
				}
			}
		}
	}

	private boolean isSwtbotTest(String content) {
		String lower = content.toLowerCase();
		return lower.contains("swtbot") || lower.contains("@runwith") || lower.contains("swtjunit4classrunner")
				|| lower.contains("bot.button") || lower.contains("bot.text") || lower.contains("bot.menu");
	}

	private int calculateScore(String content, Set<String> keywords) {
		String lower = content.toLowerCase();
		int score = 0;
		for (String keyword : keywords) {
			if (lower.contains(keyword)) {
				score++;
			}
		}
		return score;
	}

	private String readFileContent(IFile file) {
		StringBuilder sb = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getContents()))) {
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line).append("\n");
			}
		} catch (IOException | CoreException e) {
			return "";
		}
		return sb.toString();
	}

	private String truncate(String content, int maxLines) {
		String[] lines = content.split("\n");
		if (lines.length <= maxLines) {
			return content;
		}
		return String.join("\n", Arrays.copyOfRange(lines, 0, maxLines)) + "\n// ... truncated ...";
	}

	private static class ScoredFile {
		final String name;
		final String content;
		int score = 0;

		ScoredFile(String name, String content) {
			this.name = name;
			this.content = content;
		}
	}

	/**
	 * Extracts public method signatures from helper/page-object classes.
	 * Returns list of "ClassName.methodName(params)" for AI context.
	 */
	public List<String> extractHelperMethods(IProject project, String helperFolder) {
		List<String> methods = new ArrayList<>();
		
		try {
			IContainer helperContainer = project.getFolder(helperFolder);
			if (!helperContainer.exists()) {
				// Try common locations
				helperContainer = project.getFolder("src/main/java");
				if (!helperContainer.exists()) {
					helperContainer = project.getFolder("src");
				}
			}
			
			if (helperContainer.exists()) {
				collectHelperMethods(helperContainer, methods);
			}
		} catch (CoreException e) {
			// Return empty list on error
		}
		
		return methods;
	}
	
	private void collectHelperMethods(IContainer container, List<String> methods) throws CoreException {
		for (IResource resource : container.members()) {
			if (resource instanceof IContainer) {
				collectHelperMethods((IContainer) resource, methods);
			} else if (resource instanceof IFile && resource.getName().endsWith(".java")) {
				String content = readFileContent((IFile) resource);
				if (isHelperClass(content)) {
					String className = resource.getName().replace(".java", "");
					extractMethods(content, className, methods);
				}
			}
		}
	}
	
	private boolean isHelperClass(String content) {
		String lower = content.toLowerCase();
		// Helper classes typically have these characteristics
		return (lower.contains("helper") || lower.contains("util") || 
				lower.contains("page") || lower.contains("action")) &&
			   !lower.contains("@test") && // Not a test class
			   content.contains("public");   // Has public methods
	}
	
	private void extractMethods(String content, String className, List<String> methods) {
		// Simple regex to find public method signatures
		// Matches: public void methodName(params) or public Type methodName(params)
		java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
			"public\\s+(?:static\\s+)?(?:void|\\w+)\\s+(\\w+)\\s*\\([^)]*\\)");
		java.util.regex.Matcher matcher = pattern.matcher(content);
		
		while (matcher.find()) {
			String methodName = matcher.group(1);
			String signature = matcher.group(0);
			// Skip constructor
			if (!methodName.equals(className)) {
				methods.add(className + "." + methodName + "(...)");
				// Also add full signature with first line of javadoc if available
				String javadoc = extractJavadocForMethod(content, matcher.start());
				if (!javadoc.isEmpty()) {
					methods.add("  // " + javadoc);
				}
			}
		}
	}
	
	private String extractJavadocForMethod(String content, int methodPos) {
		// Find javadoc comment before this method
		int searchStart = Math.max(0, methodPos - 500);
		String before = content.substring(searchStart, methodPos);
		int javadocStart = before.lastIndexOf("/**");
		int javadocEnd = before.lastIndexOf("*/");
		
		if (javadocStart >= 0 && javadocEnd > javadocStart) {
			String javadoc = before.substring(javadocStart + 3, javadocEnd).trim();
			// Take first line only
			int lineEnd = javadoc.indexOf('\n');
			if (lineEnd > 0) {
				javadoc = javadoc.substring(0, lineEnd);
			}
			// Remove * at start
			javadoc = javadoc.replaceAll("^\\s*\\*\\s*", "").trim();
			return javadoc.length() > 60 ? javadoc.substring(0, 60) + "..." : javadoc;
		}
		return "";
	}
}
