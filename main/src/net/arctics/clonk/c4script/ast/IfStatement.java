package net.arctics.clonk.c4script.ast;

import java.util.EnumSet;

import net.arctics.clonk.Core;
import net.arctics.clonk.ast.ASTNode;
import net.arctics.clonk.ast.ASTNodePrinter;
import net.arctics.clonk.ast.ControlFlow;
import net.arctics.clonk.ast.ControlFlowException;
import net.arctics.clonk.ast.IEvaluationContext;
import net.arctics.clonk.c4script.Conf;
import net.arctics.clonk.c4script.Keywords;

public class IfStatement extends ConditionalStatement {

	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
	private ASTNode elseExpr;

	public IfStatement(final ASTNode condition, final ASTNode body, final ASTNode elseExpr) {
		super(condition, body);
		this.elseExpr = elseExpr;
		assignParentToSubElements();
	}

	@Override
	public String keyword() { return Keywords.If; }
	@Override
	public ASTNode[] subElements() { return new ASTNode[] {condition, body, elseExpr}; }
	public ASTNode elseExpression() { return elseExpr; }

	@Override
	public void doPrint(final ASTNodePrinter builder, final int depth) {
		builder.append(keyword());
		builder.append(" ("); //$NON-NLS-1$
		condition.print(builder, depth);
		builder.append(")"); //$NON-NLS-1$
		printBody(body, builder, depth);
		if (elseExpr != null) {
			builder.append("\n"); //$NON-NLS-1$
			Conf.printIndent(builder, depth);
			builder.append(Keywords.Else);
			if (!(elseExpr instanceof IfStatement)) {
				printBody(elseExpr, builder, depth);
			} else {
				builder.append(" ");
				elseExpr.print(builder, depth);
			}
		}
	}

	@Override
	public void setSubElements(final ASTNode[] elms) {
		condition = elms[0];
		body      = elms[1];
		elseExpr  = elms[2];
	}

	@Override
	public EnumSet<ControlFlow> possibleControlFlows() {
		final EnumSet<ControlFlow> result = EnumSet.of(ControlFlow.Continue);
		result.addAll(body.possibleControlFlows());
		if (elseExpr != null) {
			result.addAll(elseExpr.possibleControlFlows());
		}
		return result;
	}

	@Override
	public ControlFlow controlFlow() {
		// return most optimistic flow (the smaller ordinal() the more "continuy" the flow is)
		final ControlFlow ifCase = body.controlFlow();
		final ControlFlow elseCase = elseExpr != null ? elseExpr.controlFlow() : ControlFlow.Continue;
		return ifCase.ordinal() < elseCase.ordinal() ? ifCase : elseCase;
	}

	@Override
	public Object evaluate(final IEvaluationContext context) throws ControlFlowException {
		if (!condition.evaluate(context).equals(false)) {
			return body.evaluate(context);
		} else if (elseExpr != null) {
			return elseExpr.evaluate(context);
		} else {
			return null;
		}
	}

}
