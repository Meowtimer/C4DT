package net.arctics.clonk.ui.editors.actions.c4script;

import java.util.ResourceBundle;

import net.arctics.clonk.ast.Declaration;
import net.arctics.clonk.builder.ClonkProjectNature;
import net.arctics.clonk.ui.editors.ClonkTextEditor;
import net.arctics.clonk.ui.editors.actions.ClonkTextEditorAction;
import net.arctics.clonk.ui.editors.actions.ClonkTextEditorAction.CommandId;
import net.arctics.clonk.ui.search.ReferencesSearchQuery;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.search.ui.NewSearchUI;
import org.eclipse.ui.texteditor.ITextEditor;

@CommandId(id="ui.editors.actions.FindReferences")
public class FindReferencesAction extends ClonkTextEditorAction {

	public FindReferencesAction(ResourceBundle bundle, String prefix, ITextEditor editor) {
		super(bundle, prefix, editor);
	}

	@Override
	public void run() {
		try {
			Declaration declaration = declarationAtSelection(false);
			if (declaration == null)
				declaration = ((ClonkTextEditor)getTextEditor()).structure();
			if (declaration != null) {
				ClonkProjectNature nature = ClonkProjectNature.get(declaration.script());
				if (nature == null)
					nature = ClonkProjectNature.get(declaration.resource());
				if (nature == null) {
					MessageDialog.openError(getTextEditor().getSite().getShell(), Messages.FindReferencesAction_Label, Messages.FindReferencesAction_OnlyWorksWithinProject);
					return;
				}
				NewSearchUI.runQueryInBackground(new ReferencesSearchQuery(declaration, nature));
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

}
