package net.arctics.clonk.parser.mapcreator;

import net.arctics.clonk.parser.C4Structure;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;

public class C4MapCreator extends C4Map {

	private static final long serialVersionUID = 1L;
	
	private IFile file;

	public IFile getFile() {
		return file;
	}

	public void setFile(IFile file) {
		this.file = file;
	}

	public C4MapCreator(IFile file) {
		super();
		this.file = file;
	}
	
	@Override
	public IResource getResource() {
		return file;
	}
	
	public static void register() {
		registerStructureFactory(new IStructureFactory() {
			public C4Structure create(IFile file) {
				if (file.getName().equalsIgnoreCase("Landscape.txt")) { //$NON-NLS-1$
					C4MapCreator mapCreator = new C4MapCreator(file);
					MapCreatorParser parser = new MapCreatorParser(mapCreator);
					parser.parse();
					return mapCreator;
				}
				return null;
			}
		});
	}

}
