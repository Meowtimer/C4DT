package net.arctics.clonk.c4script.ast;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jface.text.Region;

import net.arctics.clonk.Core;
import net.arctics.clonk.ast.ASTNode;
import net.arctics.clonk.ast.ASTNodePrinter;
import net.arctics.clonk.ast.AppendableBackedNodePrinter;
import net.arctics.clonk.ast.ControlFlowException;
import net.arctics.clonk.ast.EntityRegion;
import net.arctics.clonk.ast.ExpressionLocator;
import net.arctics.clonk.ast.IEvaluationContext;
import net.arctics.clonk.c4script.Conf;
import net.arctics.clonk.c4script.ProplistDeclaration;
import net.arctics.clonk.c4script.Variable;
import net.arctics.clonk.util.StringUtil;

public class PropListExpression extends ASTNode {

	private static final int MULTILINEPRINTTHRESHOLD = 50;
	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;

	private ProplistDeclaration definedDeclaration;

	public ProplistDeclaration definedDeclaration() { return definedDeclaration; }
	
	public Collection<Variable> components() { return definedDeclaration.components(false); }
	
	@Override
	public boolean isValidInSequence(final ASTNode predecessor) { return predecessor == null; }
	
	@Override
	public void setParent(final ASTNode parent) { super.setParent(parent); }

	public PropListExpression(final ProplistDeclaration declaration) {
		this.definedDeclaration = declaration;
		assignParentToSubElements();
	}
	
	@Override
	public void doPrint(final ASTNodePrinter output_, final int depth) {
		final char FIRSTBREAK = (char)255;
		final char BREAK = (char)254;
		final char LASTBREAK = (char)253;
		final StringBuilder builder = new StringBuilder();
		final ASTNodePrinter output = new AppendableBackedNodePrinter(builder);
		output.append('{');
		final Collection<Variable> components = components();
		int i = 0;
		for (final Variable component : components) {
			output.append(i > 0 ? BREAK : FIRSTBREAK);
			output.append(component.name());
			output.append(':'); 
			if (!(component.initializationExpression() instanceof PropListExpression)) {
				output.append(' ');
			}
			component.initializationExpression().print(output, depth+1);
			if (i < components.size()-1) {
				output.append(',');
			} else {
				output.append(LASTBREAK);
			}
			i++;
		}
		output.append('}');
		String s = output.toString();
		if (s.length() > MULTILINEPRINTTHRESHOLD) {
			Conf.blockPrelude(output_, depth);
			s = s
				.replaceAll(String.format("%c", FIRSTBREAK), "\n"+StringUtil.multiply(Conf.indentString, depth+1))
				.replaceAll(String.valueOf(BREAK), "\n"+StringUtil.multiply(Conf.indentString, depth+1))
				.replace(String.valueOf(LASTBREAK), "\n"+StringUtil.multiply(Conf.indentString, depth));
		} else {
			//output_.append(' ');
			s = s
				.replaceAll(String.format("[%c%c]", FIRSTBREAK, LASTBREAK), "")
				.replaceAll(String.valueOf(BREAK), " ");
		}
		output_.append(s);
	}

	@Override
	public ASTNode[] subElements() {
		if (definedDeclaration == null) {
			return EMPTY_EXPR_ARRAY;
		}
		final Collection<Variable> components = components();
		final ASTNode[] result = new ASTNode[components.size()*2];
		int i = 0;
		for (final Variable c : components) {
			result[i++] = c.initializationExpression();
		}
		return result;
	}
	
	@Override
	public void setSubElements(final ASTNode[] elms) {
		if (definedDeclaration == null) {
			return;
		}
		final Collection<Variable> components = components();
		int i = 0;
		for (final Variable c : components) {
			c.setInitializationExpression(elms[i++]);
		}
	}
	
	@Override
	public boolean isConstant() {
		// whoohoo, proplist expressions can be constant if all components are constant
		for (final Variable component : components()) {
			if (!component.initializationExpression().isConstant()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public Object evaluateStatic(final IEvaluationContext context) {
		final Collection<Variable> components = components();
		final Map<String, Object> map = new HashMap<String, Object>(components.size());
		for (final Variable component : components) {
			map.put(component.name(), component.initializationExpression().evaluateStatic(context));
		}
		return map;
	}
	
	@Override
	public Object evaluate(IEvaluationContext context) throws ControlFlowException {
		final Collection<Variable> components = components();
		final Map<String, Object> map = new HashMap<String, Object>(components.size());
		for (final Variable component : components) {
			map.put(component.name(), evaluateVariable(component.initializationExpression().evaluate(context)));
		}
		return map;
	}

	public ASTNode value(final String key) {
		final Variable keyVar = definedDeclaration.findComponent(key);
		return keyVar != null ? keyVar.initializationExpression() : null;
	}

	@SuppressWarnings("unchecked")
	public <T> T valueEvaluated(final String key, final Class<T> cls) {
		final ASTNode e = value(key);
		if (e != null) {
			final Object eval = e.evaluateStatic(definedDeclaration.parent(IEvaluationContext.class));
			return eval != null && cls.isAssignableFrom(eval.getClass()) ? (T)eval : null;
		} else {
			return null;
		}
	}

	@Override
	public PropListExpression clone() {
		synchronized (this) {
			// when calling super.clone(), the sub elements obtained from definedDeclaration will be cloned
			// and then reassigned to the original ProplistDeclaration which is not desired so temporarily
			// set definedDeclaration to null to avoid this.
			final ProplistDeclaration saved = this.definedDeclaration;
			this.definedDeclaration = null;
			try {
				// regular copying of attributes with no sub element cloning taking place
				final PropListExpression e = (PropListExpression) super.clone();
				// clone the ProplistDeclaration, also cloning sub variables. This will automatically
				// lead to subElements also returning cloned initialization expressions.
				e.definedDeclaration = saved.clone();
				return e;
			} finally {
				// restore state of original expression which is not supposed to be altered by calling clone()
				this.definedDeclaration = saved;
			}
		}
	}

	@Override
	public EntityRegion entityAt(final int offset, final ExpressionLocator<?> locator) {
		final int secOff = sectionOffset();
		final int absolute = secOff+start()+offset;
		for (final Variable v : this.components()) {
			if (v.isAt(absolute)) {
				return new EntityRegion(v, v.relativeTo(new Region(secOff, 0)));
			}
		}
		return super.entityAt(offset, locator);
	}

}