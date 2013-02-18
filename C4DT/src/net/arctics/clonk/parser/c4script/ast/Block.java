package net.arctics.clonk.parser.c4script.ast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import net.arctics.clonk.Core;
import net.arctics.clonk.parser.ASTNode;
import net.arctics.clonk.parser.ASTNodePrinter;
import net.arctics.clonk.parser.IEvaluationContext;
import net.arctics.clonk.parser.c4script.Conf;
import net.arctics.clonk.parser.c4script.ProblemReportingContext;
import net.arctics.clonk.util.ArrayUtil;

/**
 * A {} block
 *
 */
public class Block extends Statement {

	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
	private ASTNode[] statements;
	
	public Block(List<ASTNode> statements) {
		this(statements.toArray(new ASTNode[statements.size()]));
	}

	public Block(Statement... statements) {
		super();
		this.statements = statements;
		assignParentToSubElements();
	}
	
	// helper constructor that wraps expressions in statement if necessary
	public Block(ASTNode... expressions) {
		this(SimpleStatement.wrapExpressions(expressions));
	}
	
	public ASTNode[] statements() {
		return statements;
	}

	public void setStatements(ASTNode[] statements) {
		this.statements = statements;
	}

	@Override
	public void setSubElements(ASTNode[] elms) {
		ASTNode[] typeAdjustedCopy = new ASTNode[elms.length];
		System.arraycopy(elms, 0, typeAdjustedCopy, 0, elms.length);
		setStatements(typeAdjustedCopy);
	}

	@Override
	public ASTNode[] subElements() {
		return statements();
	}

	@Override
	public void doPrint(ASTNodePrinter builder, int depth) {
		printBlock(statements, builder, depth);
	}

	public static void printBlock(ASTNode[] statements, ASTNodePrinter builder, int depth) {
		builder.append("{\n"); //$NON-NLS-1$
		for (ASTNode statement : statements) {
			//statement.printPrependix(builder, depth);
			Conf.printIndent(builder, depth+1);
			statement.print(builder, depth+1);
			//statement.printAppendix(builder, depth);
			builder.append("\n"); //$NON-NLS-1$
		}
		Conf.printIndent(builder, depth); builder.append("}"); //$NON-NLS-1$
	}
	
	@Override
	public ASTNode optimize(final ProblemReportingContext context) throws CloneNotSupportedException {
		if (parent() != null && !(parent() instanceof KeywordStatement) && !(this instanceof BunchOfStatements))
			return new BunchOfStatements(statements);
		// uncomment never-reached statements
		boolean notReached = false;
		ASTNode[] commentedOutList = null;
		for (int i = 0; i < statements.length; i++) {
			ASTNode s = statements[i];
			if (notReached) {
				if (commentedOutList != null)
					commentedOutList[i] = s instanceof Comment ? s : s.commentedOut();
				else if (!(s instanceof Comment)) {
					commentedOutList = new Statement[statements.length];
					System.arraycopy(statements, 0, commentedOutList, 0, i);
					commentedOutList[i] = s.commentedOut();
				}
			}
			else
				notReached = s != null && s.controlFlow() != ControlFlow.Continue;
		}
		if (commentedOutList != null)
			return new Block(commentedOutList);
		else
			return super.optimize(context);
	}
	
	@Override
	public ControlFlow controlFlow() {
		for (ASTNode s : statements) {
			// look for first statement that breaks execution
			ControlFlow cf = s.controlFlow();
			if (cf != ControlFlow.Continue)
				return cf;
		}
		return ControlFlow.Continue;
	}
	
	@Override
	public EnumSet<ControlFlow> possibleControlFlows() {
		EnumSet<ControlFlow> result = EnumSet.noneOf(ControlFlow.class);
		for (ASTNode s : statements) {
			ControlFlow cf = s.controlFlow();
			if (cf != ControlFlow.Continue)
				return EnumSet.of(cf);
			EnumSet<ControlFlow> cfs = s.possibleControlFlows();
			result.addAll(cfs);
		}
		return result;
	}
	
	@Override
	public Object evaluate(IEvaluationContext context) throws ControlFlowException {
		for (ASTNode s : subElements())
			if (s != null)
				s.evaluate(context);
		return null;
	}
	
	public void addStatements(Statement... statements) {
		this.statements = ArrayUtil.concat(this.statements, statements);
	}
	
	public void removeStatement(ASTNode s) {
		List<ASTNode> l = new ArrayList<ASTNode>(Arrays.asList(statements));
		l.remove(s);
		this.statements = l.toArray(new Statement[l.size()]);
	}

}