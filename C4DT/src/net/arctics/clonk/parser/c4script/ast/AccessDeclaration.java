package net.arctics.clonk.parser.c4script.ast;

import net.arctics.clonk.Core;
import net.arctics.clonk.parser.ASTNodePrinter;
import net.arctics.clonk.parser.Declaration;
import net.arctics.clonk.parser.EntityRegion;
import net.arctics.clonk.parser.ASTNode;
import net.arctics.clonk.parser.IPlaceholderPatternMatchTarget;
import net.arctics.clonk.parser.ParsingException;
import net.arctics.clonk.parser.c4script.C4ScriptParser;
import net.arctics.clonk.parser.c4script.DeclarationObtainmentContext;
import net.arctics.clonk.parser.c4script.Function;
import net.arctics.clonk.parser.c4script.ITypeable;
import net.arctics.clonk.parser.c4script.Variable;
import net.arctics.clonk.parser.c4script.ast.IASTComparisonDelegate.DifferenceHandling;
import net.arctics.clonk.parser.c4script.ast.IASTComparisonDelegate.Option;

import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;

/**
 * An expression referring to some {@link Declaration}. Derived classes represent access to {@link Variable}s ({@link AccessVar}) and {@link Function} calls ({@link CallDeclaration}).
 * @author madeen
 *
 */
public abstract class AccessDeclaration extends ASTNode implements IPlaceholderPatternMatchTarget {

	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
	protected transient Declaration declaration;
	protected String declarationName;
	
	/**
	 * Return the {@link Declaration} this expression refers to, obtaining it if that has not happened yet (see {@link #obtainDeclaration(DeclarationObtainmentContext)}).
	 * @param context Context passed to {@link #obtainDeclaration(DeclarationObtainmentContext)} if declaration is still null.
	 * @return The declaration or null if a declaration could not be found.
	 */
	public Declaration declarationFromContext(DeclarationObtainmentContext context) {
		if (declaration == null)
			declaration = obtainDeclaration(context);
		return declaration;
	}

	/**
	 * Return the {@link Declaration} if it has already been obtained or null.
	 * @return The obtain declaration or null.
	 */
	public Declaration declaration() {
		return declaration; // return without trying to obtain it (no parser context)
	}

	/**
	 * Obtain the declaration for this node. The base implementation will always return null.
	 * @param context The {@link DeclarationObtainmentContext} (sounds proper to pass it)
	 * @return The declaration this node most likely refers to
	 */
	protected Declaration obtainDeclaration(DeclarationObtainmentContext context) {
		return null;
	}

	@Override
	public void reportProblems(C4ScriptParser parser) throws ParsingException {
		super.reportProblems(parser);
		for (byte attempt = 0; declaration == null && attempt < 2; attempt++) {
			if (attempt == 1)
				// last straw in case a file was modified outside Eclipse to call some global function from a script which was not yet loaded
				// from the index - load scripts
				parser.script().index().loadScriptsContainingDeclarationsNamed(declarationName);
			declarationFromContext(parser); // find the declaration so subclasses can complain about missing variables/functions
		}
	}
	
	@Override
	public void reconsider(C4ScriptParser parser) {
		this.declaration = null;
		super.reconsider(parser);
	}

	/**
	 * Create AccessDeclaration object using a declaration name. 
	 * @param declarationName
	 */
	public AccessDeclaration(String declarationName) {
		this.declarationName = declarationName;
	}
	
	@Override
	public void doPrint(ASTNodePrinter output, int depth) {
		output.append(declarationName);
	}

	public IRegion declarationRegion(int offset) {
		return new Region(offset+start(), declarationName.length());
	}

	@Override
	public EntityRegion entityAt(int offset, C4ScriptParser parser) {
		return new EntityRegion(declarationFromContext(parser), region(0));
	}

	/**
	 * Return the declaration name this expression uses to refer to a {@link Declaration}.
	 * @return The declaration name
	 */
	public String declarationName() {
		return declarationName;
	}
	
	/**
	 * Set the declaration name.
	 * @param declarationName The name
	 */
	public void setDeclarationName(String declarationName) {
		this.declarationName = declarationName;
	}

	@Override
	public int identifierLength() {
		return declarationName.length();
	}

	/**
	 * Return whether this expression only indirectly refers to a declaration (e.g. inherited/_inherited)
	 * @return Whether or not.
	 */
	public boolean indirectAccess() {
		return declaration == null || !declaration.name().equals(declarationName);
	}
	
	@Override
	public DifferenceHandling compare(ASTNode other, IASTComparisonDelegate listener) {
		DifferenceHandling handling = super.compare(other, listener);
		if (handling != DifferenceHandling.Equal)
			return handling;
		AccessDeclaration otherDec = (AccessDeclaration) other;
		if (!listener.optionEnabled(Option.CheckForIdentity)) {
			if (!declarationName.equals(otherDec.declarationName))
				return listener.differs(this, other, "declarationName"); //$NON-NLS-1$
		} else if (declaration != otherDec.declaration)
			return listener.differs(this, other, "declaration"); //$NON-NLS-1$
		return DifferenceHandling.Equal;
	}

	/**
	 * Returns the class declarations referenced by this {@link AccessDeclaration} need to be instances of.
	 * @return The {@link Declaration} class
	 */
	public Class<? extends Declaration> declarationClass() {
		return Declaration.class;
	}
	
	@Override
	public ITypeInfo createTypeInfo(C4ScriptParser parser) {
		if (declaration instanceof ITypeable && ((ITypeable)declaration).staticallyTyped())
			return null;
		else
			return super.createTypeInfo(parser);
	}
	
	@Override
	public void postLoad(ASTNode parent, DeclarationObtainmentContext root) {
		super.postLoad(parent, root);
		declarationFromContext(root);
	}
	
	@Override
	public String patternMatchingText() {
		return declarationName();
	}
	
}