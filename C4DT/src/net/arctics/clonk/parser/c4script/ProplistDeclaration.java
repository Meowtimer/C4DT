package net.arctics.clonk.parser.c4script;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import net.arctics.clonk.index.ClonkIndex;
import net.arctics.clonk.parser.Declaration;
import net.arctics.clonk.parser.IHasIncludes;
import net.arctics.clonk.parser.Structure;
import net.arctics.clonk.util.ArrayUtil;
import net.arctics.clonk.util.CompoundIterable;

/**
 * A proplist declaration parsed from an {key:value, ...} expression.
 * @author madeen
 *
 */
public class ProplistDeclaration extends Structure implements IType, IHasIncludes {

	private static final long serialVersionUID = 1L;
	public static final String PROTOTYPE_KEY = "Prototype";
	
	protected List<Variable> components;
	
	protected boolean adHoc;

	/**
	 * Whether the declaration was "explicit" {blub=<blub>...} or
	 * by assigning values separately (effect.var1 = ...; ...)
	 * @return adhoc-ness
	 */
	public boolean isAdHoc() {
		return adHoc;
	}
	
	/**
	 * Each assignment in a proplist declaration is represented by a {@link Variable} object.
	 * @return Return the list of component variables this proplist declaration is made up of.
	 */
	public List<Variable> getComponents() {
		return components;
	}
	
	public ProplistDeclaration() {
		super();
		setName(String.format("%s {...}", PrimitiveType.PROPLIST.toString()));
	}

	/**
	 * Create a new ProplistDeclaration, passing it component variables it takes over directly. The list won't be copied.
	 * @param components The component variables
	 */
	public ProplistDeclaration(List<Variable> components) {
		this();
		this.components = components;
	}
	
	/**
	 * Create an adhoc proplist declaration.
	 * @return The newly created adhoc proplist declaration.
	 */
	public static ProplistDeclaration adHocDeclaration() {
		ProplistDeclaration result = new ProplistDeclaration();
		result.adHoc = true;
		result.components = new LinkedList<Variable>();
		return result;
	}
	
	/**
	 * Add a new component variable to this declaration.
	 * @param variable The variable to add
	 * @return Return either the passed variable or an already existing one with that name
	 */
	public Variable addComponent(Variable variable) {
		Variable found = findComponent(variable.getName());
		if (found != null) {
			return found;
		} else {
			components.add(variable);
			variable.setParentDeclaration(this);
			return variable;
		}
	}
	
	/**
	 * Find a component variable by name.
	 * @param declarationName The name of the variable
	 * @return The found variable or null.
	 */
	public Variable findComponent(String declarationName) {
		for (Variable v : components)
			if (v.getName().equals(declarationName))
				return v;
		return null;
	}

	@Override
	public Declaration findLocalDeclaration(String declarationName, Class<? extends Declaration> declarationClass) {
		for (Variable v : getComponents()) {
			if (v.getName().equals(declarationName)) {
				return v;
			}
		}
		return null;
	}
	
	@Override
	public Iterable<? extends Declaration> allSubDeclarations(int mask) {
		if ((mask & VARIABLES) != 0) {
			List<Iterable<? extends Declaration>> its = new LinkedList<Iterable<? extends Declaration>>();
			Set<IHasIncludes> includes = new HashSet<IHasIncludes>();
			its.add(getComponents());
			if ((mask & NO_INCLUDED_SUBDECLARATIONS) == 0) {
				gatherIncludes(includes, getIndex(), true);
				for (IHasIncludes i : includes)
					its.add(i.allSubDeclarations(mask & ~NO_INCLUDED_SUBDECLARATIONS));
			}
			return new CompoundIterable<Declaration>(its);
		}
		else
			return NO_SUB_DECLARATIONS;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends Declaration> T getLatestVersion(T from) {
		if (Variable.class.isAssignableFrom(from.getClass())) {
			return (T) findComponent(from.getName());
		} else {
			return super.getLatestVersion(from);
		}
	};
	
	@Override
	public Iterator<IType> iterator() {
		return ArrayUtil.arrayIterable(PrimitiveType.PROPLIST, this).iterator();
	}

	@Override
	public boolean canBeAssignedFrom(IType other) {
		return PrimitiveType.PROPLIST.canBeAssignedFrom(other);
	}

	@Override
	public String typeName(boolean special) {
		return PrimitiveType.PROPLIST.typeName(special);
	}

	@Override
	public boolean intersects(IType typeSet) {
		return PrimitiveType.PROPLIST.intersects(typeSet);
	}

	@Override
	public boolean containsType(IType type) {
		return PrimitiveType.PROPLIST.containsType(type);
	}

	@Override
	public boolean containsAnyTypeOf(IType... types) {
		return PrimitiveType.PROPLIST.containsAnyTypeOf(types);
	}

	@Override
	public int specificness() {
		return PrimitiveType.PROPLIST.specificness()+1;
	}

	@Override
	public IType staticType() {
		return PrimitiveType.PROPLIST;
	}
	
	@Override
	public String getName() {
		return "Proplist Expression 0009247331";
	}
	
	@Override
	public void setTypeDescription(String description) {}
	
	/**
	 * Return the prototype of this proplist declaration. Obtained from the special 'Prototype' entry.
	 * @return The Prototype {@link ProplistDeclaration} or null, if either the 'Prototype' entry does not exist or the type of the Prototype expression does not denote a proplist declaration.
	 */
	public IHasIncludes prototype() {
		for (Variable v : components)
			if (v.getName().equals(PROTOTYPE_KEY)) {
				IType t = v.getType();
				for (IType ty : t)
					if (ty instanceof IHasIncludes)
						return (IHasIncludes)ty;
			}
		return null;
	}

	@Override
	public Collection<? extends IHasIncludes> getIncludes(ClonkIndex index, boolean recursive) {
		Set<IHasIncludes> totalIncludes = new HashSet<IHasIncludes>();
		if (!recursive) {
			IHasIncludes proto = prototype();
			if (proto != null)
				totalIncludes.add(proto);
		} else {
			Set<IHasIncludes> alreadyVisited = new HashSet<IHasIncludes>();
			Collection<? extends IHasIncludes> includes = this.getIncludes(index, false);
			if (includes.size() > 0)
				for (IHasIncludes i : includes) {
					if (!alreadyVisited.contains(i)) {
						alreadyVisited.add(i);
						alreadyVisited.addAll(i.getIncludes(index, true));
					}
				}
		}
		return totalIncludes;
	}

	@Override
	public boolean includes(IHasIncludes other) {
		Set<IHasIncludes> includes = new HashSet<IHasIncludes>();
		gatherIncludes(includes, getIndex(), true);
		return includes.contains(other);
	}
	
	@Override
	public void gatherIncludes(Set<IHasIncludes> set, ClonkIndex index, boolean recursive) {
		IHasIncludes proto = prototype();
		if (proto != null)
			if (!set.contains(proto)) {
				set.add(proto);
				if (recursive)
					proto.gatherIncludes(set, index, true);
			}
	}
	
	@Override
	public boolean equals(Object other) {
		if (other instanceof ProplistDeclaration && ((ProplistDeclaration)other).getLocation().equals(this.getLocation()))
			return true;
		return false;
	}

}
