package net.arctics.clonk.c4script;

import net.arctics.clonk.index.Index;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;

public class MapScript extends SystemScript {
	private static final long serialVersionUID = 1L;
	public MapScript(Index index, IFile scriptFile) throws CoreException { super(index, scriptFile); }
}