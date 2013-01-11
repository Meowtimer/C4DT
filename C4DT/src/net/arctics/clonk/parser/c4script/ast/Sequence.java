package net.arctics.clonk.parser.c4script.ast;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import net.arctics.clonk.Core;
import net.arctics.clonk.parser.ParserErrorCode;
import net.arctics.clonk.parser.ParsingException;
import net.arctics.clonk.parser.c4script.C4ScriptParser;
import net.arctics.clonk.parser.c4script.DeclarationObtainmentContext;
import net.arctics.clonk.parser.c4script.IType;
import net.arctics.clonk.parser.c4script.PrimitiveType;

public class Sequence extends ExprElmWithSubElementsArray {

	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;

	public Sequence(ExprElm... elms) {
		super(elms);
		ExprElm prev = null;
		for (ExprElm e : elements) {
			if (e != null)
				e.setPredecessorInSequence(prev);
			prev = e;
		}
	}
	public Sequence(List<ExprElm> elms) {
		this(elms.toArray(new ExprElm[elms.size()]));
	}
	@Override
	public void doPrint(ExprWriter output, int depth) {
		for (ExprElm e : elements)
			e.print(output, depth+1);
	}
	@Override
	public IType unresolvedType(DeclarationObtainmentContext context) {
		return (elements == null || elements.length == 0) ? PrimitiveType.UNKNOWN : elements[elements.length-1].unresolvedType(context);
	}
	@Override
	protected IType callerType(DeclarationObtainmentContext context) {
		return super.callerType(context);
	}
	@Override
	public boolean isModifiable(C4ScriptParser context) {
		return elements != null && elements.length > 0 && elements[elements.length-1].isModifiable(context);
	}
	@Override
	public ITypeInfo createTypeInfo(C4ScriptParser parser) {
		return super.createTypeInfo(parser);
		/*ExprElm last = getLastElement();
		if (last != null)
			// things in sequences should take into account their predecessors
			return last.createStoredTypeInformation(parser);
		return super.createStoredTypeInformation(parser); */
	}
	public Statement[] splitIntoValidSubStatements(C4ScriptParser parser) {
		List<ExprElm> currentSequenceExpressions = new LinkedList<ExprElm>();
		List<Statement> result = new ArrayList<Statement>(elements.length);
		ExprElm p = null;
		for (ExprElm e : elements) {
			if (!e.isValidInSequence(p, parser)) {
				result.add(SimpleStatement.wrapExpression(new Sequence(currentSequenceExpressions)));
				currentSequenceExpressions.clear();
			}
			currentSequenceExpressions.add(e);
			p = e;
		}
		if (result.size() == 0)
			return new Statement[] {SimpleStatement.wrapExpression(this)};
		else {
			result.add(SimpleStatement.wrapExpression(new Sequence(currentSequenceExpressions)));
			return result.toArray(new Statement[result.size()]);
		}
	}
	public Sequence subSequenceUpTo(ExprElm elm) {
		List<ExprElm> list = new ArrayList<ExprElm>(elements.length);
		for (ExprElm e : elements)
			if (e == elm)
				break;
			else
				list.add(e);
		return list.size() > 0 ? new Sequence(list) : null;
	}
	public Sequence subSequenceIncluding(ExprElm elm) {
		List<ExprElm> list = new ArrayList<ExprElm>(elements.length);
		for (ExprElm e : elements) {
			list.add(e);
			if (e == elm)
				break;
		}
		return list.size() > 0 ? new Sequence(list) : null;
	}
	@Override
	public void assignment(ExprElm rightSide, C4ScriptParser context) {
		lastElement().assignment(rightSide, context);
	}
	public ExprElm successorOfSubElement(ExprElm element) {
		for (int i = 0; i < elements.length; i++)
			if (elements[i] == element)
				return i+1 < elements.length ? elements[i+1] : null;
		return null;
	}
	@Override
	public void reportProblems(C4ScriptParser parser) throws ParsingException {
		ExprElm p = null;
		for (ExprElm e : elements) {
			if (
				(e != null && !e.isValidInSequence(p, parser)) ||
				(p != null && !p.allowsSequenceSuccessor(parser, e))
			)
				parser.error(ParserErrorCode.NotAllowedHere, e, C4ScriptParser.NO_THROW, e);
			p = e;
		}
	}
}