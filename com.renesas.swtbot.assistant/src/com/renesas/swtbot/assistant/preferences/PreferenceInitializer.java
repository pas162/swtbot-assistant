package com.renesas.swtbot.assistant.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import com.renesas.swtbot.assistant.Activator;

public class PreferenceInitializer extends AbstractPreferenceInitializer {

    @Override
    public void initializeDefaultPreferences() {
        IPreferenceStore store = Activator.getDefault().getPreferenceStore();
        store.setDefault(PreferenceConstants.LLM_ENDPOINT, "https://api.openai.com/v1");
        store.setDefault(PreferenceConstants.LLM_API_KEY, "");
        store.setDefault(PreferenceConstants.LLM_MODEL, "gpt-4o-mini");
        store.setDefault(PreferenceConstants.JIRA_BASE_URL, "");
        store.setDefault(PreferenceConstants.JIRA_API_TOKEN, "");
    }
}
