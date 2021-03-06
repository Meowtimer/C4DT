package net.arctics.clonk.ui.navigator;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
	private static final String BUNDLE_NAME = Messages.class.getPackage().getName()+".messages"; //$NON-NLS-1$
	public static String Browse;
	public static String ClonkActionProvider_QuickExport;
	public static String ClonkActionProvider_TidyUpCode;
	public static String ClonkFolderView_Browse;
	public static String ClonkFolderView_Import;
	public static String ClonkFolderView_JustAFolder;
	public static String ClonkFolderView_Link;
	public static String ClonkFolderView_LookForIDJobDescription;
	public static String ClonkFolderView_LookForIDMenuText;
	public static String ClonkFolderView_LookForIDPromptCaption;
	public static String ClonkFolderView_OpenInCurrentProject;
	public static String ClonkFolderView_Project;
	public static String ClonkFolderView_Refresh0;
	public static String ClonkFolderView_RemoveLinkedFilesOnShutdown;
	public static String ClonkPreviewView_Updater;
	public static String ColorTagging_ChooseColorColorDialogTitle;
	public static String ColorTagging_ChooseColorDialogPrompt;
	public static String ColorTagging_ChooseColorDialogTitle;
	public static String ColorTagging_ChooseColorMenuItemTitle;
	public static String ColorTagging_RemoveTag;
	public static String ConvertLinkedGroupsHandler_ConfirmationText;
	public static String ConvertLinkedGroupsHandler_ConfirmationTitle;
	public static String TidyUpCodeInBulkAction_ConvertingCode;
	public static String TidyUpCodeInBulkHandler_ReallyConvert;
	public static String OpenSpecialItemAction_Open;
	public static String QuickImportAction_SelectFiles;
	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
	}
}
