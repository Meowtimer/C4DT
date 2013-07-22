package net.arctics.clonk;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
	private static final String BUNDLE_NAME = Messages.class.getPackage().getName()+".messages"; //$NON-NLS-1$
	public static String ErrorWhileSavingIndex;
	public static String ErrorWhileSavingSettings;
	public static String HumanReadableName;
	public static String UpdateNotes_1_5_9;

	public static String TokenExpected;
	public static String NotAllowedHere;
	public static String NotAnArrayOrProplist;
	public static String NotANumber;
	public static String NotAProplist;
	public static String OperatorNeedsRightSide;
	public static String NoAssignment;
	public static String NoSideEffects;
	public static String KeywordInWrongPlace;
	public static String UndeclaredIdentifier;
	public static String OldStyleFunc;
	public static String ValueExpected;
	public static String TuplesNotAllowed;
	public static String EmptyParentheses;
	public static String ExpectedCode;
	public static String MemberOperatorWithTildeNoSpace;
	public static String MissingBrackets;
	public static String MissingDirectiveArgs;
	public static String MissingClosingBracket;
	public static String MissingExpression;
	public static String MissingFormatArg;
	public static String MissingLocalizations;
	public static String MissingStatement;
	public static String CallingExpression;
	public static String CallingMethodOnNonObject;
	public static String CAM_Arg;
	public static String CAM_Callee;
	public static String CAM_Expected;
	public static String CAM_Got;
	public static String CAM_Par;
	public static String ConcreteArgumentMismatch;
	public static String ConstantValueExpected;
	public static String CommaOrSemicolonExpected;
	public static String IdentShadowed;
	public static String IncompatibleFormatArgType;
	public static String IncompatibleTypes;
	public static String VariableCalled;
	public static String VarOutsideFunction;
	public static String TypeAsName;
	public static String TypeExpected;
	public static String BlockNotClosed;
	public static String BoolLiteralAsOpArg;
	public static String UnknownDirective;
	public static String UnknownSection;
	public static String StatementExpected;
	public static String StaticInsideFunction;
	public static String ConditionExpected;
	public static String OutOfIntRange;
	public static String InvalidExpression;
	public static String InvalidType;
	public static String NoInheritedFunction;
	public static String NonConstGlobalVarAssignment;
	public static String FloatNumbersNotSupported;
	public static String FunctionRedeclared;
	public static String FunctionRefNotAllowed;
	public static String NeverReached;
	public static String ObsoleteOperator;
	public static String OnlyRefAllopwedAsReturnType;
	public static String StringNotClosed;
	public static String StringTooLong;
	public static String UnexpectedBlock;
	public static String UnexpectedToken;
	public static String NotFinished;
	public static String NotSupported;
	public static String Garbage;
	public static String GenericError;
	public static String ConditionAlwaysTrue;
	public static String ConditionAlwaysFalse;
	public static String DeclarationNotFound;
	public static String DNF_Container;
	public static String DNF_DeclarationName;
	public static String DotNotationNotSupported;
	public static String DragonsHere;
	public static String DuplicateDeclarationName;
	public static String DuplicateDeclaration;
	public static String InternalError;
	public static String InfiniteLoop;
	public static String InheritedDisabledInStrict0;
	public static String LocalUsedInGlobal;
	public static String ExpressionExpected;
	public static String UnexpectedEnd;
	public static String Unused;
	public static String UnusedParameter;
	public static String VarUsedBeforeItsDeclaration;
	public static String NameExpected;
	public static String ReturnAsFunction;
	public static String ExpressionNotModifiable;
	public static String ParserErrorCode_Arg_ActualNumber;
	public static String ParserErrorCode_Arg_ActualType;
	public static String ParserErrorCode_Arg_Bool;
	public static String ParserErrorCode_Arg_BracketType;
	public static String ParserErrorCode_Arg_Condition;
	public static String ParserErrorCode_Arg_Declaration;
	public static String ParserErrorCode_Arg_Directive;
	public static String ParserErrorCode_Arg_DisallowedToken;
	public static String ParserErrorCode_Arg_Engine;
	public static String ParserErrorCode_Arg_ExceptionMessage;
	public static String ParserErrorCode_Arg_ExpectedNumber;
	public static String ParserErrorCode_Arg_Expression;
	public static String ParserErrorCode_Arg_Feature;
	public static String ParserErrorCode_Arg_FormatArgument;
	public static String ParserErrorCode_Arg_FormatString;
	public static String ParserErrorCode_Arg_Function;
	public static String ParserErrorCode_Arg_FunctionName;
	public static String ParserErrorCode_Arg_Garbage;
	public static String ParserErrorCode_Arg_GenericError;
	public static String ParserErrorCode_Arg_GuessedType;
	public static String ParserErrorCode_Arg_Identifier;
	public static String ParserErrorCode_Arg_Integer;
	public static String ParserErrorCode_Arg_Keyword;
	public static String ParserErrorCode_Arg_LanguageId;
	public static String ParserErrorCode_Arg_MaximumSize;
	public static String ParserErrorCode_Arg_Missing;
	public static String ParserErrorCode_Arg_NumberOfMissingBrackets;
	public static String ParserErrorCode_Arg_Operator;
	public static String ParserErrorCode_Arg_Origin;
	public static String ParserErrorCode_Arg_SectionName;
	public static String ParserErrorCode_Arg_ShadowedIdentifier;
	public static String ParserErrorCode_Arg_Size;
	public static String ParserErrorCode_Arg_Token;
	public static String ParserErrorCode_Arg_Type;
	public static String ParserErrorCode_Arg_Type1;
	public static String ParserErrorCode_Arg_Type2;
	public static String ParserErrorCode_Arg_Typename;
	public static String ParserErrorCode_Arg_Variable;
	public static String ParserErrorCode_Arg_VariableType;
	public static String ParserErrorCode_Arg_WeirdoNumber;
	public static String ParserErrorCode_Parameter_Name;
	public static String PrimitiveTypeNotSupported;
	public static String ParameterCountMismatch;
	public static String LoopVariableUsedInMultipleLoops;
	public static String LoopVariableName;
	public static String LeadsToErrors;

	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
	}
}
