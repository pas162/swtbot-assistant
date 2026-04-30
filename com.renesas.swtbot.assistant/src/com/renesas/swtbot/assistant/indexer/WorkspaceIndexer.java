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
}
