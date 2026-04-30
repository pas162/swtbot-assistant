package com.renesas.swtbot.assistant.views;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IPreferencesService;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

import com.renesas.swtbot.assistant.Activator;
import com.renesas.swtbot.assistant.jira.ZephyrClient;
import com.renesas.swtbot.assistant.jira.model.TicketData;
import com.renesas.swtbot.assistant.preferences.PreferenceConstants;

public class TicketView extends ViewPart {

	public static final String ID = "com.renesas.swtbot.assistant.views.TicketView";

	private Text ticketKeyText;
	private Button fetchButton;
	private Label titleLabel;
	private Label statusLabel;
	private Table stepsTable;
	private Combo modelCombo;
	private Text packageText;
	private Text commonPluginsText;
	private TicketData currentTicket;

	@Override
	public void createPartControl(Composite parent) {
		// Create a form-like container with better margins
		ScrolledComposite scrolled = new ScrolledComposite(parent, SWT.V_SCROLL | SWT.H_SCROLL);
		scrolled.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		scrolled.setExpandHorizontal(true);
		scrolled.setExpandVertical(true);

		Composite formComposite = new Composite(scrolled, SWT.NONE);
		GridLayout formLayout = new GridLayout(1, false);
		formLayout.marginWidth = 15;
		formLayout.marginHeight = 15;
		formLayout.verticalSpacing = 12;
		formComposite.setLayout(formLayout);
		formComposite.setBackground(parent.getDisplay().getSystemColor(SWT.COLOR_WHITE));
		formComposite.setBackgroundMode(SWT.INHERIT_FORCE);

		// Header section
		createHeaderSection(formComposite);

		// Configuration section (collapsible)
		createConfigurationSection(formComposite);

		// Info section with modern styling
		createInfoSection(formComposite);

		// Test Steps section
		createStepsSection(formComposite);

		scrolled.setContent(formComposite);
		scrolled.setMinSize(formComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT));

		refreshModels();
	}

	private void createHeaderSection(Composite parent) {
		Composite header = new Composite(parent, SWT.NONE);
		GridData headerData = new GridData(SWT.FILL, SWT.TOP, true, false);
		header.setLayoutData(headerData);
		GridLayout headerLayout = new GridLayout(1, false);
		headerLayout.marginBottom = 5;
		header.setLayout(headerLayout);

		Label title = new Label(header, SWT.NONE);
		title.setText("SWTBot Test Generator");
		title.setFont(new org.eclipse.swt.graphics.Font(getSite().getShell().getDisplay(), "Segoe UI", 14, SWT.BOLD));
		title.setForeground(getSite().getShell().getDisplay().getSystemColor(SWT.COLOR_DARK_BLUE));

		Label subtitle = new Label(header, SWT.NONE);
		subtitle.setText("Generate automated SWTBot tests from Zephyr tickets");
		subtitle.setFont(
				new org.eclipse.swt.graphics.Font(getSite().getShell().getDisplay(), "Segoe UI", 9, SWT.NORMAL));
		subtitle.setForeground(getSite().getShell().getDisplay().getSystemColor(SWT.COLOR_GRAY));
	}

	private void createConfigurationSection(Composite parent) {
		// Main configuration group
		org.eclipse.swt.widgets.Group configGroup = new org.eclipse.swt.widgets.Group(parent, SWT.NONE);
		configGroup.setText("Configuration");
		configGroup.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		GridLayout configLayout = new GridLayout(1, false);
		configLayout.marginWidth = 10;
		configLayout.marginHeight = 10;
		configLayout.verticalSpacing = 8;
		configGroup.setLayout(configLayout);

		// Ticket Key with modern styling
		createTicketKeyRow(configGroup);

		// Model selection
		createModelRow(configGroup);

		// Package selection
		createPackageRow(configGroup);

		// Common Plugins (expandable)
		createExpandablePluginsSection(configGroup);
	}

	private void createTicketKeyRow(Composite parent) {
		Composite row = new Composite(parent, SWT.NONE);
		row.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		GridLayout layout = new GridLayout(3, false);
		layout.marginWidth = 0;
		layout.horizontalSpacing = 8;
		row.setLayout(layout);

		Label label = createStyledLabel(row, "Ticket Key:", true);
		label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

		ticketKeyText = new Text(row, SWT.BORDER | SWT.SINGLE);
		ticketKeyText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		ticketKeyText.setText("TEST-123");
		ticketKeyText.setToolTipText("Enter Zephyr ticket key (e.g., TEST-123)");

		fetchButton = new Button(row, SWT.PUSH);
		fetchButton.setText("Fetch");
		fetchButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		fetchButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				fetchTicket();
			}
		});
	}

	private void createModelRow(Composite parent) {
		Composite row = new Composite(parent, SWT.NONE);
		row.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		GridLayout layout = new GridLayout(2, false);
		layout.marginWidth = 0;
		layout.horizontalSpacing = 8;
		row.setLayout(layout);

		Label label = createStyledLabel(row, "LLM Model:", true);
		label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

		modelCombo = new Combo(row, SWT.READ_ONLY | SWT.DROP_DOWN);
		modelCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		modelCombo.setText("Select model...");
		modelCombo.setToolTipText("Select the LLM model for test generation");
	}

	private void createPackageRow(Composite parent) {
		Composite row = new Composite(parent, SWT.NONE);
		row.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		GridLayout layout = new GridLayout(3, false);
		layout.marginWidth = 0;
		layout.horizontalSpacing = 8;
		row.setLayout(layout);

		Label label = createStyledLabel(row, "Target Package:", true);
		label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

		packageText = new Text(row, SWT.BORDER | SWT.SINGLE);
		packageText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		packageText.setText("com.example.e2studio.test");
		packageText.setToolTipText("Package where the generated test class will be saved");

		Button browseButton = new Button(row, SWT.PUSH);
		browseButton.setText("Browse...");
		browseButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		browseButton.setToolTipText("Browse for existing packages in workspace");
		browseButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				browseForPackage();
			}
		});
	}

	private void createExpandablePluginsSection(Composite parent) {
		// Create a composite that looks like an expandable section
		Composite expandableComposite = new Composite(parent, SWT.NONE);
		expandableComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		GridLayout expandableLayout = new GridLayout(1, false);
		expandableLayout.marginWidth = 0;
		expandableLayout.marginTop = 5;
		expandableComposite.setLayout(expandableLayout);

		// Toggle button with arrow
		Composite toggleRow = new Composite(expandableComposite, SWT.NONE);
		toggleRow.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		GridLayout toggleLayout = new GridLayout(2, false);
		toggleLayout.marginWidth = 0;
		toggleLayout.horizontalSpacing = 5;
		toggleRow.setLayout(toggleLayout);

		final Button toggleButton = new Button(toggleRow, SWT.TOGGLE | SWT.FLAT);
		toggleButton.setText("  Advanced Options  ");
		toggleButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

		// Plugins content (initially hidden)
		final Composite pluginsContent = new Composite(expandableComposite, SWT.NONE);
		pluginsContent.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		GridLayout pluginsLayout = new GridLayout(2, false);
		pluginsLayout.marginWidth = 15;
		pluginsLayout.marginTop = 5;
		pluginsLayout.horizontalSpacing = 8;
		pluginsContent.setLayout(pluginsLayout);
		pluginsContent.setVisible(false);

		Label pluginsLabel = createStyledLabel(pluginsContent, "Common Plugins:", false);
		pluginsLabel.setToolTipText("Comma-separated project names containing common utility classes");

		commonPluginsText = new Text(pluginsContent, SWT.BORDER | SWT.SINGLE);
		commonPluginsText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		commonPluginsText.setToolTipText("e.g., com.renesas.swtbot.common, com.renesas.utils");

		// Help text
		Label helpLabel = new Label(pluginsContent, SWT.WRAP);
		helpLabel.setText("These projects will be indexed to provide few-shot examples for AI.");
		helpLabel.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
		helpLabel.setFont(
				new org.eclipse.swt.graphics.Font(getSite().getShell().getDisplay(), "Segoe UI", 8, SWT.ITALIC));
		GridData helpData = new GridData(SWT.FILL, SWT.TOP, true, false);
		helpData.horizontalSpan = 2;
		helpLabel.setLayoutData(helpData);

		toggleButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				boolean expanded = toggleButton.getSelection();
				pluginsContent.setVisible(expanded);
				parent.layout(true, true);
			}
		});
	}

	private Label createStyledLabel(Composite parent, String text, boolean bold) {
		Label label = new Label(parent, SWT.NONE);
		label.setText(text);
		if (bold) {
			label.setFont(
					new org.eclipse.swt.graphics.Font(getSite().getShell().getDisplay(), "Segoe UI", 9, SWT.BOLD));
		}
		return label;
	}

	private void createInfoSection(Composite parent) {
		// Status/Info group
		org.eclipse.swt.widgets.Group infoGroup = new org.eclipse.swt.widgets.Group(parent, SWT.NONE);
		infoGroup.setText("Ticket Information");
		infoGroup.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		GridLayout infoLayout = new GridLayout(1, false);
		infoLayout.marginWidth = 10;
		infoLayout.marginHeight = 10;
		infoGroup.setLayout(infoLayout);

		titleLabel = new Label(infoGroup, SWT.WRAP);
		titleLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		titleLabel.setText("Enter a ticket key and click Fetch to load test details");
		titleLabel.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));

		// Status label with icon indicator
		statusLabel = new Label(infoGroup, SWT.NONE);
		statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		statusLabel.setText("");
		statusLabel.setFont(
				new org.eclipse.swt.graphics.Font(getSite().getShell().getDisplay(), "Segoe UI", 8, SWT.NORMAL));
	}

	private void createStepsSection(Composite parent) {
		// Test Steps group
		org.eclipse.swt.widgets.Group stepsGroup = new org.eclipse.swt.widgets.Group(parent, SWT.NONE);
		stepsGroup.setText("Test Steps");
		stepsGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		GridLayout stepsLayout = new GridLayout(1, false);
		stepsLayout.marginWidth = 10;
		stepsLayout.marginHeight = 10;
		stepsGroup.setLayout(stepsLayout);

		// Create table with 2 columns
		stepsTable = new Table(stepsGroup, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.FULL_SELECTION);
		stepsTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		stepsTable.setHeaderVisible(true);
		stepsTable.setLinesVisible(true);

		// Step column
		TableColumn stepColumn = new TableColumn(stepsTable, SWT.NONE);
		stepColumn.setText("Step");
		stepColumn.setWidth(400);

		// Expected Result column
		TableColumn expectedColumn = new TableColumn(stepsTable, SWT.NONE);
		expectedColumn.setText("Expected Result");
		expectedColumn.setWidth(400);
	}

	private void fetchTicket() {
		String ticketKey = ticketKeyText.getText().trim();
		if (ticketKey.isEmpty()) {
			MessageDialog.openError(getSite().getShell(), "Error", "Please enter a ticket key");
			return;
		}

		if (Activator.getDefault() == null) {
			MessageDialog.openError(getSite().getShell(), "Error",
					"Plugin not initialized. Please check target platform configuration.");
			return;
		}

		String jiraUrl = Activator.getDefault().getPreferenceStore().getString(PreferenceConstants.JIRA_BASE_URL);
		String apiToken = Activator.getDefault().getPreferenceStore().getString(PreferenceConstants.JIRA_API_TOKEN);

		if (jiraUrl.isEmpty()) {
			MessageDialog.openInformation(getSite().getShell(), "Info",
					"Jira not configured. Please set Jira URL in Preferences.");
			return;
		}

		fetchButton.setEnabled(false);
		fetchButton.setText("Fetching...");

		// Run in background thread
		Thread fetchThread = new Thread(() -> {
			try {
				ZephyrClient client = new ZephyrClient(jiraUrl, apiToken);
				TicketData ticket = client.fetchTestCase(ticketKey);

				getSite().getShell().getDisplay().asyncExec(() -> {
					displayTicket(ticket, true);
					fetchButton.setEnabled(true);
					fetchButton.setText("Fetch");
				});
			} catch (Exception ex) {
				getSite().getShell().getDisplay().asyncExec(() -> {
					MessageDialog.openError(getSite().getShell(), "Error",
							"Failed to fetch ticket: " + ex.getMessage());
					fetchButton.setEnabled(true);
					fetchButton.setText("Fetch");
				});
			}
		});
		fetchThread.start();
	}

	private void displayTicket(TicketData ticket, boolean success) {
		this.currentTicket = ticket;

		titleLabel.setText(ticket.getKey() + ": " + (ticket.getName() != null ? ticket.getName() : "No title"));

		// Update status indicator
		if (success) {
			statusLabel.setText("✓ Fetched successfully");
			statusLabel.setForeground(stepsTable.getDisplay().getSystemColor(SWT.COLOR_DARK_GREEN));
		} else {
			statusLabel.setText("");
		}

		stepsTable.removeAll();

		if (ticket.getSteps() != null && !ticket.getSteps().isEmpty()) {
			for (TicketData.TestStep step : ticket.getSteps()) {
				TableItem item = new TableItem(stepsTable, SWT.NONE);
				item.setText(0, step.getIndex() + ". " + step.getDescription());
				item.setText(1, step.getExpectedResult() != null ? step.getExpectedResult() : "");
			}
		} else {
			TableItem item = new TableItem(stepsTable, SWT.NONE);
			item.setText(0, "No test steps available");
			item.setText(1, "");
		}
	}

	public TicketData getCurrentTicket() {
		return currentTicket;
	}

	public String getSelectedModel() {
		if (modelCombo != null && modelCombo.getSelectionIndex() >= 0) {
			return modelCombo.getText();
		}
		return null;
	}

	public String getTargetPackage() {
		if (packageText != null) {
			return packageText.getText().trim();
		}
		return "com.example.e2studio.test";
	}

	public String getCommonPlugins() {
		if (commonPluginsText != null) {
			return commonPluginsText.getText().trim();
		}
		return "";
	}

	private void browseForPackage() {
		org.eclipse.jface.window.Window dialog = createPackageSelectionDialog();
		if (dialog.open() == org.eclipse.jface.window.Window.OK) {
			String selectedPackage = getSelectedPackageFromDialog(dialog);
			if (selectedPackage != null) {
				packageText.setText(selectedPackage);
			}
		}
	}

	private org.eclipse.jface.window.Window createPackageSelectionDialog() {
		return new PackageSelectionDialog(getSite().getShell());
	}

	private String getSelectedPackageFromDialog(org.eclipse.jface.window.Window dialog) {
		if (dialog instanceof PackageSelectionDialog) {
			return ((PackageSelectionDialog) dialog).getSelectedPackage();
		}
		return null;
	}

	private void refreshModels() {
		Thread modelThread = new Thread(() -> {
			try {
				IPreferencesService prefs = Platform.getPreferencesService();
				String endpoint = prefs.getString("com.renesas.swtbot.assistant", PreferenceConstants.LLM_ENDPOINT, "",
						null);
				String apiKey = prefs.getString("com.renesas.swtbot.assistant", PreferenceConstants.LLM_API_KEY, "",
						null);

				if (endpoint.isEmpty() || apiKey.isEmpty()) {
					return;
				}

				com.renesas.swtbot.assistant.llm.LlmClient client = new com.renesas.swtbot.assistant.llm.LlmClient(
						endpoint, apiKey);
				java.util.List<String> models = client.fetchModels();

				getSite().getShell().getDisplay().asyncExec(() -> {
					modelCombo.removeAll();
					for (String model : models) {
						modelCombo.add(model);
					}
					if (modelCombo.getItemCount() > 0) {
						modelCombo.select(0);
					}
				});
			} catch (Exception e) {
				// Silently fail - models won't be populated
			}
		});
		modelThread.start();
	}

	@Override
	public void setFocus() {
		ticketKeyText.setFocus();
	}
}
