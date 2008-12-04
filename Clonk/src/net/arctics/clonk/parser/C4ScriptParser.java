package net.arctics.clonk.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import net.arctics.clonk.Utilities;
import net.arctics.clonk.parser.C4Directive.C4DirectiveType;
import net.arctics.clonk.parser.C4Function.C4FunctionScope;
import net.arctics.clonk.parser.C4Variable.C4VariableScope;
import net.arctics.clonk.parser.C4ScriptExprTree.*;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;

public class C4ScriptParser {
	
	public interface Keywords {

		public static final String Func = "func";

		public static final String Private = "private";
		public static final String Protected = "protected";
		public static final String Public = "public";
		public static final String Global = "global";
		public static final String Const = "const";

		public static final String If = "if";
		public static final String Else = "else";
		public static final String While = "while";
		public static final String For = "for";
		public static final String In = "in";
		public static final String Return = "return";
		public static final String Par = "Par";
		public static final String Goto = "goto";
		public static final String Break = "break";
		public static final String Continue = "continue";
		public static final String Inherited = "inherited";
		public static final String SafeInherited = "_inherited";

		public static final String Image = "Image";
		public static final String Contents = "Contents";
		public static final String Condition = "Condition";

		public static final String GlobalNamed = "static";
		public static final String LocalNamed = "local";
		public static final String VarNamed = "var";

		public static final String True = "true";
		public static final String False = "false";
	}
	
	protected static class BufferedScanner {

		public static final char[] WHITESPACE_DELIMITERS = new char[] { ' ', '\n', '\r', '\t' };
		public static final char[] NEWLINE_DELIMITERS = new char[] { '\n', '\r' };

		private String buffer;
		private int size;
		private InputStream contents;
		private int offset;

		private static String stringFromInputStream(InputStream stream) throws IOException {
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream));
			StringBuilder stringBuilder;
			try {
				stringBuilder = new StringBuilder();
				char[] buffer = new char[1024];
				int read;
				while ((read = bufferedReader.read(buffer)) > 0) {
					stringBuilder.append(buffer, 0, read);
				}

			} finally {
				bufferedReader.close();
			}
			return stringBuilder.toString();
		}
		
		public BufferedScanner(IFile file) throws CompilerException {
			try {
				contents = file.getContents();
				offset = 0;
				buffer = stringFromInputStream(contents);
				size = buffer.length();
			} catch (CoreException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		public BufferedScanner(InputStream stream, long fileSize) throws CompilerException {
			try {
				contents = stream;
				offset = 0;
				buffer = stringFromInputStream(stream);
				size = buffer.length();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		public BufferedScanner(String withString) {
			contents = null;
			offset = 0;
			buffer = withString;
			size = buffer.length();
		}

		public int read() {
			if (offset >= size) {
				offset++; // increment anyway so unread works as expected
				return -1;
			}
			return buffer.charAt(offset++);
		}

		public void unread() {
			offset--;
		}

		public String readString(int length) {
			if (offset+length > size) 
				return null;
			String result = buffer.substring(offset, offset+length);
			offset += length;
			return result;
		}

		public static boolean isWordPart(int character) {
			return ('A' <= character && character <= 'Z') ||
			('a'<= character && character <= 'z') ||
			(character == '_') ||
			(/*length > 0 &&*/ '0' <= character && character <= '9');
		}
		
		/**
		 * Reads a code-word. (like regexp class [0-9a-zA-Z_])
		 * @return the code-word
		 */
		public String readWord() {
			int start = offset;
			int length = 0;
			do {
				int readByte = read();
				if (isWordPart(readByte)) {
					length++;
				}
				else {
					seek(start);
					return readString(length);
				}
			} while(!reachedEOF());
			return readStringAt(start, start+length);
		}

		/**
		 * Reads a string until a char from <code>delimiters</code> occurs
		 * @param delimiters
		 * @return string sequence, without delimiter char
		 */
		public String readStringUntil(char ...delimiters) {
			int start = offset;
			int i = 0;
			do {
				int readByte = read();
				for(i = 0; i < delimiters.length;i++) {
					if (readByte == delimiters[i]) {
						i = offset - start - 1; // variable reuse
						seek(start);
						return readString(i);
					}
				}
			} while(!reachedEOF());
			return null;
		}

		/**
		 * Moves offset until a char from <code>delimiters</code> occurs
		 * @param delimiters
		 */
		public void moveUntil(char[] delimiters) {
			do {
				int readByte = read();
				for(int i = 0; i < delimiters.length;i++) {
					if (readByte == delimiters[i]) {
						return;
					}
				}
			} while(!reachedEOF());
		}

		/**
		 * Moves offset until any other char than <code>delimiters</code> occurs
		 * @param delimiters
		 */
		public int eat(char[] delimiters) {
			if (reachedEOF())
				return 0; // no unreading() when already reached EOF
			int result = 0;
			do {
				int readByte = read();
				boolean isDelimiter = true;
				for(int i = 0; i < delimiters.length;i++) {
					if (readByte != delimiters[i]) {
						isDelimiter = false;
					}
					else {
						isDelimiter = true;
						result++;
						break;
					}
				}
				if (!isDelimiter) {
					unread();
					return result;
				}
			} while(!reachedEOF());
			return result;
		}

		public int eatWhitespace() {
			return eat(WHITESPACE_DELIMITERS);
		}

		/**
		 * Absolute offset manipulation
		 * @param newPos
		 * @return new offset
		 */
		public int seek(int newPos) {
			offset = newPos;
			//if (offset >= size) offset = size - 1;
			return offset;
		}

		/**
		 * Relative offset manipulation
		 * @param distance
		 * @return new offset
		 */
		public int move(int distance) {
			offset += distance;
			if (offset >= size) offset = size - 1;
			return offset;
		}

		/**
		 * If end of file reached
		 * @return whether eof reached
		 */
		public boolean reachedEOF() {
			return (offset >= size);
		}

		/**
		 * Current offset
		 * @return offset
		 */
		public int getPosition() {
			return offset;
		}

		public String readStringAt(int start, int end) {
			int p = getPosition();
			seek(start);
			String result = readString(end-start);
			seek(p);
			return result;
		}
	}

	public static class ParsingException extends Exception {

		private static final long serialVersionUID = 5596886615974079864L;

		public ParsingException(String msg) {
			super(msg);
		}
	}
	
	private IExpressionListener expressionListener;

	public IExpressionListener getExpressionListener() {
		return expressionListener;
	}

	public void setExpressionListener(IExpressionListener expressionListener) {
		this.expressionListener = expressionListener;
	}

	private BufferedScanner fReader;
	private IFile fScript; // for project intern files
	private C4ScriptBase container;
	private InputStream stream; // for extern files

	//	private List<C4Directive> directives = new LinkedList<C4Directive>();
	//	private List<C4Function> functions = new LinkedList<C4Function>();
	//	private List<C4Variable> variables = new LinkedList<C4Variable>();

	private C4Function activeFunc;
	private int strictLevel;
	
	// parse<Blub>() functions store their results in those
	private C4ID parsedID;
	private C4Variable parsedVariable;
	private long parsedNumber;
	private String parsedObjectFieldOperator;
	private String parsedString;
	
	private int parseExpressionRecursion;
	private int parseStatementRecursion;
	
	public static final int MAX_PAR = 10;
	public static final int UNKNOWN_PARAMETERNUM = MAX_PAR+1;
	
	/*
	 * Number of unnamed parameters used in activeFunc (Par(5) -> 6 unnamed parameters)
	 * If variable is passed to Par() this variable is set to UNKNOWN_PARAMETERNUM
	 */
	private int numUnnamedParameters;

	public void unnamedParamaterUsed(ExprElm index) {
		if (numUnnamedParameters < UNKNOWN_PARAMETERNUM) {
			if (index instanceof ExprNumber) {
				int number = ((ExprNumber)index).intValue();
				numUnnamedParameters = Math.max(number+1, numUnnamedParameters);
			} else
				numUnnamedParameters = UNKNOWN_PARAMETERNUM;
		}
	}
	
	public int getStrictLevel() {
		return strictLevel;
	}
	
	public C4Function getActiveFunc() {
		return activeFunc;
	}
	
	public void setActiveFunc(C4Function func) {
		activeFunc = func;
	}
	
	public C4ScriptBase getContainer() {
		return container;
	}
	
	public C4Object getContainerObject() {
		if (container instanceof C4Object)
			return (C4Object) container;
		return null;
	}

	/**
	 * Creates a C4Script parser object.
	 * Results are stored in <code>object</code>
	 * @param scriptFile
	 * @param obj
	 * @throws CompilerException
	 */
	public C4ScriptParser(IFile scriptFile, C4ScriptBase script) throws CompilerException {
		fScript = scriptFile;
		fReader = new BufferedScanner(fScript);
		container = script;
	}

	/**
	 * Creates a C4Script parser object for extern files.
	 * Results are stored in <code>object</code>
	 * @param stream
	 * @param size
	 * @param object
	 * @throws CompilerException
	 */
	public C4ScriptParser(InputStream stream, long size, C4ScriptBase script) throws CompilerException {
		fScript = null;
		this.stream = stream;
		fReader = new BufferedScanner(this.stream, size);
		container = script;
	}
	
	public C4ScriptParser(String withString, C4ScriptBase script) {
		fScript = null;
		stream = null;
		fReader = new BufferedScanner(withString);
		container = script;
	}
	
	// TODO: in clean build, have two passes: 1. parse all declarations 2. check function code (so that all static variables/global functions and included stuff can be found)
	public void parse() {
		clean();
		parseDeclarations();
		parseCodeOfFunctions();
	}
	
	/**
	 * Parses in two steps without a clean()
	 */
	public void parseAdditional() {
		parseDeclarations();
		parseCodeOfFunctions();
	}

	public void parseDeclarations() {
		int offset = 0;
		fReader.seek(offset);
		try {
			eatWhitespace();
			while(!fReader.reachedEOF()) {
				if (!parseDeclaration(fReader.getPosition())) return;
				eatWhitespace();
			}
		}
		catch (ParsingException e) {
			return;
		}
	}
	
	public void parseCodeOfFunctions() {
		strictLevel = container.strictLevel();
		for (C4Function function : container.definedFunctions) {
			parseCodeOfFunction(function);
		}
	}

	private void parseCodeOfFunction(C4Function function) {
		activeFunc = function;
		numUnnamedParameters = 0;
		try {
			parseCodeBlock(function.getBody().getStart());
			if (numUnnamedParameters < UNKNOWN_PARAMETERNUM) {
				activeFunc.createParameters(numUnnamedParameters);
			}
		} catch (ParsingException e) {
			System.out.println(String.format("ParsingException in %s (%s)", activeFunc.getName(), container.getName()));
			e.printStackTrace();
			// not very exceptional
		} catch (Exception e) {
			// errorWithCode throws ^^;
			warningWithCode(ErrorCode.InternalError, fReader.getPosition(), fReader.getPosition()+1, e.getMessage());
			e.printStackTrace();
		}
	}

	protected boolean parseDeclaration(int offset) throws ParsingException {
		fReader.seek(offset);
		int readByte = fReader.read();
		if (readByte == '#') {
			// ok, now is a directive
			String directiveName = fReader.readStringUntil(BufferedScanner.WHITESPACE_DELIMITERS);
			C4DirectiveType type = C4DirectiveType.makeType(directiveName);
			if (type == null) {
				warningWithCode(ErrorCode.UnknownDirective, offset, offset + directiveName.length());
				fReader.moveUntil(BufferedScanner.NEWLINE_DELIMITERS);
				return true;
			}
			else {
				String content = fReader.readStringUntil(BufferedScanner.NEWLINE_DELIMITERS);
				if (content != null)
					content = content.trim();
				container.definedDirectives.add(new C4Directive(type, content));
				return true;
			}
		}
		else {
			fReader.seek(offset);
			String word = fReader.readWord();
			if (looksLikeStartOfFunction(word)) {
				if (parseFunctionDeclaration(word, offset, fReader.getPosition())) return true;
			}
			else if (word.equals(Keywords.GlobalNamed) || word.equals(Keywords.LocalNamed)) {
				if (parseVariableDeclaration(offset)) return true;
			}
			else {
				// old-style function declaration without visibility
				eatWhitespace();
				if (fReader.read() == ':') {
					fReader.seek(offset); // just let parseFunctionDeclaration parse the name again
					if (parseFunctionDeclaration(Keywords.Public, offset, offset)) // just assume public
						return true;
				}
			}
		}
		return false;
	}

	private boolean looksLikeStartOfFunction(String word) {
		return word.equalsIgnoreCase(Keywords.Public) || word.equalsIgnoreCase(Keywords.Protected) || word.equalsIgnoreCase(Keywords.Private) || word.equalsIgnoreCase(Keywords.Global) || word.equals(Keywords.Func);
	}

	private boolean parseVariableDeclaration(int offset) throws ParsingException {
		fReader.seek(offset);

		String word = fReader.readWord();
		if (word.equals(Keywords.GlobalNamed)) {
			eatWhitespace();
			int pos = fReader.getPosition();
			boolean constDecl = false; 
			if (fReader.readWord().equals(Keywords.Const)) {
				constDecl = true;
			} else {
				fReader.seek(pos);
			}
			do {
				eatWhitespace();
				int s = fReader.getPosition();
				String varName = fReader.readWord();
				int e = fReader.getPosition();
				if (constDecl) {
					eatWhitespace();
					if (fReader.read() != '=') {
						tokenExpectedError("=");
					}
					eatWhitespace();
					offset = fReader.getPosition();
					ExprElm constantValue = parseExpression(offset, false);
					if (constantValue == null)
						constantValue = ERROR_PLACEHOLDER_EXPR;
					if (!(constantValue instanceof ExprLiteral)) {
						errorWithCode(ErrorCode.ConstantValueExpected, constantValue, true);
					}
					C4Variable var = new C4Variable(varName,C4VariableScope.VAR_CONST);
					var.setLocation(new SourceLocation(s, e));
					var.setScript(container);
					var.inferTypeFromAssignment(constantValue, this);
					container.addField(var);
				}
				else {
					C4Variable var = new C4Variable(varName, C4VariableScope.VAR_STATIC);
					var.setLocation(new SourceLocation(s, e));
					var.setScript(container);
					container.addField(var);
				}
				eatWhitespace();
			} while(fReader.read() == ',');
			fReader.unread();
			if (fReader.read() != ';') {
				errorWithCode(ErrorCode.CommaOrSemicolonExpected, fReader.getPosition()-1, fReader.getPosition());
			}
			return true;
		}
		else if (word.equals(Keywords.LocalNamed)) {
			do {
				eatWhitespace();
				int s = fReader.getPosition();
				String varName = fReader.readWord();
				int e = fReader.getPosition();
				C4Variable v = new C4Variable(varName, C4VariableScope.VAR_LOCAL);
				v.setLocation(new SourceLocation(s, e));
				v.setScript(container);
				container.addField(v);
				eatWhitespace();
			} while (fReader.read() == ',');
			fReader.unread();
			if (fReader.read() != ';') {
				errorWithCode(ErrorCode.CommaOrSemicolonExpected, fReader.getPosition()-1, fReader.getPosition());
			}
			return true;
		}
		// local iVar, iX;
		// static const pObj = parseValue, iMat = 2;
		return false;
	}

	private boolean parseVarVariableDeclaration(int offset, boolean declaration) throws ParsingException {
		parsedVariable = null;
		fReader.seek(offset);

		String word = fReader.readWord();
		if (word == null)
			return false;
		if (word.equals(Keywords.VarNamed)) {
			do {
				eatWhitespace();
				int nameStart = fReader.getPosition();
				String varName = fReader.readWord();
				int nameEnd = fReader.getPosition();
				if (declaration) {
					// construct C4Variable object and register it
					C4Variable previousDeclaration = activeFunc.findVariable(varName); 
					C4Variable var = previousDeclaration != null ? previousDeclaration : new C4Variable(varName,C4VariableScope.VAR_VAR);
					var.setParentField(activeFunc);
					var.setLocation(new SourceLocation(nameStart, nameEnd));
					if (previousDeclaration == null)
						activeFunc.getLocalVars().add(var);
					parsedVariable = var;
				}
				// check if there is initial content
				eatWhitespace();
				C4Variable var = activeFunc.findVariable(varName);
				parsedVariable = var;
				if (fReader.read() == '=') {
					eatWhitespace();
					offset = fReader.getPosition();
					ExprElm val = parseExpression(offset, !declaration);
					if (!declaration) {
						if (val == null)
							errorWithCode(ErrorCode.ValueExpected, fReader.getPosition()-1, fReader.getPosition());
						else {
							var.inferTypeFromAssignment(val, this);
						}
					}
				}
				else {
					fReader.unread();
				}

			} while(fReader.read() == ',');
			fReader.unread();
			//			if (fReader.read() != ';') {
			//				String problem = "Syntax error: expected ';' or ','";
			//				createErrorMarker(fReader.getPosition() - 1, fReader.getPosition(), problem);
			//				throw new ParsingException(problem);
			//			}
			return true;
		}
		else {
			fReader.seek(offset);
			return false;
		}
	}

	@SuppressWarnings("unused")
	private boolean parseFunctionDeclaration(int offset) throws ParsingException {
		fReader.seek(offset);
		return parseFunctionDeclaration(fReader.readWord(), offset, fReader.getPosition());
	}
	
	private C4Type parseFunctionReturnType(int offset) {
		fReader.seek(offset);
		eatWhitespace();
		int readByte = fReader.read();
		if (readByte == '&') {
			return C4Type.REFERENCE;
		}
		fReader.seek(offset);
		return C4Type.ANY;
	}
	
	private int consumeFunctionCodeOrReturnReadChar(int offset) throws ParsingException {
		eatWhitespace();
		offset = fReader.getPosition();
		if (parseVarVariableDeclaration(offset, true))
			return 0;
		if (parseToken(offset) != null)
			return 0;
		return fReader.read();
	}

	/**
	 * for optimization reasons
	 * @param firstWord
	 * @param offset
	 * @return
	 * @throws ParsingException 
	 */
	private boolean parseFunctionDeclaration(String firstWord, int startOfHeader, int offset) throws ParsingException {
		int endOfHeader;
		fReader.seek(offset);
		eatWhitespace();
		activeFunc = new C4Function();
		activeFunc.setScript(container);
		int startName = 0, endName = 0, startBody = 0, endBody = 0;
		boolean suspectOldStyle = false;
		String funcName = null;
		C4Type retType = C4Type.ANY;
		if (!firstWord.equals(Keywords.Func)) {
			activeFunc.setVisibility(C4FunctionScope.makeScope(firstWord));
			startName = fReader.getPosition();
			String shouldBeFunc = fReader.readWord();
			if (!shouldBeFunc.equals(Keywords.Func)) {
//				String problem = "Syntax error: expected 'func'";
//				createErrorMarker(offset, fReader.getPosition(), problem);
//				throw new ParsingException(problem);
				suspectOldStyle = true; // suspicious
				funcName = shouldBeFunc;
				endName = fReader.getPosition();
				warningWithCode(ErrorCode.OldStyleFunc, startName, endName);
			}
		}
		else {
			activeFunc.setVisibility(C4FunctionScope.FUNC_PUBLIC);
			// well, so common it can hardly be worth a warning
			//createWarningMarker(offset - firstWord.length(), offset, "Function declarations should define a scope. (public,protected,private,global)");
		}
		if (!suspectOldStyle) {
			retType = parseFunctionReturnType(fReader.getPosition());
			eatWhitespace();
			startName = fReader.getPosition();
			funcName = fReader.readWord();
			if (funcName == null || funcName.length() == 0)
				errorWithCode(ErrorCode.NameExpected, fReader.getPosition()-1, fReader.getPosition());
			endName = fReader.getPosition();
		}
		for(C4Function otherFunc : container.definedFunctions) {
			if (otherFunc.getName().equalsIgnoreCase(funcName)) {
				warningWithCode(ErrorCode.FunctionRedeclared, startName, fReader.getPosition());
				break;
			}
		}
		activeFunc.setName(funcName);
		activeFunc.setReturnType(retType);
		activeFunc.setOldStyle(suspectOldStyle);
		eatWhitespace();
		int shouldBeBracket = fReader.read();
		if (shouldBeBracket != '(') {
			if (suspectOldStyle && shouldBeBracket == ':') {
				// old style funcs have no named parameters
			} else {
				tokenExpectedError("(");
			}
		} else {
			// get parameter
			do {
				eatWhitespace();
				offset = fReader.getPosition();
				if (parseParameter(offset, activeFunc)) offset = fReader.getPosition(); 
				eatWhitespace(offset);
				int readByte = fReader.read();
				if (readByte == ')') break; // all parameter parsed
				else if (readByte == ',') continue; // parse another parameter
				else {
					errorWithCode(ErrorCode.TokenExpected, fReader.getPosition()-1, fReader.getPosition(), (Object) new String[] {")", ","});
				}
			} while(!fReader.reachedEOF());
		}
		endOfHeader = fReader.getPosition();
		eatWhitespace();
		// parse code block
		if (fReader.read() != '{') {
			if (suspectOldStyle) {
				fReader.unread();
				parseFunctionDescription(fReader.getPosition());
				startBody = fReader.getPosition();
				// body goes from here to start of next function...
				do {
					eatWhitespace();
					endBody = fReader.getPosition();
					String word = fReader.readWord();
					if (word != null && word.length() > 0) {
						if (looksLikeStartOfFunction(word) || looksLikeVarDeclaration(word)) {
							fReader.seek(endBody);
							break;
						} else {
							eatWhitespace();
							if (fReader.read() == ':') {
								fReader.seek(endBody);
								break;
							} else {
								fReader.seek(endBody);
							}
						}
					}
					// just move on
					consumeFunctionCodeOrReturnReadChar(fReader.getPosition());

					endBody = fReader.getPosition(); // blub
				} while (!fReader.reachedEOF());
			} else {
				errorWithCode(ErrorCode.TokenExpected, fReader.getPosition()-1, fReader.getPosition(), "{");
			}
		} else {
			// body in {...}
			blockDepth = 0;
			eatWhitespace();
			offset = fReader.getPosition();
			if (parseFunctionDescription(offset)) offset = fReader.getPosition();
			startBody = offset;

			// new two pass strategy to be able to check if functions and variables exist
			// first pass: skip the code, just remember where it is
			boolean foundLast;
			do {
				int c = consumeFunctionCodeOrReturnReadChar(fReader.getPosition());
				if (c == '}')
					blockDepth--;
				else if (c == '{')
					blockDepth++;
				foundLast = blockDepth == -1;
			} while (!(foundLast || fReader.reachedEOF()));
			if (foundLast)
				fReader.unread(); // go back to last '}'

			endBody = fReader.getPosition();
			eatWhitespace();
			if (fReader.read() != '}') {
//				System.out.println(activeFunc.getName());
				int pos = Math.min(fReader.getPosition()-1, fReader.size-2);
				errorWithCode(ErrorCode.TokenExpected, fReader.getPosition()-1, pos, pos+1);
				return false;
			}
		}
		// finish up
		activeFunc.setLocation(new SourceLocation(startName,endName));
		activeFunc.setBody(new SourceLocation(startBody,endBody));
		activeFunc.setHeader(new SourceLocation(startOfHeader, endOfHeader));
		container.addField(activeFunc);
		//		functions.add(func);
		return true;
	}

	private boolean looksLikeVarDeclaration(String word) {
		return word.equals(Keywords.GlobalNamed) || word.equals(Keywords.LocalNamed);
	}

	/**
	 * Parses one command
	 * i.e. if (condition) <code>parseCode</code>
	 * @param offset
	 * @return
	 * @throws ParsingException 
	 */
	public boolean parseCode(int offset) throws ParsingException {
		return parseStatement(offset) != null;
//		fReader.seek(offset);
//		this.eatWhitespace();
//		offset = fReader.getPosition();
//		try {
//			int readByte = fReader.read();
//			if (readByte == ';') {
//				// empty statement
//				return true;
//			}
//			fReader.unread();
//			if (parseKeyword(offset)) return true;
//			if (parseReturn(offset) || parseVarVariableDeclaration(offset, false) || parseStandaloneExpression(offset)) {
//				checkForSemicolon();
//				return true;
//			}
//			else {
//				return false;
//			}
//		} catch (Exception e) {
//			// something exceptional happened
//			if (!(e instanceof ParsingException)) {
//				e.printStackTrace();
//				errorWithCode(ErrorCode.InternalError, offset, fReader.getPosition(), e.toString());
//			} else
//				throw (ParsingException)e;
//			return false;
//		}
	}

	/**
	 * Parses all commands
	 * For use in keyword() { <code>parseCodeBlock<code> }
	 * @param offset
	 * @return
	 * @throws ParsingException 
	 */
	private boolean parseCodeBlock(int offset) throws ParsingException {
		fReader.seek(offset);
		while(!fReader.reachedEOF() && fReader.getPosition() < activeFunc.getBody().getEnd() && parseStatement(fReader.getPosition()) != null) {
			eatWhitespace();
		}
		// a complete code block without reading { }
		// function call
		// zuweisung
		// post-prefix
		return true;
	}

	private int blockDepth;
	
	/**
	 * Parses { parseCodeBlock } or parseCode
	 * @param offset
	 * @return
	 * @throws ParsingException 
	 */
	protected boolean parseCodeSegment(int offset) throws ParsingException {
		int c = fReader.read();
		if (c == '{') { // if has block
			blockDepth++;
			eatWhitespace();
			offset = fReader.getPosition();
			if (!parseCodeBlock(fReader.getPosition())) fReader.seek(offset);
			eatWhitespace();
			if (fReader.read() != '}') {
				errorWithCode(ErrorCode.BlockNotClosed, fReader.getPosition()-1, fReader.getPosition());
			}
			blockDepth--;
			return true;
		} else if (c == ';') {
			return true; // empty statement
		} else {
			eatWhitespace();
			if (parseCode(offset)) return true;
			else return false;
		}
	}

//	private boolean parseAssignment(int offset) throws ParsingException {
//		fReader.seek(offset);
//		if (!parseCall(offset)) {
//			fReader.seek(offset);
//			String varName = fReader.readWord();
//			if (varName.length() == 0) {
//				//				fReader.seek(offset);
//				return false;
//			}
//		}
//		eatWhitespace();
//
//		int readByte = fReader.read();
//		int secondByte = fReader.read();
//		if (readByte != '=') {
//
//			if (secondByte == '=' && ((0x2A <= readByte && readByte <= 0x2F && readByte != 0x2C && readByte != 0x2E) || readByte == '%')) {
//
//			}
//			else {
//				fReader.unread();
//				fReader.unread();
//				return false;
//			}
//		}
//		else {
//
//		}
//		eatWhitespace();
//		offset = fReader.getPosition();
//		if (!parseValue(fReader.getPosition())) {
//			String problem = "Syntax error: expected a value after '='";
//			createErrorMarker(offset, offset + 3, problem);
//			throw new ParsingException(problem);
//		}
//		// *= /= %= += -= =
//		return true;
//	}

	private boolean parseReturn(int offset) throws ParsingException {
		fReader.seek(offset);
		String word = fReader.readWord();
		if (word == null || !word.equals(Keywords.Return)) {
			fReader.seek(offset);
			return false;
		}
		eatWhitespace();
		int next = fReader.read();
		if (next == ';') {
			fReader.unread();
		}
		else {
			fReader.unread();
			offset = fReader.getPosition();
			disableError(ErrorCode.TuplesNotAllowed);
			disableError(ErrorCode.EmptyParentheses);
			ExprElm returnExpr = parseExpression(fReader.getPosition());
			if (returnExpr == null) {
				errorWithCode(ErrorCode.ValueExpected, fReader.getPosition() - 1, fReader.getPosition());				
			}
			warnAboutTupleInReturnExpr(returnExpr, false);
			enableError(ErrorCode.TuplesNotAllowed);
			enableError(ErrorCode.EmptyParentheses);
		}
		return true;
	}

	private void warnAboutTupleInReturnExpr(ExprElm expr, boolean tupleIsError) throws ParsingException {
		if (expr == null)
			return;
		if (expr instanceof ExprTuple) {
			if (tupleIsError)
				errorWithCode(ErrorCode.TuplesNotAllowed, expr);
			else
				warningWithCode(ErrorCode.ReturnAsFunction, expr);
		}
		ExprElm[] subElms = expr.getSubElements();
		for (ExprElm e : subElms) {
			warnAboutTupleInReturnExpr(e, true);
		}
	}
	
	private boolean parseStandaloneExpression(int offset) throws ParsingException {
		fReader.seek(offset);
		ExprElm elm = parseExpression(offset);
		if (elm != null) {
			if (elm instanceof ExprBinaryOp) {
				((ExprBinaryOp)elm).checkTopLevelAssignment(this);
			} else
				elm.warnIfNoSideEffects(this);
			return true;
		}
		return false;
	}

	private boolean parseValue(int offset) throws ParsingException {
		ExprElm elm = parseExpression(offset);
		return elm != null;
	}

	private boolean parseNumber(int offset) throws ParsingException {
		fReader.seek(offset);
		int count = 0;
		do {
			int readByte = fReader.read();
			if ('0' <= readByte && readByte <= '9') {
				count++;
				continue;
			}
			else {
				fReader.unread();
				if (count > 0) {
					fReader.seek(offset);
					parsedNumber = Long.parseLong(fReader.readString(count));
					fReader.seek(offset+count);
				} else {
					parsedNumber = -1; // unlikely to be parsed
					return false; // well, this seems not to be a number at all
				} 
				return true;
			}
		} while(!fReader.reachedEOF());
		return true; // TODO correct number finish?
	}
	
	private boolean parseEllipsis(int offset) {
		fReader.seek(offset);
		String e = fReader.readString(3);
		if (e != null && e.equals("..."))
			return true;
		fReader.seek(offset);
		return false;
	}
	
	private boolean parseObjectFieldOperator(int offset) {
		fReader.seek(offset);
		parsedObjectFieldOperator = fReader.readString(2);
		if (parsedObjectFieldOperator == null) {
			return false;
		}
		if (parsedObjectFieldOperator.equals("->")) {
			offset = fReader.getPosition();
			eatWhitespace();
			if (fReader.read() == '~')
				parsedObjectFieldOperator = parsedObjectFieldOperator + "~";
			else
				fReader.seek(offset);
			return true;
		}
		fReader.seek(offset);
		return false;
	}

	/**
	 * loop types
	 */
	public enum LoopType {
		For,
		IterateArray, // for (x in y)
		While
	}

	public enum Token {
		String,
		Word,
		ID,
		Number
	}
	
	public Token parseToken(int offset) throws ParsingException {
		fReader.seek(offset);
		if (parseString(offset))
			return Token.String;
		if (fReader.readWord().length() > 0)
			return Token.Word;
		if (parseID(offset))
			return Token.ID;
		if (parseNumber(offset))
			return Token.Number;
		return null;
	}

	/**
	 * read operator at some location
	 * @param offset
	 * @return the operator referenced in the code at offset
	 */
	private C4ScriptOperator parseOperator_(int offset) {
		fReader.seek(offset);

		final char[] chars = new char[] { (char)fReader.read(), (char)fReader.read()  };
		String s = new String(chars);
		
		// never to be read as an operator
		if (s.equals("->")) {
			fReader.seek(offset);
			return null;
		}

		C4ScriptOperator result = C4ScriptOperator.stringToOperatorMap.get(s);
		if (result != null) {
			// new_variable should not be parsed as ne w_variable -.-
			if (result == C4ScriptOperator.ne || result == C4ScriptOperator.eq) {
				int followingChar = fReader.read();
				if (BufferedScanner.isWordPart(followingChar)) {
					fReader.seek(offset);
					return null;
				} else
					fReader.unread();
			}
			return result;
		}

		s = s.substring(0, 1);
		result = C4ScriptOperator.stringToOperatorMap.get(s);
		if (result != null) {
			fReader.unread();
			return result;
		}

		fReader.seek(offset);
		return null;
	}
	
	public enum ErrorCode {
		TokenExpected, NotAllowedHere, MissingClosingBracket, InvalidExpression, InternalError, ExpressionExpected, UnexpectedEnd, NameExpected, ReturnAsFunction, ExpressionNotModifiable, OperatorNeedsRightSide, NoAssignment, NoSideEffects, KeywordInWrongPlace, UndeclaredIdentifier, OldStyleFunc, ValueExpected, TuplesNotAllowed, EmptyParentheses, ExpectedCode, ConstantValueExpected, CommaOrSemicolonExpected, IncompatibleTypes, VariableCalled, TypeAsName, BlockNotClosed, UnknownDirective, StatementExpected, ConditionExpected, OutOfIntRange, NoInheritedFunction, FunctionRedeclared
	}
	
	private static String[] errorStrings = new String[] {
		"'%s' expected",
		"'%s' not allowed here",
		"Missing '%s'",
		"Invalid expression",
		"Internal error: %s",
		"Expression expected",
		"Unexpected end of script",
		"Name expected",
		"return should not be treated as function",
		"Expression cannot be modified",
		"Operator has no right side",
		"There is no toplevel-assignment in this expression",
		"Expression has no side effects",
		"Keyword '%s' misplaced",
		"Undeclared identifier '%s'",
		"Old-style function",
		"Value expected",
		"Tuples not allowed here",
		"Empty parentheses",
		"Code expected",
		"Constant value expected",
		"Comma or semicolon expected",
		"Incompatible types: %s and %s",
		"Variable %s called as function",
		"Typename as name: %s",
		"Code block not closed with '}'",
		"Unknown directive",
		"Statement expected",
		"Condition expected",
		"Out of integer range: %s",
		"No inherited version of %s found",
		"Function overload: this function is already declared in this script"
	};
	
	private static Set<ErrorCode> disabledErrors = new HashSet<ErrorCode>();
	
	private static void disableError(ErrorCode error) {
		disabledErrors.add(error);
	}
	
	private static void enableError(ErrorCode error) {
		disabledErrors.remove(error);
	}
	
	public static boolean errorDisabled(ErrorCode error) {
		return disabledErrors.contains(error);
	}
	
	void warningWithCode(ErrorCode code, int errorStart, int errorEnd, Object... args) {
		String problem = String.format(errorStrings[code.ordinal()], args);
		createWarningMarker(errorStart, errorEnd, problem);
	}
	
	void warningWithCode(ErrorCode code, IRegion errorRegion, Object... args) {
		warningWithCode(code, errorRegion.getOffset(), errorRegion.getOffset()+errorRegion.getLength(), args);
	}
	
	void errorWithCode(ErrorCode code, IRegion errorRegion, Object... args) throws ParsingException {
		errorWithCode(code, errorRegion, false, args);
	}
	
	void errorWithCode(ErrorCode code, IRegion errorRegion, boolean noThrow, Object... args) throws ParsingException {
		errorWithCode(code, errorRegion.getOffset(), errorRegion.getOffset()+errorRegion.getLength(), noThrow, args);
	}
	
	void errorWithCode(ErrorCode code, int errorStart, int errorEnd, boolean noThrow, Object... args) throws ParsingException {
//		if (fScript == null)
//			return; // parser used for other purposes -> no errors
		if (errorDisabled(code))
			return;
		String problem = String.format(errorStrings[code.ordinal()], args);
		createErrorMarker(errorStart, errorEnd, problem);
		if (!noThrow)
			throw new ParsingException(problem);
	}
	
	private void errorWithCode(ErrorCode code, int errorStart, int errorEnd, Object... args) throws ParsingException {
		errorWithCode(code, errorStart, errorEnd, false, args);
	}
	
	private void tokenExpectedError(String token) throws ParsingException {
		errorWithCode(ErrorCode.TokenExpected, fReader.getPosition()-1, fReader.getPosition(), false, token);
	}
	
	private boolean parseStaticFieldOperator_(int offset) {
		fReader.seek(offset);
		String o = fReader.readString(2);
		if (o.equals("::"))
			return true;
		fReader.seek(offset);
		return false;
	}
	
	public ExprElm parseExpressionWithoutOperators(int offset, boolean reportErrors) throws ParsingException {
		fReader.seek(offset);
		this.eatWhitespace();
		int sequenceStart = fReader.getPosition();
		C4ScriptOperator preop = parseOperator_(fReader.offset);
		ExprElm result = null;
		if (preop != null && preop.isPrefix()) {
			ExprElm followingExpr = parseExpressionWithoutOperators(fReader.offset, reportErrors);
			if (followingExpr == null) {
				errorWithCode(ErrorCode.ExpressionExpected, fReader.getPosition(), fReader.getPosition()+1);
			}
			result = new ExprUnaryOp(preop, ExprUnaryOp.Placement.Prefix, followingExpr);
		} else
			fReader.seek(sequenceStart); // don't skip operators that aren't prefixy
		if (result != null) {
			result.setExprRegion(sequenceStart, fReader.getPosition());
			return result;
		}
		Vector<ExprElm> elements = new Vector<ExprElm>(5);
		ExprElm elm;
		ExprElm prevElm = null;
		boolean dontCheckForPostOp = false;
		int noWhitespaceEating = sequenceStart;
		do {
			elm = null;
			
			noWhitespaceEating = fReader.getPosition();
			this.eatWhitespace();
			int elmStart = fReader.getPosition();

			// operator always ends a sequence without operators
			if (parseOperator_(fReader.getPosition()) != null) {
				fReader.seek(elmStart);
				break;
			}
			
			// id
			if (parseID(fReader.offset)) {
				elm = new ExprID(parsedID);
			}
			
			// number
			if (elm == null && parseNumber(fReader.offset)) {
				if (parsedNumber < Integer.MIN_VALUE || parsedNumber > Integer.MAX_VALUE)
					warningWithCode(ErrorCode.OutOfIntRange, elmStart, fReader.getPosition(), String.valueOf(parsedNumber));
				elm = new ExprNumber(parsedNumber);
			}
			
			// string
			if (elm == null && parseString(fReader.getPosition())) {
				elm = new ExprString(parsedString);
			}
			
			// variable or function
			if (elm == null) {
				String word = fReader.readWord();
				if (word != null && word.length() > 0) {
					int beforeSpace = fReader.getPosition();
					this.eatWhitespace();
					if (fReader.read() == '(') {
						// function call
						List<ExprElm> args = new LinkedList<ExprElm>();
						parseRestOfTuple(fReader.getPosition(), args, reportErrors);
						elm = new ExprCallFunc(word, args.toArray(new ExprElm[0]));
					} else {
						fReader.seek(beforeSpace);
						// bool
						if (word.equals(Keywords.True))
							elm = new ExprBool(true);
						else if (word.equals(Keywords.False))
							elm = new ExprBool(false);
						else
							// variable
							elm = new ExprAccessVar(word);
					}
				}
			}
			
			// array
			if (elm == null) {
				int c = fReader.read();
				if (c == '[') {
					if (prevElm != null) {
						// array access
						ExprElm arg = parseExpression(fReader.getPosition());
						this.eatWhitespace();
						if (fReader.read() != ']') {
							tokenExpectedError("]");
						}
						elm = new ExprAccessArray(arg);
					} else {
						// array creation
						Vector<ExprElm> arrayElms = new Vector<ExprElm>(10);
						boolean properlyClosed = false;
						boolean expectingComma = false;
						while (!fReader.reachedEOF()) {
							this.eatWhitespace();
							c = fReader.read();
							if (c == ',') {
								if (!expectingComma)
									arrayElms.add(null);
								expectingComma = false;
							} else if (c == ']') {
								properlyClosed = true;
								break;
							} else {
								fReader.unread();
								arrayElms.add(parseExpression(fReader.getPosition(), COMMA_OR_CLOSE_BRACKET, reportErrors));
								expectingComma = true;
							}
						}
						if (!properlyClosed) {
							errorWithCode(ErrorCode.MissingClosingBracket, fReader.getPosition()-1, fReader.getPosition(), "]");
						}
						elm = new ExprArray(arrayElms.toArray(new ExprElm[0]));
						
					}
				} else { 
					fReader.unread();
				}
			}
		
			// ->
			if (elm == null) {
				int fieldOperatorStart = fReader.getPosition();
				if (parseObjectFieldOperator(fReader.getPosition())) {
					eatWhitespace();
					int idOffset = fReader.getPosition()-fieldOperatorStart;
					if (parseID(fReader.getPosition())) {
						eatWhitespace();
						if (!parseStaticFieldOperator_(fReader.getPosition())) {
							errorWithCode(ErrorCode.TokenExpected, fReader.getPosition(), fReader.getPosition()+2, "::");
						}
					} else
						idOffset = 0;
					elm = new ExprObjectCall(parsedObjectFieldOperator.length() == 3, parsedID, idOffset);
				}
			}
			
			// (<expr>)
			if (elm == null) {
				int c = fReader.read();
				if (c == '(') {
					ExprElm firstExpr = parseExpression(fReader.getPosition());
					if (firstExpr == null) {
						firstExpr = ExprElm.NULL_EXPR;
						// might be disabled
						errorWithCode(ErrorCode.EmptyParentheses, fReader.getPosition()-1, fReader.getPosition());
					}
					c = fReader.read();
					if (c == ')')
						elm = new ExprParenthesized(firstExpr);
					else if (c == ',') {
						errorWithCode(ErrorCode.TuplesNotAllowed, fReader.getPosition()-1, fReader.getPosition());
						// tuple (just for multiple parameters for return)
						List<ExprElm> tupleElms = new LinkedList<ExprElm>();
						tupleElms.add(firstExpr);
						parseRestOfTuple(fReader.getPosition(), tupleElms, reportErrors);
						elm = new ExprTuple(tupleElms.toArray(new ExprElm[0]));
					} else
						errorWithCode(ErrorCode.TokenExpected, fReader.getPosition()-1, fReader.getPosition(), ")");
				} else {
					fReader.unread();
				}
			}
			
			if (elm == null && parsePlaceholderString(fReader.getPosition())) {
				elm = new ExprPlaceholder(parsedString);
			}
			
			// check if sequence is valid (CreateObject(BLUB)->localvar is not)
			if (elm != null) {
				if (!elm.isValidInSequence(prevElm)) {
					elm = null; // blub blub <- first blub is var; second blub is not part of the sequence -.-
					fReader.seek(elmStart);
					dontCheckForPostOp = true;
					//errorWithCode(ErrorCode.NotAllowedHere, elmStart, fReader.getPosition(), fReader.readStringAt(elmStart, fReader.getPosition()));
				} else {
					elm.setExprRegion(elmStart, fReader.getPosition());
					elements.add(elm);
					prevElm = elm;
				}
			}

		} while (elm != null);
		fReader.seek(noWhitespaceEating);
		if (elements.size() == 1) {
			// no need for sequences containing one element
			result = elements.elementAt(elements.size()-1);
		}
		else if (elements.size() > 1) {
			result = new ExprSequence(elements.toArray(new ExprElm[0]));
		} else {
			return null;
		}
		
		result.setExprRegion(sequenceStart, fReader.getPosition());
		if (result.getType() == null) {
			errorWithCode(ErrorCode.InvalidExpression, result);
		}
		
		if (!dontCheckForPostOp) {
			this.eatWhitespace();
			int saved = fReader.getPosition();
			C4ScriptOperator postop = parseOperator_(fReader.getPosition());
			if (postop != null) {
				if (postop.isPostfix()) {
					ExprUnaryOp op = new ExprUnaryOp(postop, ExprUnaryOp.Placement.Postfix, result);
					op.setExprRegion(result.getExprStart(), fReader.getPosition());
					return op;
				} else {
					// a binary operator following this sequence
					fReader.seek(saved);
				}
			}
		}
		return result;
		
	}

	private void parseRestOfTuple(int offset, List<ExprElm> listToAddElementsTo, boolean reportErrors) throws ParsingException {
		fReader.seek(offset);
		boolean expectingComma = false;
		while (!fReader.reachedEOF()) {
			this.eatWhitespace();
			int c = fReader.read();
			if (c == ')') {
				if (!expectingComma && listToAddElementsTo.size() > 0)
					listToAddElementsTo.add(ExprElm.NULL_EXPR);
				break;
			} else if (c == ',') {
				if (!expectingComma) {
					listToAddElementsTo.add(ExprElm.NULL_EXPR);
				}
				expectingComma = false;
			} else {
				fReader.unread();
				if (listToAddElementsTo.size() > 100) {
					errorWithCode(ErrorCode.InternalError, fReader.getPosition(), fReader.getPosition(), "Way too much");
				//	break;
				}
				ExprElm arg = parseExpression(fReader.getPosition(), reportErrors);
				if (arg == null) {
					errorWithCode(ErrorCode.ExpressionExpected, fReader.getPosition(), fReader.getPosition()+1);
//					break;
				} else
					listToAddElementsTo.add(arg);
				expectingComma = true;
			}
		}
	}
	
	/*
	 * ERROR_PLACEHOLDER_EXPR: is always at the reader's current location
	 */
	private final ExprElm ERROR_PLACEHOLDER_EXPR = new ExprElm() {
		@Override
		public int getExprStart() {
			return fReader.getPosition();
		}
		@Override
		public int getExprEnd() {
			return fReader.getPosition()+1;
		}
	};
	
	private ExprElm parseExpression(int offset, char[] delimiters, boolean reportErrors) throws ParsingException {
		final int START = 0;
		final int OPERATOR = 1;
		final int SECONDOPERAND = 2;
		final int DONE = 3;

		parseExpressionRecursion++;
		try {

			ExprElm root = null;
			ExprElm current = null;
			ExprBinaryOp lastOp = null;

			// magical thingie to pass all parameters to inherited
			if (parseEllipsis(offset)) {
				return new ExprEllipsis();
			}

			fReader.seek(offset);
			//this.eatWhitespace();
			int exprStart = fReader.getPosition();
			for (int state = START; state != DONE;) {
				this.eatWhitespace();
				switch (state) {
				case START:
					root = parseExpressionWithoutOperators(fReader.getPosition(), reportErrors);
					current = root;
					state = current != null ? OPERATOR : DONE;
					break;
				case OPERATOR:

					// end of expression?
					int c = fReader.read();
					for (int i = 0; i < delimiters.length; i++) {
						if (delimiters[i] == c) {
							state = DONE;
							fReader.unread();
							break;
						}
					}

					if (state != DONE) {
						fReader.unread();

						int operatorPos = fReader.getPosition();
						C4ScriptOperator op = parseOperator_(fReader.getPosition());
						if (op != null && op.isBinary()) {
							int priorOfNewOp = op.priority();
							ExprElm newLeftSide = null;
							ExprBinaryOp theOp = null;
							for (ExprElm opFromBottom = current.getParent(); opFromBottom instanceof ExprBinaryOp; opFromBottom = opFromBottom.getParent()) {
								ExprBinaryOp oneOp = (ExprBinaryOp) opFromBottom;
								if (priorOfNewOp > oneOp.getOperator().priority() || (priorOfNewOp == oneOp.getOperator().priority() && op.rightAssociative())) {
									theOp = oneOp;
									break;
								}
							}
							if (theOp != null) {
								newLeftSide = theOp.getRightSide();
								current = lastOp = new ExprBinaryOp(op);
								theOp.setRightSide(current);
							} else {
								newLeftSide = root;
								current = root = lastOp = new ExprBinaryOp(op);
							}
							lastOp.setLeftSide(newLeftSide);
							lastOp.setExprRegion(operatorPos, fReader.getPosition());
							state = SECONDOPERAND;
						} else {
							fReader.seek(operatorPos); // in case there was an operator but not a binary one
							state = DONE;
						}
					}
					break;
				case SECONDOPERAND:
					ExprElm rightSide = parseExpressionWithoutOperators(fReader.getPosition(), reportErrors);
					if (rightSide == null)
						errorWithCode(ErrorCode.OperatorNeedsRightSide, lastOp);
					((ExprBinaryOp)current).setRightSide(rightSide);
					lastOp = (ExprBinaryOp)current;
					current = rightSide;
					state = OPERATOR;
					break;

				}
			}
			if (root != null) {
				root.setExprRegion(exprStart, fReader.getPosition());
				// potentially throwing exceptions and stuff
				if (reportErrors)
					root.reportErrors(this);
//				if (expressionListener != null && parseExpressionRecursion == 1)
//					expressionListener.expressionDetected(root, this);
			}
			
			return root;

		} finally {
			parseExpressionRecursion--;
		}
		
	}
	
	private static final char[] SEMICOLON_DELIMITER = new char[] { ';' };
	private static final char[] COMMA_OR_CLOSE_BRACKET = new char[] { ',', ']' };
	
	private ExprElm parseExpression(int offset, boolean reportErrors) throws ParsingException {
		return parseExpression(offset, SEMICOLON_DELIMITER, reportErrors);
	}
	
	private ExprElm parseExpression(int offset) throws ParsingException {
		return parseExpression(offset, true);
	}
	
	/**
	 * Parses [Function description|IMGC|canDisplayMenuItem()]
	 * @param offset
	 * @return
	 * @throws ParsingException
	 */
	private boolean parseFunctionDescription(int offset) throws ParsingException {
		fReader.seek(offset);
		if (fReader.read() != '[') {
			fReader.unread();
			return false;
		}
		String descriptionString = fReader.readStringUntil(']');
		if (descriptionString == null) {
			fReader.seek(offset);
			return false;
		}
		
		if (fReader.read() == ']') {
			return true;
		}
		else {
			tokenExpectedError("]");
			return false;
		}
//		fReader.readStringUntil(new char[] { '|' }); // description text
//		if (fReader.read() != '|') {
//			fReader.unread();
//			if (fReader.read() == ']') return true;
//			else return false;
//		}
//		int idStart = fReader.getPosition();
//		String id = fReader.readStringUntil(new char[] { '|' }); // description ID for icon
//		if (id != null) { // char not found
//			if (id.length() != 4) {
//				String problem = "Syntax error: expected an ID";
//				createErrorMarker(idStart, idStart + 4, problem);
//				throw new ParsingException(problem);
//			}
//			if (fReader.read() == '|') {
//				parseValue(fReader.getPosition());  // menu item condition
//			}
//		}
//

		// [blublu|IMGC|...]
	}
	
	private boolean parseString(int offset) throws ParsingException {
		fReader.seek(offset);
		int delimiter = fReader.read();
		if (delimiter != '"') {
			fReader.unread();
			return false;
		}
		StringBuilder builder = new StringBuilder();
		do {
			if (builder.length() > 0) builder.append(fReader.readString(1));
			builder.append(fReader.readStringUntil((char)delimiter));
		} while (builder.length() != 0 && (builder.charAt(builder.length() - 1) == '\\'));
		if (fReader.read() != '"') {
			throw new ParsingException("Internal parsing error.");
		}
		parsedString = builder.toString();
		return true;
	}
	
	private boolean parsePlaceholderString(int offset) throws ParsingException {
		fReader.seek(offset);
		int delimiter = fReader.read();
		if (delimiter != '$') {
			fReader.unread();
			return false;
		}
		StringBuilder builder = new StringBuilder();
		do {
			if (builder.length() > 0) builder.append(fReader.readString(1));
			builder.append(fReader.readStringUntil((char)delimiter));
		} while (builder.length() != 0 && (builder.charAt(builder.length() - 1) == '\\'));
		if (fReader.read() != '$') {
			throw new ParsingException("Internal parsing error.");
		}
		parsedString = builder.toString();
		return true;
	}

	private LoopType currentLoop;
	
	private Statement parseStatement(int offset) throws ParsingException {
		return parseStatement(offset, false);
	}
	
	private Statement parseStatement(int offset, boolean initializationStatement) throws ParsingException {
		parseStatementRecursion++;
		try {
			fReader.seek(offset);
			eatWhitespace();
			int start = offset;
			String readWord = fReader.readWord();
			Statement result;
			if (readWord == null || readWord.length() == 0) {
				int read = fReader.read();
				if (read == '{' && !initializationStatement) { // order of conditions important oO
					List<Statement> subStatements = new LinkedList<Statement>();
					boolean foundClosingBracket;
					for (eatWhitespace(); !(foundClosingBracket = fReader.read() == '}') && !fReader.reachedEOF(); eatWhitespace()) {
						fReader.unread();
						Statement subStatement = parseStatement(fReader.getPosition());
						if (subStatement != null)
							subStatements.add(subStatement);
						else
							errorWithCode(ErrorCode.StatementExpected, this.ERROR_PLACEHOLDER_EXPR);
					}
					if (!foundClosingBracket)
						errorWithCode(ErrorCode.BlockNotClosed, start, start+1);
					result = new Block(subStatements.toArray(new Statement[0]));
				}
				else if (read == ';') {
					result = new EmptyStatement();
				}
				else {
					fReader.unread();
					ExprElm expression = parseExpression(offset);
					if (expression != null) {
						result = new SimpleStatement(expression);
						if (!initializationStatement) {
							checkForSemicolon();
						}
					}
					else
						result = null;
				}
			}
			else if (readWord.equals(Keywords.VarNamed)) {
				List<Pair<String, ExprElm>> initializations = new LinkedList<Pair<String, ExprElm>>();
				do {
					eatWhitespace();
					String varName = fReader.readWord();
					// check if there is initial content
					eatWhitespace();
					C4Variable var = activeFunc.findVariable(varName);
					parsedVariable = var;
					ExprElm val;
					if (fReader.read() == '=') {
						eatWhitespace();
						offset = fReader.getPosition();
						val = parseExpression(offset);
						if (val == null)
							errorWithCode(ErrorCode.ValueExpected, fReader.getPosition()-1, fReader.getPosition());
						else {
							var.inferTypeFromAssignment(val, this);
						}
					}
					else {
						val = null;
						fReader.unread();
					}
					initializations.add(new Pair<String, ExprElm>(varName, val));
				} while(fReader.read() == ',');
				fReader.unread();
				result = new VarDeclarationStatement(initializations);
				if (!initializationStatement)
					checkForSemicolon();
			}
			else if (!initializationStatement) {
				if (readWord.equals(Keywords.If)) {
					// there are people naming their variables iF >.>
					//				if (!readWord.equals(readWord.toLowerCase())) {
					//					String problem = "Syntax error: you should only use lower case letters in keywords. ('" + readWord.toLowerCase() + "' instead of '" + readWord + "')"; 
					//					createErrorMarker(fReader.getPosition() - readWord.length(), fReader.getPosition(), problem);
					//					throw new ParsingException(problem);
					//				}
					eatWhitespace();
					if (fReader.read() != '(') {
						tokenExpectedError("(");
					}
					eatWhitespace();
					ExprElm condition = parseExpression(fReader.getPosition());
					if (condition == null)
						condition = ExprElm.NULL_EXPR; // if () is valid
					eatWhitespace();
					if (fReader.read() != ')') {
						tokenExpectedError(")");
					}
					eatWhitespace();
					Statement body = parseStatement(fReader.getPosition());
					if (body == null) {
						errorWithCode(ErrorCode.StatementExpected, offset, offset+4);
					}
					
					int beforeElse = fReader.getPosition();
					eatWhitespace();
					offset = fReader.getPosition();
					String nextWord = fReader.readWord();
					Statement elseStatement;
					if (nextWord.equals(Keywords.Else)) {
						eatWhitespace();
						offset = fReader.getPosition();
						elseStatement = parseStatement(fReader.getPosition());
						if (elseStatement == null) {
							errorWithCode(ErrorCode.StatementExpected, offset, offset+Keywords.Else.length());
						}	
					}
					else {
						fReader.seek(beforeElse); // don't eat comments and stuff after if (...) ...;
						elseStatement = null;
					}
					result = new IfStatement(condition, body, elseStatement);
				}
				else if (readWord.equals(Keywords.While)) {
					currentLoop = LoopType.While;
					//			if (!readWord.equals(readWord.toLowerCase())) {
					//				String problem = "Syntax error: you should only use lower case letters in keywords. ('" + readWord.toLowerCase() + "' instead of '" + readWord + "')"; 
					//				createErrorMarker(fReader.getPosition() - readWord.length(), fReader.getPosition(), problem);
					//				throw new ParsingException(problem);
					//			}
					eatWhitespace();
					if (fReader.read() != '(') {
						tokenExpectedError("(");
					}
					eatWhitespace();
					ExprElm condition = parseExpression(fReader.getPosition());
					if (condition == null)
						condition = ExprElm.NULL_EXPR; // while () is valid
					eatWhitespace();
					if (fReader.read() != ')') {
						tokenExpectedError(")");
					}
					eatWhitespace();
					offset = fReader.getPosition();
					Statement body = parseStatement(fReader.getPosition());
					if (body == null) {
						errorWithCode(ErrorCode.StatementExpected, offset, offset+4);
					}
					result = new WhileStatement(condition, body);
				}
				else if (readWord.equals(Keywords.For)) {
					eatWhitespace();
					if (fReader.read() != '(') {
						tokenExpectedError("(");					
					}
					eatWhitespace();

					// initialization
					offset = fReader.getPosition();
					C4Variable loopVariable = null;
					Statement initialization, body;
					ExprElm arrayExpr, condition, increment;
					if (fReader.read() == ';') {
						// any of the for statements is optional
						initialization = null;
					} else {
						fReader.unread();
						initialization = parseStatement(fReader.getPosition(), true);
						if (initialization == null) {
							errorWithCode(ErrorCode.ExpectedCode, fReader.getPosition(), fReader.getPosition()+1);
						}
						loopVariable = parsedVariable; // let's just assume it's the right one
					}

					// determine loop type
					eatWhitespace();
					offset = fReader.getPosition();
					String w;
					if (initialization != null) {
						if (fReader.read() == ';') { // initialization finished regularly with ';'
							offset = fReader.getPosition();
							w = null; // implies there can be no 'in'
						} else {
							fReader.unread();
							w = fReader.readWord();
						}
					}
					else
						w = null; // if there is no initialization statement at all there can also be no 'in'
					LoopType loopType;
					if (w != null && w.equals(Keywords.In)) {
						// it's a for (x in array) loop!
						loopType = LoopType.IterateArray;
						eatWhitespace();
						arrayExpr = parseExpression(fReader.getPosition());
						if (arrayExpr == null)
							errorWithCode(ErrorCode.ExpressionExpected, offset, fReader.getPosition()+1);
						else {
							C4Type t = arrayExpr.getType();
							if (t != C4Type.ANY && t != C4Type.ARRAY)
								warningWithCode(ErrorCode.IncompatibleTypes, arrayExpr, C4Type.ARRAY.toString(), t.toString());
							if (loopVariable != null)
								loopVariable.inferTypeFromAssignment(arrayExpr.getExemplaryArrayElement(this), this);
						}
						condition = null;
						increment = null;
					} else {
						loopType = LoopType.For;
						fReader.seek(offset); // if a word !equaling("in") was read

						if (fReader.read() == ';') {
							// any " optional "
							fReader.unread(); // is expected
							condition = null;
						} else {
							fReader.unread();
							condition = parseExpression(fReader.getPosition());
							if (condition == null) {
								errorWithCode(ErrorCode.ConditionExpected, offset, fReader.getPosition());
							}
						}
						eatWhitespace();
						offset = fReader.getPosition();
						if (fReader.read() != ';') {
							tokenExpectedError(";");
						}
						eatWhitespace();
						offset = fReader.getPosition();
						if (fReader.read() == ')') {
							// " optional "
							fReader.unread(); // is expected
							increment = null;
						} else {
							fReader.unread();
							increment = parseExpression(fReader.getPosition());
							if (increment == null) {
								errorWithCode(ErrorCode.ExpressionExpected, offset, fReader.getPosition()+1);
							}
						}
						arrayExpr = null;
					}
					eatWhitespace();
					if (fReader.read() != ')') {
						tokenExpectedError(")");
					}
					eatWhitespace();
					offset = fReader.getPosition();
					currentLoop = loopType;
					body = parseStatement(fReader.getPosition());
					if (body == null) {
						errorWithCode(ErrorCode.StatementExpected, offset, offset+4);
					}
					switch (loopType) {
					case For:
						result = new ForStatement(initialization, condition, increment, body);
						break;
					case IterateArray:
						result = new IterateArrayStatement(initialization, arrayExpr, body);
						break;
					default:
						result = null;
					}
				}
				else if (readWord.equals(Keywords.Continue)) {
					if (currentLoop == null)
						errorWithCode(ErrorCode.KeywordInWrongPlace, fReader.getPosition()-readWord.length(), fReader.getPosition(), readWord);
					checkForSemicolon();
					result = new ContinueStatement();
				}
				else if (readWord.equals(Keywords.Break)) {
					if (currentLoop == null)
						errorWithCode(ErrorCode.KeywordInWrongPlace, fReader.getPosition()-readWord.length(), fReader.getPosition(), readWord);
					checkForSemicolon();
					result = new BreakStatement();
				}
				else if (readWord.equals(Keywords.Return)) {
					int next = fReader.read();
					ExprElm returnExpr;
					if (next == ';') {
						fReader.unread();
						returnExpr = null;
					}
					else {
						fReader.unread();
						offset = fReader.getPosition();
						disableError(ErrorCode.TuplesNotAllowed);
						disableError(ErrorCode.EmptyParentheses);
						returnExpr = parseExpression(fReader.getPosition());
						if (returnExpr == null) {
							errorWithCode(ErrorCode.ValueExpected, fReader.getPosition() - 1, fReader.getPosition());				
						}
						warnAboutTupleInReturnExpr(returnExpr, false);
						enableError(ErrorCode.TuplesNotAllowed);
						enableError(ErrorCode.EmptyParentheses);
					}
					result = new ReturnStatement(returnExpr);
					checkForSemicolon();
				}
				else
					result = null;
			}
			else
				result = null;

			if (result == null) {
				fReader.seek(start);
				ExprElm expression = parseExpression(fReader.getPosition());
				if (expression != null) {
					result = new SimpleStatement(expression);
					checkForSemicolon();
				}
				else
					result = null;
			}

			if (result != null) {
				result.setExprRegion(start, fReader.getPosition());
				if (parseStatementRecursion == 1 && expressionListener != null)
					expressionListener.expressionDetected(result, this);
				//System.out.println(result.toString());
			}
			return result;
		} finally {
			parseStatementRecursion--;
		}
		

	}

	private boolean parseKeyword(int offset) throws ParsingException {
		LoopType savedLoop = currentLoop;
		try {
			fReader.seek(offset);
			String readWord = fReader.readWord();
			if (readWord == null)
				return false;
			if (readWord.equals(Keywords.If)) {
				// there are people naming their variables iF >.>
//				if (!readWord.equals(readWord.toLowerCase())) {
//					String problem = "Syntax error: you should only use lower case letters in keywords. ('" + readWord.toLowerCase() + "' instead of '" + readWord + "')"; 
//					createErrorMarker(fReader.getPosition() - readWord.length(), fReader.getPosition(), problem);
//					throw new ParsingException(problem);
//				}
				eatWhitespace();
				if (fReader.read() != '(') {
					tokenExpectedError("(");
				}
				eatWhitespace();
				parseValue(fReader.getPosition()); // if () is valid
				eatWhitespace();
				if (fReader.read() != ')') {
					tokenExpectedError(")");
				}
				eatWhitespace();
				offset = fReader.getPosition();
				if (!parseCodeSegment(fReader.getPosition())) {
					errorWithCode(ErrorCode.StatementExpected, offset, offset+4);
				}
				eatWhitespace();
				offset = fReader.getPosition();
				String nextWord = fReader.readWord();
				if (nextWord.equalsIgnoreCase(Keywords.Else)) {
					eatWhitespace();
					offset = fReader.getPosition();
					if (!parseCodeSegment(fReader.getPosition())) {
						errorWithCode(ErrorCode.StatementExpected, offset, offset+4);
					}	
				}
				else {
					fReader.seek(offset);
				}
				return true;
			}
			else if (readWord.equals(Keywords.While)) {
				currentLoop = LoopType.While;
//				if (!readWord.equals(readWord.toLowerCase())) {
//					String problem = "Syntax error: you should only use lower case letters in keywords. ('" + readWord.toLowerCase() + "' instead of '" + readWord + "')"; 
//					createErrorMarker(fReader.getPosition() - readWord.length(), fReader.getPosition(), problem);
//					throw new ParsingException(problem);
//				}
				eatWhitespace();
				if (fReader.read() != '(') {
					tokenExpectedError("(");
				}
				eatWhitespace();
				parseValue(fReader.getPosition()); // while () is valid
				eatWhitespace();
				if (fReader.read() != ')') {
					tokenExpectedError(")");
				}
				eatWhitespace();
				offset = fReader.getPosition();
				if (!parseCodeSegment(fReader.getPosition())) {
					errorWithCode(ErrorCode.StatementExpected, offset, offset+4);
				}
				return true;
			}
			else if (readWord.equals(Keywords.For)) {
				eatWhitespace();
				if (fReader.read() != '(') {
					tokenExpectedError("(");					
				}
				eatWhitespace();

				// initialization
				offset = fReader.getPosition();
				boolean noInitialization = false;
				C4Variable loopVariable = null;
				if (fReader.read() == ';') {
					// any of the for statements is optional
					noInitialization = true;
				} else {
					fReader.unread();
					if (!(parseVarVariableDeclaration(fReader.getPosition(), false) || parseValue(fReader.getPosition()))) {
						errorWithCode(ErrorCode.ExpectedCode, fReader.getPosition(), fReader.getPosition()+1);
					}
					loopVariable = parsedVariable; // let's just assume it's the right one
				}

				// determine loop type
				eatWhitespace();
				offset = fReader.getPosition();
				String w;
				if (!noInitialization) {
					if (fReader.read() == ';') { // initialization finished regularly with ';'
						offset = fReader.getPosition();
						w = null; // implies there can be no 'in'
					} else {
						fReader.unread();
						w = fReader.readWord();
					}
				}
				else
					w = null; // if there is no initialization statement at all there can also be no 'in'
				if (w != null && w.equals(Keywords.In)) {
					// it's a for (x in array) loop!
					currentLoop = LoopType.IterateArray;
					eatWhitespace();
					ExprElm arrayExpr = parseExpression(fReader.getPosition());
					if (arrayExpr == null)
						errorWithCode(ErrorCode.ExpressionExpected, offset, fReader.getPosition()+1);
					else {
						C4Type t = arrayExpr.getType();
						if (t != C4Type.ANY && t != C4Type.ARRAY)
							warningWithCode(ErrorCode.IncompatibleTypes, arrayExpr, C4Type.ARRAY.toString(), t.toString());
						if (loopVariable != null)
							loopVariable.inferTypeFromAssignment(arrayExpr.getExemplaryArrayElement(this), this);
					}
				} else {
					currentLoop = LoopType.For;
					fReader.seek(offset); // if a word !equaling("in") was read

					if (fReader.read() == ';') {
						// any " optional "
						fReader.unread(); // is expected
					} else {
						fReader.unread();
						if (!parseValue(fReader.getPosition())) {
							errorWithCode(ErrorCode.ConditionExpected, offset, fReader.getPosition());
						}
					}
					eatWhitespace();
					offset = fReader.getPosition();
					if (fReader.read() != ';') {
						tokenExpectedError(";");
					}
					eatWhitespace();
					offset = fReader.getPosition();
					if (fReader.read() == ')') {
						// " optional "
						fReader.unread(); // is expected
					} else {
						fReader.unread();
						if (!parseValue(offset)) {
							errorWithCode(ErrorCode.ExpressionExpected, offset, fReader.getPosition()+1);
						}
					}
				}
				eatWhitespace();
				if (fReader.read() != ')') {
					tokenExpectedError(")");
				}
				eatWhitespace();
				offset = fReader.getPosition();
				if (!parseCodeSegment(fReader.getPosition())) {
					errorWithCode(ErrorCode.StatementExpected, offset, offset+4);
				}
				return true;
			}
			else if (readWord.equals(Keywords.Continue) || readWord.equals(Keywords.Break)) {
				if (currentLoop == null)
					errorWithCode(ErrorCode.KeywordInWrongPlace, fReader.getPosition()-readWord.length(), fReader.getPosition(), readWord);
				checkForSemicolon();
				return true;
			}
			else {
				return false;
			}
			// if (parseValue) { parseCode } else if (parseValue) { parseCode } else { parseCode }
			// while (parseValue) { parseCode }
			// for ( ; ; ) { parseCode } // that is special
		} finally {
			currentLoop = savedLoop;
		}
	}
	
	private void checkForSemicolon() throws ParsingException {
		eatWhitespace();
		int readChar = fReader.read();
		if (readChar != ';')
			tokenExpectedError(";");
		
	}
	
	private boolean parseID(int offset) throws ParsingException {
		parsedID = null; // reset so no old parsed ids get through
		fReader.seek(offset);
		String word = fReader.readWord();
		if (word != null && word.length() != 4) {
			fReader.seek(offset);
			return false;
		}
		if (!Utilities.looksLikeID(word)) {
			fReader.seek(offset);
			return false;
		}
		parsedID = C4ID.getID(word);
		return true;
	}

	private boolean parseParameter(int offset, C4Function function) throws ParsingException {
		fReader.seek(offset);
		int s = fReader.getPosition();
		String firstWord = fReader.readWord();
		if (firstWord.length() == 0) {
			if (fReader.read() == '&') {
				firstWord = "&";
			} else {
				fReader.unread();
				return false;
			}
		}
		int e = fReader.getPosition();
		C4Variable var = new C4Variable(null, C4VariableScope.VAR_VAR);
		C4Type type = C4Type.makeType(firstWord);
		var.setType(type);
		if (type == C4Type.UNKNOWN) {
			var.setType(C4Type.ANY);
			var.setName(firstWord);
		}
		else {
			eatWhitespace();
			if (fReader.read() == '&') {
				var.setByRef(true);
				eatWhitespace();
			} else
				fReader.unread();
			int newStart = fReader.getPosition();
			String secondWord = fReader.readWord();
			if (secondWord.length() > 0) {
				var.setName(secondWord);
				s = newStart;
				e = fReader.getPosition();
			}
			else {
				// type is name
				warningWithCode(ErrorCode.TypeAsName, s, e, firstWord);
				var.setType(C4Type.ANY);
				var.setName(firstWord);
				fReader.seek(e);
			}
		}
		var.setLocation(new SourceLocation(s, e));
		var.setParentField(function);
		function.getParameters().add(var);
		return true;
	}
	
	protected void eatWhitespace(int offset) {
		fReader.seek(offset);
		while ((fReader.eatWhitespace() > 0 || parseComment(fReader.getPosition())));
	}
	
	protected boolean eatWhitespace() {
		eatWhitespace(fReader.getPosition());
		return true; // to use it in loop conditions :D
	}
	
	protected boolean parseComment(int offset) {
		fReader.seek(offset);
		String sequence = fReader.readString(2);
		if (sequence == null) {
			return false;
		}
		else if (sequence.equals("//")) {
			fReader.moveUntil(BufferedScanner.NEWLINE_DELIMITERS);
			fReader.eat(BufferedScanner.NEWLINE_DELIMITERS);
			return true;
		}
		else if (sequence.equals("/*")) {
			while (!fReader.reachedEOF()) {
				if (fReader.read() == '*') {
					if (fReader.read() == '/') {
						return true; // genug gefressen
					}
					else {
						fReader.unread();
					}
				}
			}
			return true;
		}
		else {
			fReader.move(-2);
			return false;
		}
	}

	private IMarker createErrorMarker(int start, int end, String message) {
		if (fScript == null) return null;
		try {
			IMarker marker = fScript.createMarker(IMarker.PROBLEM);
			marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
			marker.setAttribute(IMarker.TRANSIENT, false);
			marker.setAttribute(IMarker.MESSAGE, message);
			marker.setAttribute(IMarker.CHAR_START, start);
			marker.setAttribute(IMarker.CHAR_END, end);
			return marker;
		} catch (CoreException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private IMarker createWarningMarker(int start, int end, String message) {
		if (fScript == null) return null;
		try {
			IMarker marker = fScript.createMarker(IMarker.PROBLEM);
			marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_WARNING);
			marker.setAttribute(IMarker.TRANSIENT, false);
			marker.setAttribute(IMarker.MESSAGE, message);
			marker.setAttribute(IMarker.CHAR_START, start);
			marker.setAttribute(IMarker.CHAR_END, end);
			return marker;
		} catch (CoreException e) {
			e.printStackTrace();
		}
		return null;
	}

	public void clean() {
		try {
			if (fScript != null) fScript.deleteMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ONE);
		} catch (CoreException e1) {
			e1.printStackTrace();
		}
		container.clearFields();
	}
	
	public static C4ScriptParser reportExpressionsInStatements(IDocument doc, IRegion region, C4ScriptBase context, C4Function func, IExpressionListener listener) throws BadLocationException, CompilerException, ParsingException {
		return reportExpressionsInStatements(doc, region.getOffset(), region.getOffset()+region.getLength(), context, func, listener);
	}
	
	public static C4ScriptParser reportExpressionsInStatements(IDocument doc, int statementStart, int statementEnd, C4ScriptBase context, C4Function func, IExpressionListener listener) throws BadLocationException, CompilerException, ParsingException {
		String expr = doc.get(statementStart, Math.min(statementEnd-statementStart, doc.getLength()-statementStart));
		C4ScriptParser parser = new C4ScriptParser(expr, context);
		parser.activeFunc = func;
		parser.setExpressionListener(listener);
		try {
			while (!parser.fReader.reachedEOF() && parser.parseCode(parser.fReader.getPosition())) {
				parser.eatWhitespace();
			}
		} catch (Exception e) {
			// :C
		}
		return parser;
	}
	
	public static C4ScriptParser reportExpressionsInStatements(IDocument doc, int offset, C4Object context, C4Function func, IExpressionListener listener) throws BadLocationException, CompilerException, ParsingException {
		return reportExpressionsInStatements(doc, Utilities.getStartOfStatement(doc, offset), offset, context, func, listener);
	}

}
