package net.arctics.clonk.ui.editors;

import org.eclipse.jface.text.rules.IWhitespaceDetector;

public class WhitespaceDetector implements IWhitespaceDetector {
	@Override
	public boolean isWhitespace(final char c) {
		return (c == ' ' || c == '\t' || c == '\n' || c == '\r');
	}
}
