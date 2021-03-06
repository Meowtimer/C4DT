package net.arctics.clonk.ui.editors;

import static java.util.Arrays.stream;
import static net.arctics.clonk.util.StreamUtil.ofType;
import static net.arctics.clonk.util.Utilities.attempt;

import java.util.HashSet;
import java.util.Set;

import net.arctics.clonk.c4script.Script;
import net.arctics.clonk.ui.search.DuplicatesSearchQuery;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.search.ui.NewSearchUI;
import org.eclipse.ui.handlers.HandlerUtil;

public class FindDuplicatesHandler extends AbstractHandler {
	@Override
	public Object execute(final ExecutionEvent event) throws ExecutionException {
		final ISelection selection = HandlerUtil.getCurrentSelection(event);
		if (selection instanceof IStructuredSelection) {
			final IStructuredSelection ssel = (IStructuredSelection) selection;
			final Set<Script> scripts = new HashSet<Script>();
			final IResourceVisitor visitor = resource -> {
				final Script script = Script.get(resource, false);
				if (script != null)
					scripts.add(script);
				return true;
			};
			ofType(stream(ssel.toArray()), IResource.class).forEach(res -> attempt(
				() -> { res.accept(visitor); return true; },
				CoreException.class, Exception::printStackTrace)
			);
			NewSearchUI.runQueryInBackground(DuplicatesSearchQuery.queryWithScripts(scripts));
		}
		return null;
	}
}