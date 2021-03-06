package net.arctics.clonk.ui.editors.actions.c4script;

import java.util.ResourceBundle;

import org.eclipse.search.ui.NewSearchUI;
import org.eclipse.ui.texteditor.ITextEditor;

import net.arctics.clonk.ast.Declaration;
import net.arctics.clonk.builder.ClonkProjectNature;
import net.arctics.clonk.index.Definition.ProxyVar;
import net.arctics.clonk.ui.editors.StructureTextEditor;
import net.arctics.clonk.ui.editors.actions.ClonkTextEditorAction;
import net.arctics.clonk.ui.editors.actions.ClonkTextEditorAction.CommandId;
import net.arctics.clonk.ui.search.ReferencesSearchQuery;

@CommandId(id="ui.editors.actions.FindReferences")
public class FindReferencesAction extends ClonkTextEditorAction {
	public FindReferencesAction(final ResourceBundle bundle, final String prefix, final ITextEditor editor) { super(bundle, prefix, editor); }
	@Override
	public void run() {
		try {
			Declaration declaration = declarationAtSelection(false);
			final Declaration structure = ((StructureTextEditor)getTextEditor()).structure();
			if (declaration == null) {
				declaration = structure;
			}
			if (declaration != null) {
				if (declaration instanceof ProxyVar) {
					declaration = ((ProxyVar)declaration).definition();
				}
				final ClonkProjectNature nature = ClonkProjectNature.get(structure.resource().getProject());
				NewSearchUI.runQueryInBackground(new ReferencesSearchQuery(nature, declaration));
			}
		} catch (final Exception e) { e.printStackTrace(); }
	}
}
