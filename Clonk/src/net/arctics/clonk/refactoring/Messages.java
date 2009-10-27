package net.arctics.clonk.refactoring;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
	private static final String BUNDLE_NAME = "net.arctics.clonk.refactoring.messages"; //$NON-NLS-1$
	public static String OutsideProject;
	public static String DuplicateItem;
	public static String Success;
	public static String RenamingProgress;
	public static String RenameChangeDescription;
	public static String RenameProcessorName;
	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
	}
}
