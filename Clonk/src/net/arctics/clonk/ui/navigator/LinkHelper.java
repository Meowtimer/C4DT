package net.arctics.clonk.ui.navigator;

import net.arctics.clonk.parser.C4Declaration;
import net.arctics.clonk.ui.editors.ClonkTextEditor;
import net.arctics.clonk.ui.editors.c4script.ScriptWithStorageEditorInput;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.navigator.ILinkHelper;

public class LinkHelper implements ILinkHelper {

	public void activateEditor(IWorkbenchPage page,
			IStructuredSelection selection) {
		try {
			if (selection.getFirstElement() instanceof C4Declaration) {
				C4Declaration dec = (C4Declaration) selection.getFirstElement();
				IWorkbenchPage wpage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
				IEditorInput input = dec.getStructure() != null ? dec.getStructure().getEditorInput() : null;
				if (input != null && wpage.findEditor(input) != null)
					ClonkTextEditor.openDeclaration(dec, false);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public IStructuredSelection findSelection(IEditorInput anInput) {
		ScriptWithStorageEditorInput input = (ScriptWithStorageEditorInput) anInput;
		StructuredSelection sel = new StructuredSelection(input.getScript());
		return sel;
	}

}
