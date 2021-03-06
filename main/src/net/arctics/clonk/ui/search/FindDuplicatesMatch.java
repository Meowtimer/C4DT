package net.arctics.clonk.ui.search;

import net.arctics.clonk.c4script.Function;
import net.arctics.clonk.util.IHasLabelAndImage;
import net.arctics.clonk.util.UI;

import org.eclipse.search.ui.text.Match;
import org.eclipse.swt.graphics.Image;

public class FindDuplicatesMatch extends Match implements IHasLabelAndImage {

	private final Function original;
	private final Function dupe;
	
	public FindDuplicatesMatch(final Object element, final int offset, final int length, final Function original, final Function dupe) {
		super(element, offset, length);
		this.original = original;
		this.dupe = dupe;
	}
	
	public Function getOriginal() {
		return original;
	}

	@Override
	public String label() {
		return dupe.qualifiedName();
	}

	@Override
	public Image image() {
		return UI.DUPE_ICON;
	}

	public Function getDupe() {
		return dupe;
	}
	
	@Override
	public String toString() {
		return label();
	}

}
