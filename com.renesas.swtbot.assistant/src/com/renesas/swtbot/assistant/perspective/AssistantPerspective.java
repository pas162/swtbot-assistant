package com.renesas.swtbot.assistant.perspective;

import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;

public class AssistantPerspective implements IPerspectiveFactory {

    public static final String ID = "com.renesas.swtbot.assistant.perspective";

    @Override
    public void createInitialLayout(IPageLayout layout) {
        layout.setEditorAreaVisible(true);

        IFolderLayout left = layout.createFolder("left", IPageLayout.LEFT, 0.25f, layout.getEditorArea());
        left.addView("com.renesas.swtbot.assistant.views.TicketView");
        left.addView(IPageLayout.ID_PROJECT_EXPLORER);

        layout.addView(IPageLayout.ID_OUTLINE, IPageLayout.RIGHT, 0.75f, layout.getEditorArea());
        layout.addView(IPageLayout.ID_PROBLEM_VIEW, IPageLayout.BOTTOM, 0.75f, layout.getEditorArea());
    }
}
