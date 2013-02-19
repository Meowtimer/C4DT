package net.arctics.clonk.parser.c4script.ast;

import net.arctics.clonk.Core;
import net.arctics.clonk.parser.ASTNode;
import net.arctics.clonk.parser.c4script.Keywords;

public class CallInherited extends CallDeclaration {
	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
	private final boolean failsafe;
	public boolean failsafe() { return failsafe; }
	public CallInherited(boolean failsafe, ASTNode[] parameters) {
		super((String)null, parameters);
		this.failsafe = failsafe;
	}
	@Override
	public String declarationName() { return failsafe ? Keywords.SafeInherited : Keywords.Inherited; }
}
