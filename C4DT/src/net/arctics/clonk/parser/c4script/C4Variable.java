package net.arctics.clonk.parser.c4script;

import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.security.InvalidParameterException;
import java.util.HashSet;
import java.util.Set;

import net.arctics.clonk.index.C4Engine;
import net.arctics.clonk.index.C4Object;
import net.arctics.clonk.index.C4ObjectIntern;
import net.arctics.clonk.index.ClonkIndex;
import net.arctics.clonk.parser.C4Declaration;
import net.arctics.clonk.parser.C4ID;
import net.arctics.clonk.parser.C4Structure;
import net.arctics.clonk.parser.c4script.C4ScriptExprTree.ExprElm;
import net.arctics.clonk.resource.ClonkProjectNature;

/**
 * Represents a variable.
 * @author ZokRadonh
 *
 */
public class C4Variable extends C4Declaration implements Serializable, ITypedDeclaration, IHasUserDescription {

	private static final long serialVersionUID = -2350345359769750230L;
	
	/**
	 * Scope (local, static or function-local)
	 */
	private C4VariableScope scope;
	
	/**
	 * Type of the variable.
	 */
	private IType type;
	
	/**
	 * Mostly null - only set when type=object
	 */
	private transient WeakReference<C4Object> objectType;
	
	/**
	 * Instead of storing a ref to a C4Object (and retaining it) just store the id and restore the weak ref to the object.
	 */
	private C4ID objectID;
	
	/**
	 * Descriptive text meant for the user
	 */
	private String description;
	
	/**
	 * It's a reference. Only parameters get this flag set when they are explicitly marked as by-ref. 
	 */
	private boolean byRef;
	
	/**
	 * Explicit type, not to be changed by weird type inference
	 */
	private transient boolean typeLocked;
	
	/**
	 * Value of the constant. Only for constants quite obviously.
	 */
	private Object constValue;
	
	/**
	 * Variable object used as the special 'this' object.
	 */
	public static final C4Variable THIS = new C4Variable("this", C4Type.OBJECT, Messages.This_Description); //$NON-NLS-1$
	
	private C4Variable(String name, C4Type type, String desc) {
		this(name, type, desc, C4VariableScope.VAR_VAR);
		typeLocked = true;
	}
	
	public C4Variable(String name, C4Type type) {
		this.name = name;
		this.type = type;
	}
	
	public C4Variable(String name, C4VariableScope scope) {
		this.name = name;
		this.scope = scope;
		objectType = null;
		description = ""; //$NON-NLS-1$
		type = C4Type.UNKNOWN;
	}
	
	public C4Variable(String name, C4Type type, String desc, C4VariableScope scope) {
		this.name = name;
		this.type = type;
		this.description = desc;
		this.scope = scope;
		objectType = null;
	}
	
	@Override
	public C4Declaration latestVersion() {
		if (parentDeclaration instanceof C4Structure)
			return ((C4Structure)parentDeclaration).findDeclaration(getName(), C4Variable.class);
		return super.latestVersion();
	}

	public C4Variable() {
		name = ""; //$NON-NLS-1$
		scope = C4VariableScope.VAR_VAR;
	}
	
	public C4Variable(String name, String scope) {
		this(name,C4VariableScope.makeScope(scope));
	}

	/**
	 * @return the type
	 */
	public IType getType() {
		if (type == null)
			type = C4Type.UNKNOWN;
		return type;
	}
	
	/**
	 * @param type the type to set
	 */
	public void forceType(IType type) {
		// -.-;
//		if (type == C4Type.DWORD) formerly DWORD
//			type = C4Type.INT;
		this.type = type;
	}
	
	public void forceType(C4Type type, boolean typeLocked) {
		forceType(type);
		this.typeLocked = typeLocked;
	}
	
	public void setType(IType type) {
		if (typeLocked)
			return;
		forceType(type);
	}

	/**
	 * @return the expectedContent
	 */
	public C4Object getObjectType() {
		return objectType != null ? objectType.get() : null;
	}

	/**
	 * @param objType the object type to set
	 */
	public void setObjectType(C4Object objType) {
		this.objectType = objType != null ? new WeakReference<C4Object>(objType) : null;
		this.objectID = objType != null ? objType.getId() : null;
	}

	public C4ID getObjectID() {
		return objectID;
	}

	/**
	 * @return the scope
	 */
	public C4VariableScope getScope() {
		return scope;
	}
	
	/**
	 * @return the description
	 */
	public String getUserDescription() {
		return isEngineDeclaration() ? getEngine().descriptionFor(this) : description;
	}

	/**
	 * @param description the description to set
	 */
	public void setUserDescription(String description) {
		this.description = description;
	}

	/**
	 * @param scope the scope to set
	 */
	public void setScope(C4VariableScope scope) {
		this.scope = scope;
	}

	/**
	 * The scope of a variable
	 * @author ZokRadonh
	 *
	 */
	public enum C4VariableScope implements Serializable {
		VAR_STATIC,
		VAR_LOCAL,
		VAR_VAR,
		VAR_CONST;
		
		public static C4VariableScope makeScope(String scopeString) {
			if (scopeString.equals(Keywords.VarNamed)) return C4VariableScope.VAR_VAR;
			if (scopeString.equals(Keywords.LocalNamed)) return C4VariableScope.VAR_LOCAL;
			if (scopeString.equals(Keywords.GlobalNamed)) return C4VariableScope.VAR_STATIC;
			if (scopeString.equals(Keywords.GlobalNamed + " " + Keywords.Const)) return C4VariableScope.VAR_CONST; //$NON-NLS-1$
			//if (C4VariableScope.valueOf(scopeString) != null) return C4VariableScope.valueOf(scopeString);
			else return null;
		}
		
		public String toKeyword() {
			switch (this) {
			case VAR_CONST:
				return Keywords.GlobalNamed + " " + Keywords.Const; //$NON-NLS-1$
			case VAR_STATIC:
				return Keywords.GlobalNamed;
			case VAR_LOCAL:
				return Keywords.LocalNamed;
			case VAR_VAR:
				return Keywords.VarNamed;
			default:
				return null;
			}
		}
	}
	
	public int sortCategory() {
		if (scope == null) return C4VariableScope.VAR_VAR.ordinal();
		return scope.ordinal();
	}

	private static String htmlerize(String text) {
		return text.replace("<", "&lt;").replace(">", "&gt;");
	}
	
	@Override
	public String getInfoText() {
		StringBuilder builder = new StringBuilder();
		builder.append("<b>"); //$NON-NLS-1$
		builder.append(htmlerize((getType() == C4Type.UNKNOWN ? C4Type.ANY : getType()).typeName(false)));
		builder.append(" "); //$NON-NLS-1$
		builder.append(getName());
		if (constValue != null) {
			builder.append(" = "); //$NON-NLS-1$
			builder.append(constValue.toString());
		}
		builder.append("</b>"); //$NON-NLS-1$
		if (getUserDescription() != null && getUserDescription().length() > 0) {
			builder.append("<br>"); //$NON-NLS-1$
			builder.append(getUserDescription());
		}
		return builder.toString();
	}

	public void inferTypeFromAssignment(ExprElm val, C4ScriptParser context) {
		if (typeLocked)
			return;
		ITypedDeclaration.Default.inferTypeFromAssignment(this, val, context);
	}
	
	public void expectedToBeOfType(IType t) {
		// engine objects should not be altered
		if (!typeLocked && !(getScript() instanceof C4Engine))
			ITypedDeclaration.Default.expectedToBeOfType(this, t);
	}

	public boolean isByRef() {
		return byRef;
	}

	public void setByRef(boolean byRef) {
		this.byRef = byRef;
	}

	public Object getConstValue() {
		return constValue;
	}

	public void setConstValue(Object constValue) {
		if (C4Type.typeFrom(constValue) == C4Type.ANY)
			throw new InvalidParameterException("constValue must be of primitive type recognized by C4Type"); //$NON-NLS-1$
		this.constValue = constValue;
	}

	@Override
	public Object[] occurenceScope(ClonkProjectNature project) {
		if (parentDeclaration instanceof C4Function)
			return new Object[] {parentDeclaration};
		if (!isGlobal() && parentDeclaration instanceof C4ObjectIntern) {
			C4ObjectIntern obj = (C4ObjectIntern) parentDeclaration;
			ClonkIndex index = obj.getIndex();
			Set<Object> result = new HashSet<Object>();
			result.add(obj);
			for (C4Object o : index) {
				if (o.includes(obj)) {
					result.add(o);
				}
			}
			for (C4ScriptBase script : index.getIndexedScripts()) {
				if (script.includes(obj)) {
					result.add(script);
				}
			}
			// scenarios... unlikely
			return result.toArray();
		}
		return super.occurenceScope(project);
	}

	
	@Override
	public boolean isGlobal() {
		return scope == C4VariableScope.VAR_STATIC || scope == C4VariableScope.VAR_CONST;
	}

	public boolean isAt(int offset) {
		return offset >= getLocation().getStart() && offset <= getLocation().getEnd();
	}

	public boolean isTypeLocked() {
		return typeLocked;
	}
	
	private void ensureTypeLockedIfPredefined(C4Declaration declaration) {
		if (!typeLocked && declaration instanceof C4Engine)
			typeLocked = true;
	}
	
	@Override
	public void setParentDeclaration(C4Declaration declaration) {
		super.setParentDeclaration(declaration);
		ensureTypeLockedIfPredefined(declaration);
	}
	
	@Override
	public void postSerialize(C4Declaration parent) {
		super.postSerialize(parent);
		ensureTypeLockedIfPredefined(parent);
		if (objectID != null && parent instanceof C4ScriptBase) {
			C4ScriptBase script = (C4ScriptBase) parent;
			if (script.getResource() != null && script.getIndex() != null)
				setObjectType(script.getIndex().getObjectNearestTo(parent.getResource(), objectID));
		}
	}

	/**
	 * Returns whether this variable is an actual explicitly declared parameter and not some crazy hack thingie like '...'
	 * @return look above and feel relieved that redundancy is lifted from you
	 */
	public boolean isActualParm() {
		return !getName().equals("..."); //$NON-NLS-1$
	}
	
}
