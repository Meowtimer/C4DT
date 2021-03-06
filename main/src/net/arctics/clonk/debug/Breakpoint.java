package net.arctics.clonk.debug;

import net.arctics.clonk.Core;
import net.arctics.clonk.ui.debug.ClonkDebugModelPresentation;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.LineBreakpoint;

public class Breakpoint extends LineBreakpoint {

	public static final String ID = Breakpoint.class.getName(); // who needs ids differing from class name -.-

	public Breakpoint() {
		super();
	}

	public Breakpoint(final IResource resource, final int lineNumber) throws CoreException {
		final IWorkspaceRunnable markerAttribs = monitor -> {
			final IMarker marker = resource.createMarker(Core.id("breakpointMarker")); //$NON-NLS-1$
			marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
			marker.setAttribute(IBreakpoint.ENABLED, true);
			marker.setAttribute(IBreakpoint.ID, getModelIdentifier());
			marker.setAttribute(IMarker.MESSAGE, String.format(Messages.ClonkDebugLineBreakpoint_BreakpointMessage, resource.getProjectRelativePath(), lineNumber));
			setMarker(marker);
		};
		run(getMarkerRule(resource), markerAttribs);
	}

	@Override
	public String getModelIdentifier() {
		return ClonkDebugModelPresentation.ID;
	}

}
