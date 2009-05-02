package net.arctics.clonk.ui.editors;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;

import net.arctics.clonk.parser.C4Function;
import net.arctics.clonk.parser.C4Object;
import net.arctics.clonk.parser.C4ObjectIntern;
import net.arctics.clonk.parser.ClonkIndex;
import net.arctics.clonk.util.Utilities;

import org.eclipse.jface.text.contentassist.ContextInformation;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.texteditor.ITextEditor;

public abstract class ClonkCompletionProcessor implements IContentAssistProcessor {

	protected ITextEditor editor;
	
	public ClonkCompletionProcessor(ITextEditor editor) {
		this.editor = editor;
	}
	
	protected void proposalForObject(C4Object obj,String prefix,int offset,Collection<ICompletionProposal> proposals) {
		try {
			if (obj == null || obj.getId() == null)
				return;

			if (prefix != null) {
				if (!(
					obj.getName().toLowerCase().startsWith(prefix) ||
					obj.getId().getName().toLowerCase().startsWith(prefix) ||
					(obj instanceof C4ObjectIntern && ((C4ObjectIntern)obj).getObjectFolder() != null && ((C4ObjectIntern)obj).getObjectFolder().getName().startsWith(prefix))
				))
					return;
			}
			String displayString = obj.getName();
			int replacementLength = prefix != null ? prefix.length() : 0;

			// no need for context information
//			String contextInfoString = obj.getName();
//			IContextInformation contextInformation = null;// new ContextInformation(obj.getId().getName(),contextInfoString); 

			ICompletionProposal prop = new ClonkCompletionProposal(obj.getId().getName(), offset, replacementLength, obj.getId().getName().length(),
					Utilities.getIconForObject(obj), displayString.trim(), null, null, " - " + obj.getId().getName());
			proposals.add(prop);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(obj.toString());
		}
	}
	
	protected void proposalsForIndexedObjects(ClonkIndex index, int offset, int wordOffset, String prefix, Collection<ICompletionProposal> proposals) {
		for (C4Object obj : index.objectsIgnoringRemoteDuplicates(((IFileEditorInput)editor.getEditorInput()).getFile())) {
			proposalForObject(obj, prefix, wordOffset, proposals);
		}
	}
	
	protected void proposalForFunc(C4Function func,String prefix,int offset, Collection<ICompletionProposal> proposals,String parentName, boolean brackets) {
		if (prefix != null) {
			if (!func.getName().toLowerCase().startsWith(prefix))
				return;
		}
		String displayString = func.getLongParameterString(true);
		int replacementLength = 0;
		if (prefix != null) replacementLength = prefix.length();
		
		String contextInfoString = func.getLongParameterString(false);
		IContextInformation contextInformation = new ContextInformation(func.getName() + "()",contextInfoString); 
		
		String replacement = func.getName() + (brackets ? "()" : "");
		ClonkCompletionProposal prop = new ClonkCompletionProposal(replacement, offset,replacementLength,func.getName().length()+1,
				Utilities.getIconForFunction(func), displayString.trim(),contextInformation, func.getShortInfo()," - " + parentName);
		proposals.add(prop);
	}

	protected ICompletionProposal[] sortProposals(ICompletionProposal[] proposals) {
		Arrays.sort(proposals, new Comparator<ICompletionProposal>() {
			public int compare(ICompletionProposal propA, ICompletionProposal propB) {
				return (propA.getDisplayString().compareToIgnoreCase(propB.getDisplayString()));
			}
		});
		return proposals;
	}

}
