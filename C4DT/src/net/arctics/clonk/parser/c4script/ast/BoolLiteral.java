package net.arctics.clonk.parser.c4script.ast;

import net.arctics.clonk.ClonkCore;
import net.arctics.clonk.parser.ParserErrorCode;
import net.arctics.clonk.parser.ParsingException;
import net.arctics.clonk.parser.c4script.C4ScriptOperator;
import net.arctics.clonk.parser.c4script.C4ScriptParser;
import net.arctics.clonk.parser.c4script.C4Type;
import net.arctics.clonk.parser.c4script.IType;
import net.arctics.clonk.parser.c4script.Keywords;

public final class BoolLiteral extends Literal<Boolean> {

	private static final long serialVersionUID = ClonkCore.SERIAL_VERSION_UID;
	public boolean booleanValue() {
		return getLiteral().booleanValue();
	}
	public BoolLiteral(boolean value) {
		super(Boolean.valueOf(value));
	}
	public IType getType(C4ScriptParser context) {
		return C4Type.BOOL;
	}
	@Override
	public void doPrint(ExprWriter output, int depth) {
		output.append(booleanValue() ? Keywords.True : Keywords.False);
	}
	@Override
	public void reportErrors(C4ScriptParser parser) throws ParsingException {
		if (getParent() instanceof BinaryOp) {
			C4ScriptOperator op = ((BinaryOp) getParent()).getOperator();
			if (op == C4ScriptOperator.And || op == C4ScriptOperator.Or)
				parser.warningWithCode(ParserErrorCode.BoolLiteralAsOpArg, this, this.toString());
		}
	}
}