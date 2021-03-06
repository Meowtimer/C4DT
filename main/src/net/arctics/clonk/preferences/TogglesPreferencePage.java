package net.arctics.clonk.preferences;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.navigator.CommonNavigator;

import net.arctics.clonk.Core;
import net.arctics.clonk.builder.ClonkProjectNature;
import net.arctics.clonk.util.UI;

public class TogglesPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	@Override
	protected void createFieldEditors() {
		// toggles
		final String[] toggles = {
			ClonkPreferences.SHOW_EXPORT_LOG, Messages.ShowExportLog,
			ClonkPreferences.SHOW_ERRORS_WHILE_TYPING, Messages.ClonkPreferencePage_ShowErrorsWhileTyping,
			ClonkPreferences.OPEN_EXTERNAL_BROWSER, Messages.ClonkPreferencePage_OpenExternalBrowser,
			ClonkPreferences.STRUCTURE_OUTLINES_IN_PROJECT_EXPLORER, Messages.ClonkPreferencePage_StructureOutlinesInProjectExplorer,
			ClonkPreferences.NO_AUTOBRACKETPAIRS, Messages.ClonkPreferencePage_NoAutoBrackets,
			ClonkPreferences.IGNORE_SIMPLE_FUNCTION_DUPES, Messages.TogglesPreferencePage_IgnoreSimpleFunctionDupes,
			ClonkPreferences.ANALYZE_CODE, Messages.TogglesPreferencePage_AnalyzeCode,
			ClonkPreferences.JAVA_STYLE_BLOCKS, Messages.TogglesPreferencePage_JavaStyleBlocks,
			ClonkPreferences.INSTANT_C4SCRIPT_COMPLETIONS, Messages.TogglesPreferencePage_InstantCompletions,
			ClonkPreferences.LONG_PARAMETER_INFO, Messages.TogglesPreferencePage_LongParameterInfo
		};
		for (int i = 0; i < toggles.length; i += 2) {
			addField(new BooleanFieldEditor(toggles[i], toggles[i+1], getFieldEditorParent()));
		}
		final IntegerFieldEditor cores = new IntegerFieldEditor(ClonkPreferences.TASKEXECUTION_THREADS, Messages.TogglesPreferencePage_TaskExecutionThreads, getFieldEditorParent());
		cores.setValidRange(1, Runtime.getRuntime().availableProcessors());
		addField(cores);
	}

	@Override
	public void init(final IWorkbench workbench) {
		setPreferenceStore(Core.instance().getPreferenceStore());
	}

	@Override
	public boolean performOk() {
		final boolean r = super.performOk();
		if (r) {
			for (final CommonNavigator nav : UI.projectExplorers()) {
				for (final IProject proj : ClonkProjectNature.clonkProjectsInWorkspace()) {
					nav.getCommonViewer().refresh(proj);
				}
			}
		}
		return r;
	}

}
