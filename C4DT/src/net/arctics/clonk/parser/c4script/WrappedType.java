package net.arctics.clonk.parser.c4script;

import net.arctics.clonk.Core;
import net.arctics.clonk.util.Utilities;

public abstract class WrappedType implements IType {
	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
	protected final IType wrappedType;
	public IType wrappedType() { return wrappedType; }
	protected WrappedType(IType type) {
		this.wrappedType = type;
	}
	public static IType unwrap(IType type) {
		return type instanceof WrappedType ? ((WrappedType)type).wrappedType : type;
	}
	@Override
	public int precision() {
		return wrappedType.precision();
	}
	@Override
	public IType staticType() {
		return wrappedType.staticType();
	}
	@Override
	public void setTypeDescription(String description) {
		wrappedType.setTypeDescription(description);
	}
	@Override
	public String toString() {
		return typeName(false);
	}
	@Override
	public boolean equals(Object obj) {
		if (obj.getClass() == this.getClass()) {
			WrappedType other = (WrappedType)obj;
			return Utilities.objectsEqual(other.wrappedType, this.wrappedType);
		} else
			return false;
	}
}