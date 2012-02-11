package net.arctics.clonk.ui.editors.c4script;

import net.arctics.clonk.ClonkCore;
import net.arctics.clonk.index.Engine;
import net.arctics.clonk.parser.Declaration;
import net.arctics.clonk.resource.ClonkProjectNature;
import net.arctics.clonk.ui.editors.ClonkSourceViewerConfiguration;
import net.arctics.clonk.ui.editors.ClonkTextHover;
import net.arctics.clonk.util.Utilities;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;

public class C4ScriptTextHover extends ClonkTextHover<C4ScriptEditor> {

	private EntityLocator entityLocator;
	
	public C4ScriptTextHover(ClonkSourceViewerConfiguration<C4ScriptEditor> clonkSourceViewerConfiguration) {
	    super(clonkSourceViewerConfiguration);
    }

	// some overriding necessary so hovers also work for declarations that can't be hyperlinked (engine declarations and such)

	@Override
	public String getHoverInfo(ITextViewer viewer, IRegion region) {
		IFile scriptFile = Utilities.fileBeingEditedBy(configuration.editor());
		StringBuilder messageBuilder = new StringBuilder();
		if (entityLocator != null && entityLocator.entity() != null) {
			messageBuilder.append(entityLocator.entity().infoText());
			if (!(entityLocator.entity() instanceof Declaration && ((Declaration)entityLocator.entity()).isEngineDeclaration())) {
				Engine engine = ClonkProjectNature.engineFromResource(scriptFile);
				if (engine != null) {
					Declaration engineDeclaration = engine.findDeclaration(entityLocator.entity().name());
					if (engineDeclaration != null) {
						messageBuilder.append("<br/><br/><b>"+"Engine:"+"</b><br/>");
						messageBuilder.append(engineDeclaration.infoText());
					}
				}
			}
		}
		else {
			String superInfo = super.getHoverInfo(viewer, region);
			if (superInfo != null)
				messageBuilder.append(superInfo);
		}
		try {
			IMarker[] markers = scriptFile.findMarkers(ClonkCore.MARKER_C4SCRIPT_ERROR, true, IResource.DEPTH_ONE);
			boolean foundSomeMarkers = false;
			for (IMarker m : markers) {
				int charStart;
				IRegion markerRegion = new Region(
					charStart = m.getAttribute(IMarker.CHAR_START, -1),
					m.getAttribute(IMarker.CHAR_END, -1)-charStart
				);
				if (Utilities.regionContainsOtherRegion(markerRegion, region)) {
					if (!foundSomeMarkers) {
						if (messageBuilder.length() > 0)
							messageBuilder.append("<br/><br/><b>"+Messages.C4ScriptTextHover_Markers1+"</b><br/>"); //$NON-NLS-1$ 
						foundSomeMarkers = true;
					}
					messageBuilder.append(m.getAttribute(IMarker.MESSAGE));
					messageBuilder.append("<br/>"); //$NON-NLS-1$
				}
			}
		} catch (Exception e) {
			// whatever
		}
		return messageBuilder.toString();
	}

	@Override
	public IRegion getHoverRegion(ITextViewer viewer, int offset) {
		super.getHoverRegion(viewer, offset);
		IRegion region = new Region(offset, 0);
		try {
			entityLocator = new EntityLocator(configuration.editor(), viewer.getDocument(), region);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return region;
	}

}
