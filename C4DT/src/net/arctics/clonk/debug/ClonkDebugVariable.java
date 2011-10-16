package net.arctics.clonk.debug;

import net.arctics.clonk.parser.c4script.Variable;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.core.model.IVariable;

public class ClonkDebugVariable extends ClonkDebugElement implements IVariable {

	private ClonkDebugStackFrame stackFrame;
	private Variable variable;
	private ClonkDebugVariableValue value;
	
	public ClonkDebugVariable(ClonkDebugStackFrame stackFrame, Variable variable) {
		super(stackFrame.getTarget());
		this.stackFrame = stackFrame;
		this.variable = variable;
		this.value = new ClonkDebugVariableValue(this);
	}
	
	public Variable getVariable() {
		return variable;
	}

	public ClonkDebugStackFrame getStackFrame() {
    	return stackFrame;
    }

	@Override
	public String getName() throws DebugException {
		return variable.name();
	}

	@Override
	public String getReferenceTypeName() throws DebugException {
		return variable.getType().toString();
	}

	@Override
	public ClonkDebugVariableValue getValue() throws DebugException {
		return value;
	}

	@Override
	public boolean hasValueChanged() throws DebugException {
		return false;
	}

	@Override
	public void setValue(String expression) throws DebugException {
		// not supported as of yet
	}

	@Override
	public void setValue(IValue value) throws DebugException {
		// not supported as of yet
	}

	@Override
	public boolean supportsValueModification() {
		return false;
	}

	@Override
	public boolean verifyValue(String expression) throws DebugException {
		return true;
	}

	@Override
	public boolean verifyValue(IValue value) throws DebugException {
		return true;
	}

}
