package net.arctics.clonk.ui.editors.actions;

import java.util.LinkedList;
import java.util.ResourceBundle;

import net.arctics.clonk.parser.C4ScriptParser;
import net.arctics.clonk.parser.CompilerException;
import net.arctics.clonk.parser.C4ScriptExprTree.ExprElm;
import net.arctics.clonk.ui.editors.C4ScriptEditor;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.TextEditorAction;

public class ConvertOldCodeToNewCodeAction extends TextEditorAction {

	public ConvertOldCodeToNewCodeAction(ResourceBundle bundle,
			String prefix, ITextEditor editor) {
		super(bundle, prefix, editor);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.action.Action#run()
	 */
	@Override
	public void run() {
		C4ScriptEditor editor = (C4ScriptEditor)this.getTextEditor();
		ITextSelection selection = (ITextSelection)editor.getSelectionProvider().getSelection();
		IDocument document = editor.getDocumentProvider().getDocument(editor.getEditorInput());
		final LinkedList<ExprElm> expressions = new LinkedList<ExprElm>();
		try {
			editor.reparseWithDocumentContents(new C4ScriptParser.IExpressionNotifiee() {
				public void parsedToplevelExpression(ExprElm expression) {
					expressions.addFirst(expression);
				}
			},false);
		} catch (CompilerException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		for (ExprElm e : expressions) {
			try {
				document.replace(e.getExprStart(), e.getExprEnd()-e.getExprStart(), e.newStyleReplacement().toString());
			} catch (BadLocationException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (CloneNotSupportedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
	}
	
}