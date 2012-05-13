package net.arctics.clonk.parser.c4script.ast;

import net.arctics.clonk.Core;
import net.arctics.clonk.util.ArrayUtil;
import net.arctics.clonk.util.StringUtil;

public class Tuple extends ExprElmWithSubElementsArray {

	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;

	public Tuple(ExprElm[] elms) {
		super(elms);		
	}

	@Override
	public void doPrint(ExprWriter output, int depth) {
		StringUtil.writeBlock(output, "(", ")", ", ", ArrayUtil.iterable(elements));
	}

}