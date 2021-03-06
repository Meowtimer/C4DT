package net.arctics.clonk.c4script.typing;


/**
 * Enum describing how strongly some {@link ITypeable} entity is expected to be of some type.
 * @author madeen
 *
 */
public enum TypingJudgementMode {
	/**
	 * Force the type of the entity.
	 */
	OVERWRITE,
	/**
	 * Unify with existing typing judgement.
	 */
	UNIFY
}