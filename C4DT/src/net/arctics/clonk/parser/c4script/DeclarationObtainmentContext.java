package net.arctics.clonk.parser.c4script;

import net.arctics.clonk.index.Definition;
import net.arctics.clonk.parser.ParsingException;
import net.arctics.clonk.parser.c4script.ast.ExprElm;

public interface DeclarationObtainmentContext {
	ScriptBase getContainer();
	Function getCurrentFunc();
	IType queryTypeOfExpression(ExprElm exprElm, IType defaultType);
	Definition getContainerAsDefinition();
	void parseCodeOfFunction(Function field, boolean b) throws ParsingException;
	IType getContainerAsType();
}