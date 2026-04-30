package com.renesas.swtbot.assistant.preferences;

import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.renesas.swtbot.assistant.Activator;

public class PreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    public PreferencePage() {
        super(GRID);
        if (Activator.getDefault() != null) {
            setPreferenceStore(Activator.getDefault().getPreferenceStore());
        }
        setDescription("SWTBot Assistant Configuration");
    }

    @Override
    public void createFieldEditors() {
        addField(new StringFieldEditor(
                PreferenceConstants.LLM_ENDPOINT,
                "LLM API Endpoint:",
                getFieldEditorParent()));

        addField(new StringFieldEditor(
                PreferenceConstants.LLM_API_KEY,
                "LLM API Key:",
                getFieldEditorParent()));

        addField(new StringFieldEditor(
                PreferenceConstants.JIRA_BASE_URL,
                "Jira Base URL:",
                getFieldEditorParent()));

        addField(new StringFieldEditor(
                PreferenceConstants.JIRA_API_TOKEN,
                "Jira API Token:",
                getFieldEditorParent()));
    }

    @Override
    public void init(IWorkbench workbench) {
    }
}
