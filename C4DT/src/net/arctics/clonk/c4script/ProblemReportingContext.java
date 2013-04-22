package net.arctics.clonk.c4script;

import net.arctics.clonk.index.CachedEngineDeclarations;
import net.arctics.clonk.index.Definition;
import net.arctics.clonk.parser.ASTNode;
import net.arctics.clonk.parser.IASTPositionProvider;
import net.arctics.clonk.parser.IASTVisitor;
import net.arctics.clonk.parser.Markers;
import net.arctics.clonk.parser.SourceLocation;

public interface ProblemReportingContext extends IASTPositionProvider, ITypingContext {
	Definition definition();
	SourceLocation absoluteSourceLocationFromExpr(ASTNode expression);
	CachedEngineDeclarations cachedEngineDeclarations();
	Markers markers();
	void setMarkers(Markers markers);
	Script script();
	void reportProblems();
	Object visitFunction(Function function);
	boolean triggersRevisit(Function function, Function called);
	public void setObserver(IASTVisitor<ProblemReportingContext> observer);
}