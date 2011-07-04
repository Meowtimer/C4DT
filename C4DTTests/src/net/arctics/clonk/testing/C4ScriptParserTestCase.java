package net.arctics.clonk.testing;


import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.arctics.clonk.ClonkCore;
import net.arctics.clonk.index.Index;
import net.arctics.clonk.index.Engine;
import net.arctics.clonk.parser.BufferedScanner;
import net.arctics.clonk.parser.ID;
import net.arctics.clonk.parser.ParserErrorCode;
import net.arctics.clonk.parser.ParsingException;
import net.arctics.clonk.parser.SimpleScriptStorage;
import net.arctics.clonk.parser.c4script.C4ScriptParser;
import net.arctics.clonk.parser.c4script.IType;
import net.arctics.clonk.parser.c4script.Operator;
import net.arctics.clonk.parser.c4script.PrimitiveType;
import net.arctics.clonk.parser.c4script.ScriptBase;
import net.arctics.clonk.parser.c4script.SpecialScriptRules;
import net.arctics.clonk.parser.c4script.TypeSet;
import net.arctics.clonk.parser.c4script.Variable;
import net.arctics.clonk.parser.c4script.ast.AccessVar;
import net.arctics.clonk.parser.c4script.ast.BinaryOp;
import net.arctics.clonk.parser.c4script.ast.Block;
import net.arctics.clonk.parser.c4script.ast.BoolLiteral;
import net.arctics.clonk.parser.c4script.ast.BreakStatement;
import net.arctics.clonk.parser.c4script.ast.BunchOfStatements;
import net.arctics.clonk.parser.c4script.ast.CallFunc;
import net.arctics.clonk.parser.c4script.ast.ExprElm;
import net.arctics.clonk.parser.c4script.ast.ForStatement;
import net.arctics.clonk.parser.c4script.ast.IASTComparisonDelegate;
import net.arctics.clonk.parser.c4script.ast.NumberLiteral;
import net.arctics.clonk.parser.c4script.ast.SimpleStatement;
import net.arctics.clonk.parser.c4script.ast.StringLiteral;
import net.arctics.clonk.parser.c4script.ast.UnaryOp;
import net.arctics.clonk.parser.c4script.ast.VarDeclarationStatement;
import net.arctics.clonk.parser.c4script.ast.UnaryOp.Placement;
import net.arctics.clonk.parser.c4script.ast.WhileStatement;
import net.arctics.clonk.parser.c4script.ast.Wildcard;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IStorage;
import org.junit.*;

import static org.junit.Assert.*;

public class C4ScriptParserTestCase {

	@Before
	public void Before() {

	}

	public class Setup {
		public ScriptBase script;
		public C4ScriptParser parser;
		private List<ParserErrorCode> errors = new ArrayList<ParserErrorCode>(
				20);

		public Setup(final String script) throws UnsupportedEncodingException {
			this.script = new ScriptBase(new Index() {
				private static final long serialVersionUID = ClonkCore.SERIAL_VERSION_UID;
				private Engine engine = new Engine("TestEngine") {
					private final SpecialScriptRules rules = new SpecialScriptRules() {
						private final Matcher ID_MATCHER = Pattern.compile(
								"[A-Za-z_][A-Za-z_0-9]*").matcher("");

						@Override
						public ID parseId(BufferedScanner scanner) {
							// HACK: Script parsers won't get IDs from this
							// method because IDs are actually parsed as
							// AccessVars and parsing them with
							// a <match all identifiers> pattern would cause
							// zillions of err0rs
							if (scanner instanceof C4ScriptParser)
								return null;
							if (ID_MATCHER.reset(
									scanner.getBuffer().substring(
											scanner.getPosition()))
									.lookingAt()) {
								String idString = ID_MATCHER.group();
								scanner.advance(idString.length());
								if (BufferedScanner.isWordPart(scanner
										.peek())
										|| C4ScriptParser.NUMERAL_PATTERN
												.matcher(idString)
												.matches()) {
									scanner.advance(-idString.length());
									return null;
								}
								return ID.getID(idString);
							}
							return null;
						}
					};
					private EngineSettings settings;
					private static final long serialVersionUID = ClonkCore.SERIAL_VERSION_UID;

					@Override
					public SpecialScriptRules getSpecialScriptRules() {
						return rules;
					};

					@Override
					public EngineSettings getCurrentSettings() {
						if (settings == null) {
							settings = new EngineSettings();
							settings.maxStringLen = 0;
							settings.nonConstGlobalVarsAssignment = true;
							settings.proplistsSupported = true;
							settings.strictDefaultLevel = 3;
							settings.supportsRefs = false;
							settings.treatZeroAsAny = true;
						}
						return settings;
					};
				};

				@Override
				public Engine getEngine() {
					return engine;
				};
			}) {
				private static final long serialVersionUID = ClonkCore.SERIAL_VERSION_UID;
				SimpleScriptStorage storage = new SimpleScriptStorage(
						"TestScript", script);

				@Override
				public IStorage getScriptStorage() {
					return storage;
				}
			};
			this.parser = new C4ScriptParser(script, this.script, null) {
				@Override
				public IMarker markerWithCode(ParserErrorCode code,
						int markerStart, int markerEnd, int flags,
						int severity, Object... args) throws ParsingException {
					try {
						throw new Exception();
					} catch (Exception e) {
						e.printStackTrace();
					}
					errors.add(code);
					return null;
				};
			};
		}
	}

	@Test
	public void testASTPrinting() {
		Block b = new Block(new SimpleStatement(new BinaryOp(Operator.Assign,
				new AccessVar("i"), new NumberLiteral(50))),
				new SimpleStatement(new UnaryOp(Operator.Increment,
						Placement.Prefix, new AccessVar("i"))),
				new WhileStatement(new BoolLiteral(true), new Block(
						new SimpleStatement(new CallFunc("Log",
								new StringLiteral("Test"))),
						new BreakStatement())));
		String ref = "{\n" + "\ti = 50;\n" + "\t++i;\n" + "\twhile (true)\n"
				+ "\t{\n" + "\t\tLog(\"Test\");\n" + "\t\tbreak;\n" + "\t}\n"
				+ "}";
		assertTrue(b.toString().equals(ref));
	}

	@Test
	public void testForLoopParsingParsing() {
		final Block block = new BunchOfStatements(new ForStatement(
				new VarDeclarationStatement(Variable.Scope.VAR,
						new VarDeclarationStatement.VarInitialization("i",
								NumberLiteral.ZERO, 0)), new BinaryOp(
						Operator.Smaller, new AccessVar("i"),
						new NumberLiteral(100)), new UnaryOp(
						Operator.Increment, Placement.Postfix, new AccessVar(
								"i")), new Block(new SimpleStatement(
						new CallFunc("Log", new StringLiteral("Hello"))))));
		assertParsingYieldsCorrectAST(block);
	}

	private static final String TEST_FUNC_TEMPLATE = "func Test() {%s}";

	protected void assertParsingYieldsCorrectAST(final Block block) {
		Setup setup;
		try {
			setup = new Setup(String.format(TEST_FUNC_TEMPLATE,
					block.toString()));
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
			assertFalse(true);
			return;
		}
		try {
			setup.parser.parse();
		} catch (ParsingException e) {
			e.printStackTrace();
		}
		assertTrue(setup.errors.size() == 0);
		assertTrue(setup.script.findFunction("Test") != null);
		block.toString();
		assertTrue(setup.script.findFunction("Test").getCodeBlock()
				.compare(block, new IASTComparisonDelegate() {
					@Override
					public DifferenceHandling differs(ExprElm a, ExprElm b,
							Object what) {
						return DifferenceHandling.Differs;
					}

					@Override
					public boolean optionEnabled(Option option) {
						switch (option) {
						case CheckForIdentity:
							return false;
						default:
							return false;
						}
					}

					@Override
					public void wildcardMatched(Wildcard wildcard,
							ExprElm expression) {
						// ignore
					}
				}).isEqual());
	}

	public static String callingMethod() {
		return new Throwable().getStackTrace()[1].getMethodName();
	}

	public String file(String name) {
		try {
			InputStream stream = getClass().getResourceAsStream(name+".txt");
			try {
				InputStreamReader reader = new InputStreamReader(stream);
				StringBuilder builder = new StringBuilder();
				int read = 0;
				char[] buf = new char[1024];
				while ((read = reader.read(buf)) > 0) {
					builder.append(buf, 0, read);
				}
				return builder.toString();
			} finally {
				stream.close();
			}
		} catch (IOException e) {
			return "";
		}
	}

	@Test
	public void testTypeInference() throws UnsupportedEncodingException, ParsingException {
		Setup setup = new Setup(file(callingMethod()));
		setup.parser.parse();
		IType t = setup.script.findFunction("TypeInference").findVariable("x").getType();
		assertTrue(t instanceof TypeSet);
		TypeSet ty = (TypeSet)t;
		assertTrue(ty.size() == 3 && ty.types().contains(PrimitiveType.STRING) && ty.types().contains(PrimitiveType.BOOL) && ty.types().contains(PrimitiveType.INT));
	}

}