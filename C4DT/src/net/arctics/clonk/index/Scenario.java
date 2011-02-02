package net.arctics.clonk.index;

import net.arctics.clonk.ClonkCore;
import net.arctics.clonk.parser.ID;

import org.eclipse.core.resources.IContainer;

/**
 * A scenario. 
 * @author madeen
 *
 */
public class Scenario extends ProjectDefinition {

	public Scenario(ID id, String name, IContainer container) {
		super(id, name, container);
	}
	
	public static Scenario get(IContainer folder) {
		ProjectDefinition obj = objectCorrespondingTo(folder);
		return obj instanceof Scenario ? (Scenario)obj : null;
	}


	private static final long serialVersionUID = ClonkCore.SERIAL_VERSION_UID;

}