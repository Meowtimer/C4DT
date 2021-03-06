package net.arctics.clonk.c4script;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
	private static final String BUNDLE_NAME = Messages.class.getPackage().getName()+".messages"; //$NON-NLS-1$
	public static String DescriptionNotAvailable;
	public static String FunctionInfoTextTemplate;
	public static String C4TypeSet_End;
	public static String C4TypeSet_Or;
	public static String C4TypeSet_Start;
	public static String C4Variable_InfoTextFormatConstValue;
	public static String C4Variable_InfoTextFormatDefaultValue;
	public static String C4Variable_InfoTextFormatOverall;
	public static String C4Variable_InfoTextFormatUserDescription;
	public static String ConstrainedProplist_Including;
	public static String ConstrainedProplist_ObjectOfCurrentType;
	public static String ConstrainedProplist_CurrentType;
	public static String ConstrainedProplist_ExactType;
	public static String ImportingEngineFromXML;
	public static String TokenStringOrIdentifier;
	public static String InternalError_WayTooMuch;
	public static String InternalParserError;
	public static String IType_ErroneousType;
	public static String Parameters;
	public static String Returns;
	public static String This_Description;
	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
	}
}
