package net.arctics.clonk.ui;

import net.arctics.clonk.ClonkCore;

import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;

public class ClonkPerspective implements IPerspectiveFactory {

	public void createInitialLayout(IPageLayout layout) {
//		layout.addFastView("org.eclipse.ui.navigator.ProjectExplorer", (float) 0.2);
//		layout.addView("org.eclipse.ui.navigator.ProjectExplorer", IPageLayout.RIGHT, IPageLayout.DEFAULT_VIEW_RATIO, "navigator");
		
		layout.addActionSet(ClonkCore.id("ui.actionset"));
        layout.addActionSet(IDebugUIConstants.LAUNCH_ACTION_SET);
		
		layout.addShowViewShortcut(ClonkCore.id("views.EngineIdentifiersView"));
		
		// Get the editor area.
		 String editorArea = layout.getEditorArea();

		 // Top left: Resource Navigator view and Bookmarks view placeholder
		 IFolderLayout topLeft = layout.createFolder("topLeft", IPageLayout.LEFT, 0.25f,
		    editorArea);
		 topLeft.addView("org.eclipse.ui.navigator.ProjectExplorer");
		 topLeft.addPlaceholder(IPageLayout.ID_BOOKMARKS);

		 // Bottom left: Outline view and Property Sheet view
		 IFolderLayout bottomLeft = layout.createFolder("bottomLeft", IPageLayout.BOTTOM, 0.50f,
		 	   "topLeft");
		 bottomLeft.addView(IPageLayout.ID_OUTLINE);
		 bottomLeft.addView(IPageLayout.ID_PROP_SHEET);

		 // Bottom right: Task List view
		 layout.addView(IPageLayout.ID_TASK_LIST, IPageLayout.BOTTOM, 0.66f, editorArea);

		 layout.addNewWizardShortcut(ClonkCore.id("wizards.NewC4Object"));
		 layout.addNewWizardShortcut(ClonkCore.id("wizards.NewClonkProject"));
		 layout.addNewWizardShortcut(ClonkCore.id("wizards.NewScenario"));
		 layout.addNewWizardShortcut(ClonkCore.id("wizards.NewParticle"));
//		 layout.addActionSet("Clonk.actionSet2");
	}

}
