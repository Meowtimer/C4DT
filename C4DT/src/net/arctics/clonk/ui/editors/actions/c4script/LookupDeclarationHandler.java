package net.arctics.clonk.ui.editors.actions.c4script;

import java.util.HashSet;
import java.util.Set;

import net.arctics.clonk.parser.Declaration;
import net.arctics.clonk.parser.IHasIncludes;
import net.arctics.clonk.parser.c4script.IHasSubDeclarations;
import net.arctics.clonk.parser.c4script.Script;
import net.arctics.clonk.resource.ClonkProjectNature;
import net.arctics.clonk.ui.editors.c4script.C4ScriptEditor;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.handlers.HandlerUtil;

public class LookupDeclarationHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkbenchPart part = HandlerUtil.getActivePart(event);
		if (part instanceof C4ScriptEditor) {
			Script script = ((C4ScriptEditor)part).scriptBeingEdited();
			if (script != null) {
				Set<IHasIncludes> scripts = new HashSet<IHasIncludes>();
				script.gatherIncludes(scripts, true);
				Set<Declaration> declarations = new HashSet<Declaration>();
				for (IHasIncludes s : scripts)
					if (s instanceof Script)
						for (Declaration d : ((Script)s).allSubDeclarations(IHasSubDeclarations.DIRECT_SUBDECLARATIONS))
							declarations.add(d);
				EntityChooser chooser = new EntityChooser(HandlerUtil.getActiveShell(event), declarations);
				chooser.setInitialPattern(".*");
				chooser.run();
			}
		}
		else {
			ClonkProjectNature nat = ClonkProjectNature.get(part);
			if (nat != null && nat.index() != null)
				new EntityChooser(HandlerUtil.getActiveShell(event), nat.index()).run();
		}
		return null;
	}

}
