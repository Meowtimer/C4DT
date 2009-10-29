package net.arctics.clonk.command.ui.actions;

import net.arctics.clonk.command.Command.C4CommandScript;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.commands.IHandlerListener;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.window.Window;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;
import org.eclipse.ui.PlatformUI;

public class InvokeCommandAction extends Action implements IWorkbenchWindowActionDelegate, IHandler {
	
	private static final String COMMAND_SCRIPT_TEMPLATE = "func Main() {%s;}";
	
	@Override
	public void run() {
	    InputDialog inputDialog = new InputDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
	    		"Invoke Command", "Specify command", "", null);
	    switch (inputDialog.open()) {
	    case Window.OK:
	    	C4CommandScript script = new C4CommandScript("command", String.format(COMMAND_SCRIPT_TEMPLATE, inputDialog.getValue()));
	    	script.invoke((Object[])null);
	    }
	}

	@Override
    public void dispose() {}

	@Override
    public void init(IWorkbenchWindow window) {}

	@Override
    public void run(IAction action) {
	    run();
    }

	@Override
    public void selectionChanged(IAction action, ISelection selection) {}

	@Override
    public void addHandlerListener(IHandlerListener handlerListener) {}

	@Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
	    run();
	    return null;
    }

	@Override
    public void removeHandlerListener(IHandlerListener handlerListener) {
    }
}
