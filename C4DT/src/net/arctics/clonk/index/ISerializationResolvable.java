package net.arctics.clonk.index;

/**
 * Interface for objects that are given a chance to change references to it to some other equivalent object.
 * @author madeen
 *
 */
public interface ISerializationResolvable {
	/**
	 * Resolve this object to some other equivalent object, including itself.
	 * @param index {@link Index} acting as context
	 * @return An equivalent object or this one.
	 */
	Object resolve(Index index);
}
