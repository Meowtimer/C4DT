package net.arctics.clonk.ui.editors.actions.c4script;

import java.util.ResourceBundle;

import net.arctics.clonk.parser.C4Declaration;
import net.arctics.clonk.parser.ParsingException;
import net.arctics.clonk.ui.editors.IClonkCommandIds;
import net.arctics.clonk.ui.editors.ClonkTextEditor;
import net.arctics.clonk.ui.editors.c4script.DeclarationLocator;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.TextEditorAction;

public class OpenDeclarationAction extends TextEditorAction {

	public OpenDeclarationAction(ResourceBundle bundle, String prefix,
			ITextEditor editor) {
		super(bundle, prefix, editor);
		this.setActionDefinitionId(IClonkCommandIds.OPEN_DECLARATION);
	}
	
	protected C4Declaration getDeclarationAtSelection() throws BadLocationException, ParsingException {
		ITextSelection selection = (ITextSelection) getTextEditor().getSelectionProvider().getSelection();
		IRegion r = new Region(selection.getOffset(), selection.getLength());
		DeclarationLocator info = new DeclarationLocator(
			getTextEditor(),
			getTextEditor().getDocumentProvider().getDocument(getTextEditor().getEditorInput()),
			r
		);
		return info.getDeclaration();
	}

	@Override
	public void run() {
		// OpenDeclarationAction is for all text editors in the plugin so it opens declarations by querying for hyperlinks instead of relying on a script being edited
		ITextSelection selection = (ITextSelection) getTextEditor().getSelectionProvider().getSelection();
		ClonkTextEditor clonkEd = (ClonkTextEditor) getTextEditor();
		IHyperlink hyperlink = clonkEd.hyperlinkAtOffset(selection.getOffset());
		if (hyperlink != null)
			hyperlink.open();
//		try {
//			C4Declaration field = getDeclarationAtSelection();
//			if (field != null)
//				C4ScriptEditor.openDeclaration(field);
//		} catch (Exception e) {
//			// so what
//		}
	}
}
