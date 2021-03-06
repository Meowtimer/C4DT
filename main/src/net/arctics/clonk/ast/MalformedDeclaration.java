package net.arctics.clonk.ast;

import net.arctics.clonk.Core;

public class MalformedDeclaration extends Declaration {
	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
	private final String source;
	public MalformedDeclaration(final String source) { this.source = source; }
	@Override
	public void doPrint(final ASTNodePrinter output, final int depth) { output.append(source); }
	@Override
	public String toString() { return source; };
}
