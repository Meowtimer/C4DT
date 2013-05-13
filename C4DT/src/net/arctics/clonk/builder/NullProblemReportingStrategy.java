package net.arctics.clonk.builder;

import static net.arctics.clonk.util.Utilities.as;
import net.arctics.clonk.ast.ASTNode;
import net.arctics.clonk.ast.Declaration;
import net.arctics.clonk.ast.IASTVisitor;
import net.arctics.clonk.ast.SourceLocation;
import net.arctics.clonk.c4script.Function;
import net.arctics.clonk.c4script.ProblemReporter;
import net.arctics.clonk.c4script.ProblemReportingStrategy;
import net.arctics.clonk.c4script.ProblemReportingStrategy.Capabilities;
import net.arctics.clonk.c4script.Script;
import net.arctics.clonk.c4script.ast.AccessDeclaration;
import net.arctics.clonk.c4script.typing.IType;
import net.arctics.clonk.c4script.typing.PrimitiveType;
import net.arctics.clonk.c4script.typing.TypingJudgementMode;
import net.arctics.clonk.index.CachedEngineDeclarations;
import net.arctics.clonk.index.Definition;
import net.arctics.clonk.parser.Markers;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IRegion;

@Capabilities(capabilities=Capabilities.ISSUES|Capabilities.TYPING)
final class NullProblemReportingStrategy extends ProblemReportingStrategy {
	@Override
	public void run() {}

	@Override
	public ProblemReporter localReporter(final Script script, int fragmentOffset, ProblemReporter chain) {
		return new ProblemReporter() {
			IASTVisitor<ProblemReporter> observer;
			final Markers markers = new Markers();
			@Override
			public boolean judgement(ASTNode node, IType type, TypingJudgementMode mode) { return false; }
			@Override
			public <T extends IType> T typeOf(ASTNode node, Class<T> cls) { return as((IType)PrimitiveType.ANY, cls); }
			@Override
			public IType typeOf(ASTNode node) { return PrimitiveType.ANY; }
			@Override
			public <T extends AccessDeclaration> Declaration obtainDeclaration(T access) { return null; }
			@Override
			public void incompatibleTypesMarker(ASTNode node, IRegion region, IType left, IType right) {}
			@Override
			public int fragmentOffset() { return 0; }
			@Override
			public IFile file() { return script.scriptFile(); }
			@Override
			public Declaration container() { return script; }
			@Override
			public Script script() { return script; }
			@Override
			public Object visit(Function function) {
				if (observer != null)
					function.traverse(observer, this);
				return null;
			}
			@Override
			public void run() {}
			@Override
			public Markers markers() { return markers; }
			@Override
			public void setMarkers(Markers markers) { /* ignore */ }
			@Override
			public Definition definition() { return as(script, Definition.class); }
			@Override
			public CachedEngineDeclarations cachedEngineDeclarations() { return null; }
			@Override
			public SourceLocation absoluteSourceLocationFromExpr(ASTNode expression) { return expression; }
			@Override
			public boolean isModifiable(ASTNode node) { return true; }
			@Override
			public void setObserver(IASTVisitor<ProblemReporter> observer) { this.observer = observer; }
		};
	}
}