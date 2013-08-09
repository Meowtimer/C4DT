package net.arctics.clonk.c4script.ast;

import static net.arctics.clonk.util.ArrayUtil.filter;

import java.util.Arrays;
import java.util.List;

import net.arctics.clonk.Core;
import net.arctics.clonk.ast.ASTNode;
import net.arctics.clonk.ast.ASTNodePrinter;
import net.arctics.clonk.ast.EntityRegion;
import net.arctics.clonk.ast.ExpressionLocator;
import net.arctics.clonk.c4script.Function;
import net.arctics.clonk.c4script.Variable;
import net.arctics.clonk.c4script.Variable.Scope;
import net.arctics.clonk.util.ArrayUtil;

import org.eclipse.jface.text.Region;

/**
 * Variable declaration statement, containing multiple variable declarations which optionally include an initial assignment.
 * @author madeen
 *
 */
public class VarDeclarationStatement extends KeywordStatement {
	
	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
	private VarInitialization[] varInitializations;
	private final Scope scope;
	
	public Scope scope() { return scope; }
	
	public VarDeclarationStatement(List<VarInitialization> varInitializations, Scope scope) {
		super();
		this.varInitializations = varInitializations.toArray(new VarInitialization[varInitializations.size()]);
		this.scope = scope;
		assignParentToSubElements();
	}
	public VarDeclarationStatement(Scope scope, VarInitialization... varInitializations) {
		this(Arrays.asList(varInitializations), scope);
	}
	public VarDeclarationStatement(String varName, ASTNode initialization, int namePos, Scope scope) {
		this(ArrayUtil.list(new VarInitialization(varName, initialization, namePos, namePos+varName.length(), null)), scope);
	}
	@Override
	public String keyword() {
		return scope.toKeyword();
	}
	@Override
	public VarInitialization[] subElements() {
		return varInitializations;
	}
	@Override
	public void setSubElements(ASTNode[] elms) {
		final List<VarInitialization> inits = Arrays.asList(filter(elms, VarInitialization.class));
		final VarInitialization[] newElms = new VarInitialization[inits.size()];
		System.arraycopy(inits.toArray(), 0, newElms, 0, inits.size());
		varInitializations = newElms;
	}
	public final VarInitialization[] variableInitializations() {
		return varInitializations;
	}
	@Override
	public void doPrint(ASTNodePrinter builder, int depth) {
		builder.append(keyword());
		builder.append(" "); //$NON-NLS-1$
		int counter = 0;
		for (final VarInitialization var : varInitializations) {
			var.print(builder, depth);
			if (++counter < varInitializations.length)
				builder.append(", "); //$NON-NLS-1$
			else
				builder.append(";"); //$NON-NLS-1$
		}
	}
	@Override
	public EntityRegion entityAt(int offset, ExpressionLocator<?> locator) {
		final Function activeFunc = this.parent(Function.class);
		if (activeFunc != null) {
			final int addToMakeAbsolute = activeFunc.bodyLocation().start() + this.start();
			offset += addToMakeAbsolute;
			for (final VarInitialization pair : varInitializations) {
				final String varName = pair.name;
				final Variable var = activeFunc.findVariable(varName);
				if (var != null && var.isAt(offset))
					return new EntityRegion(var, new Region(var.start()-activeFunc.bodyLocation().start(), var.getLength()));
			}
		}
		return super.entityAt(offset, locator);
	}
}