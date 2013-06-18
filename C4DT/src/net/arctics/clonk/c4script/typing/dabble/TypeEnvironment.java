package net.arctics.clonk.c4script.typing.dabble;

import static net.arctics.clonk.c4script.typing.TypeUnification.unify;

import java.util.HashMap;
import java.util.Map;

import net.arctics.clonk.ast.Declaration;
import net.arctics.clonk.c4script.typing.TypeVariable;

@SuppressWarnings("serial")
public class TypeEnvironment extends HashMap<Declaration, TypeVariable> {
	public final TypeEnvironment up;
	public TypeEnvironment() { super(5); this.up = null; }
	public TypeEnvironment(TypeEnvironment up) { super(5); this.up = up; }
	public TypeEnvironment inject(TypeEnvironment other) {
		System.out.println("Injecting");
		for (final Map.Entry<Declaration, TypeVariable> otherInfo : other.entrySet()) {
			System.out.println(otherInfo.getValue().toString());
			final TypeVariable myVar = this.get(otherInfo.getKey());
			if (myVar != null)
				myVar.set(unify(myVar.get(), otherInfo.getValue().get()));
			else
				this.put(otherInfo.getKey(), otherInfo.getValue());
		}
		return this;
	}
	public void apply(boolean soft) {
		for (final TypeVariable info : this.values())
			info.apply(soft);
	}
	public void add(TypeVariable var) { this.put(var.key(), var); }
	public static TypeEnvironment newSynchronized() {
		return new TypeEnvironment() {
			@Override
			public synchronized TypeEnvironment inject(TypeEnvironment other) {
				return super.inject(other);
			}
			@Override
			public synchronized TypeVariable get(Object key) { return super.get(key); }
		};
	}
}