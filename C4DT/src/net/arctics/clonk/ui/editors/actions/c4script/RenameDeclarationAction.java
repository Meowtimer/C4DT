package net.arctics.clonk.ui.editors.actions.c4script;

import java.lang.reflect.InvocationTargetException;
import java.util.ResourceBundle;

import net.arctics.clonk.ast.Declaration;
import net.arctics.clonk.index.IIndexEntity;
import net.arctics.clonk.refactoring.RenameDeclarationProcessor;
import net.arctics.clonk.ui.editors.StructureTextEditor;
import net.arctics.clonk.ui.editors.EditorUtil;
import net.arctics.clonk.ui.editors.actions.ClonkTextEditorAction;
import net.arctics.clonk.ui.editors.actions.ClonkTextEditorAction.CommandId;
import net.arctics.clonk.ui.refactoring.ClonkRenameRefactoringWizard;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.ltk.core.refactoring.participants.RenameRefactoring;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

@CommandId(id="ui.editors.actions.RenameDeclaration")
public class RenameDeclarationAction extends ClonkTextEditorAction {
	public RenameDeclarationAction(ResourceBundle bundle, String prefix, ITextEditor editor) { super(bundle, prefix, editor); }
	@Override
	public void run() {
		try {
			final Declaration structure = ((StructureTextEditor)getTextEditor()).structure();
			IIndexEntity entity = entityAtSelection(false);
			if (entity == null)
				entity = structure;
			if (entity != null)
				performRenameRefactoring((Declaration)entity, null, 0);
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}
	/**
	 * Perform a Rename Refactoring on the specified declaration. Custom options are passed to {@link RenameDeclarationProcessor#RenameDeclarationProcessor(Declaration, String, int)}
	 * @param declarationToRename The {@link Declaration} to rename
	 * @param fixedNewName New name to perform this refactoring with, without presenting UI to change this name. Supply null to let the user specify the new name
	 * @param renameProcessorOptions {@link RenameDeclarationProcessor} options
	 */
	public static void performRenameRefactoring(Declaration declarationToRename, String fixedNewName, int renameProcessorOptions) {
		if (declarationToRename != null) {
			saveModifiedFiles();
			final String newName = fixedNewName != null ? fixedNewName : declarationToRename.name();
			final RenameRefactoring refactoring = new RenameRefactoring(new RenameDeclarationProcessor(declarationToRename, newName, renameProcessorOptions));
			final ClonkRenameRefactoringWizard wizard = new ClonkRenameRefactoringWizard(refactoring, fixedNewName == null);
			final RefactoringWizardOpenOperation op = new RefactoringWizardOpenOperation(wizard);
			try {
				op.run(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "Performing Clonk Rename refactoring");
			} catch (final InterruptedException e) {
				// do nothing
			}
		}
	}
	private static void saveModifiedFiles() {
		final boolean anyModified = EditorUtil.editorPartsToBeSaved().iterator().hasNext();
		if (anyModified) {
			final ProgressMonitorDialog progressMonitor = new ProgressMonitorDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell());
			try {
				progressMonitor.run(false, false, new IRunnableWithProgress() {
					@Override
					public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
						for (final IEditorPart part : EditorUtil.editorPartsToBeSaved())
							part.doSave(monitor);
					}
				});
			} catch (final Exception e) {
				e.printStackTrace();
			}
		}
	}
}
