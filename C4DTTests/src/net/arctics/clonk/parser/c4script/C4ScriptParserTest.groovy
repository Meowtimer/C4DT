package net.arctics.clonk.parser.c4script

import java.util.regex.Matcher
import java.util.regex.Pattern
import org.junit.Assert
import org.junit.Before

import net.arctics.clonk.Core
import net.arctics.clonk.TestBase;
import net.arctics.clonk.index.Engine
import net.arctics.clonk.index.EngineSettings
import net.arctics.clonk.index.Index
import net.arctics.clonk.parser.ASTNode
import net.arctics.clonk.parser.BufferedScanner
import net.arctics.clonk.parser.ID
import net.arctics.clonk.parser.Markers
import net.arctics.clonk.parser.ParsingException
import net.arctics.clonk.parser.Problem
import net.arctics.clonk.parser.SimpleScriptStorage
import net.arctics.clonk.parser.c4script.ast.ASTComparisonDelegate
import net.arctics.clonk.parser.c4script.ast.AccessVar
import net.arctics.clonk.parser.c4script.ast.BinaryOp
import net.arctics.clonk.parser.c4script.ast.Block
import net.arctics.clonk.parser.c4script.ast.BreakStatement
import net.arctics.clonk.parser.c4script.ast.BunchOfStatements
import net.arctics.clonk.parser.c4script.ast.CallDeclaration
import net.arctics.clonk.parser.c4script.ast.ForStatement
import net.arctics.clonk.parser.c4script.ast.IntegerLiteral
import net.arctics.clonk.parser.c4script.ast.SimpleStatement
import net.arctics.clonk.parser.c4script.ast.StringLiteral
import net.arctics.clonk.parser.c4script.ast.True
import net.arctics.clonk.parser.c4script.ast.UnaryOp
import net.arctics.clonk.parser.c4script.ast.VarDeclarationStatement
import net.arctics.clonk.parser.c4script.ast.VarInitialization
import net.arctics.clonk.parser.c4script.ast.WhileStatement
import net.arctics.clonk.parser.c4script.ast.UnaryOp.Placement

import org.eclipse.core.resources.IStorage
import org.junit.Test


public class C4ScriptParserTest extends TestBase {

	static class Setup {
		Script script
		C4ScriptParser parser
		final Markers parserMarkers = new Markers()
		Setup(final String script) {
			this.script = new Script(new Index() {
				private static final long serialVersionUID = Core.SERIAL_VERSION_UID
				@Override
				public Engine engine() { Core.instance().loadEngine(TestBase.ENGINE) }
			}) {
				@Override
				IStorage source() { new SimpleScriptStorage(script) }
			}
			this.parser = new C4ScriptParser(script, this.script, null)
			this.parser.setMarkers(parserMarkers)
		}
	}

	@Test
	public void testASTPrinting() {
		final Block b = new Block(new SimpleStatement(new BinaryOp(Operator.Assign,
				new AccessVar("i"), new IntegerLiteral(50))),
				new SimpleStatement(new UnaryOp(Operator.Increment,
						Placement.Prefix, new AccessVar("i"))),
				new WhileStatement(new True(), new Block(
						new SimpleStatement(new CallDeclaration("Log",
								new StringLiteral("Test"))),
						new BreakStatement())))
		def ref =
"""{
	i = 50;
	++i;
	while (true)
	{
		Log("Test");
		break;
	}
}"""
		Assert.assertTrue(b.printed().equals(ref))
	}

	@Test
	public void testForLoopParsingParsing() {
		final ASTNode body = new ForStatement(
				new VarDeclarationStatement(Variable.Scope.VAR,
						new VarInitialization("i", IntegerLiteral.ZERO, 0, 0, null)),
							new BinaryOp(Operator.Smaller, new AccessVar("i"),
								new IntegerLiteral(100)), new UnaryOp(Operator.Increment, Placement.Postfix, new AccessVar("i")),
								new Block(new SimpleStatement(
									new CallDeclaration("Log", new StringLiteral("Hello")))))
		
		final Setup setup = new Setup(String.format("func Test() {%s}", body.printed()))
		try {
			setup.parser.parse()
		} catch (final ParsingException e) {
			e.printStackTrace()
		}
		Assert.assertTrue(setup.parserMarkers.size() == 0)
		Assert.assertTrue(setup.script.findFunction("Test") != null)
		Assert.assertTrue(setup.script.findFunction("Test").body().statements().length == 1)
		Assert.assertTrue(setup.script.findFunction("Test").body().statements()[0].compare(body, new ASTComparisonDelegate(body)))
	}

	public String file(String name) {
		try {
			final InputStream stream = getClass().getResourceAsStream(name+".txt")
			try {
				final InputStreamReader reader = new InputStreamReader(stream)
				final StringBuilder builder = new StringBuilder()
				int read = 0
				final char[] buf = new char[1024]
				while ((read = reader.read(buf)) > 0)
					builder.append(buf, 0, read)
				return builder.toString()
			} finally {
				stream.close()
			}
		} catch (final IOException e) { return "" }
	}

}
