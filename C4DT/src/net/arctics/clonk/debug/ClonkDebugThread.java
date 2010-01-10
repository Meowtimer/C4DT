package net.arctics.clonk.debug;

import java.util.HashSet;
import java.util.Set;

import net.arctics.clonk.debug.ClonkDebugTarget.Commands;
import net.arctics.clonk.index.ClonkIndex;
import net.arctics.clonk.index.ProjectIndex;
import net.arctics.clonk.parser.c4script.C4Function;
import net.arctics.clonk.parser.c4script.C4ScriptBase;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;

public class ClonkDebugThread extends ClonkDebugElement implements IThread {
	
	private static final IStackFrame[] NO_STACKFRAMES = new IStackFrame[0];
	
	private String sourcePath;
	private IStackFrame[] stackFrames;
	private C4ScriptBase script;
	
	private void nullOut() {
		script = null;
		stackFrames = NO_STACKFRAMES;
	}
	
	public C4ScriptBase findScript(String path, ClonkIndex index, Set<ClonkIndex> alreadySearched) throws CoreException {
		if (alreadySearched.contains(index))
			return null;
		C4ScriptBase script = index.findScriptByPath(path);
		if (script != null)
			return script;
		alreadySearched.add(index);
		if (index instanceof ProjectIndex) {
			for (IProject proj : ((ProjectIndex) index).getProject().getReferencedProjects()) {
				ProjectIndex projIndex = ProjectIndex.get(proj);
				if (projIndex != null) {
					C4ScriptBase _result = findScript(path, projIndex, alreadySearched);
					if (_result != null)
						return _result;
				}
			}
		}
		return null;
	}
	
	public void setSourcePath(String sourcePath) throws CoreException {
		if (sourcePath == null) {
			nullOut();
			return;
		}
		String fullSourcePath = sourcePath;
		int delim = sourcePath.lastIndexOf(':');
		String linePart = sourcePath.substring(delim+1);
		int line = Integer.parseInt(linePart);
		sourcePath = sourcePath.substring(0, delim);
		if (this.sourcePath == null || this.sourcePath.equals(sourcePath)) {
			this.sourcePath = sourcePath;
			ProjectIndex index = ProjectIndex.get(getTarget().getScenario().getProject());
			if (index != null)
				script = findScript(sourcePath, index, new HashSet<ClonkIndex>());
		}
		if (script != null) {
			C4Function f = script.funcAtLine(line);
			stackFrames = new IStackFrame[] {
				new ClonkDebugStackFrame(this, f != null ? f : fullSourcePath, line)
			};
		}
	}
	
	public ClonkDebugThread(ClonkDebugTarget target) {
		super(target);
		fireEvent(new DebugEvent(this, DebugEvent.CREATE));
	}

	@Override
	public IBreakpoint[] getBreakpoints() {
		return new IBreakpoint[0];
	}

	@Override
	public String getName() throws DebugException {
		return Messages.MainThread;
	}

	@Override
	public int getPriority() throws DebugException {
		return 1;
	}

	@Override
	public IStackFrame[] getStackFrames() throws DebugException {
		return hasStackFrames() ? stackFrames : new IStackFrame[0];
	}

	@Override
	public IStackFrame getTopStackFrame() throws DebugException {
		return hasStackFrames() ? stackFrames[0] : null;
	}

	@Override
	public boolean hasStackFrames() throws DebugException {
		return stackFrames != null && stackFrames.length > 0 && isSuspended();
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Object getAdapter(Class adapter) {
		return null;
	}

	@Override
	public boolean canResume() {
		return true;
	}

	@Override
	public boolean canSuspend() {
		return true;
	}

	@Override
	public boolean isSuspended() {
		return getTarget().isSuspended();
	}

	@Override
	public void resume() throws DebugException {
		getTarget().resume();
		fireResumeEvent(DebugEvent.CLIENT_REQUEST);
	}

	@Override
	public void suspend() throws DebugException {
		getTarget().suspend();
		//fireSuspendEvent(DebugEvent.CLIENT_REQUEST);
	}

	@Override
	public boolean canStepInto() {
		return true;
	}

	@Override
	public boolean canStepOver() {
		return true;
	}

	@Override
	public boolean canStepReturn() {
		return true;
	}

	@Override
	public boolean isStepping() {
		return getTarget().isSuspended();
	}

	@Override
	public void stepInto() throws DebugException {
		getTarget().send(Commands.SUSPEND);
	}

	@Override
	public void stepOver() throws DebugException {
		// TODO Auto-generated method stub

	}

	@Override
	public void stepReturn() throws DebugException {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean canTerminate() {
		return getTarget().canTerminate();
	}

	@Override
	public boolean isTerminated() {
		return getTarget().isTerminated();
	}

	@Override
	public void terminate() throws DebugException {
		getTarget().terminate();
		fireTerminateEvent();
	}

}
