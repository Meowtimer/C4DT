package net.arctics.clonk.parser.c4script.quickfix;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
	private static final String BUNDLE_NAME = "net.arctics.clonk.parser.c4script.quickfix.messages"; //$NON-NLS-1$
	public static String Fix;
	public static String MarkerResolutionDefaultMessage;
	public static String MarkerResolutionDescription;
	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
	}
}
