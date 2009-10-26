package net.arctics.clonk.resource;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import net.arctics.clonk.ClonkCore;
import net.arctics.clonk.index.C4Object;
import net.arctics.clonk.index.C4ObjectIntern;
import net.arctics.clonk.index.C4ObjectParser;
import net.arctics.clonk.index.ClonkIndex;
import net.arctics.clonk.parser.c4script.C4ScriptBase;
import net.arctics.clonk.parser.c4script.C4ScriptIntern;
import net.arctics.clonk.parser.c4script.C4ScriptParser;
import net.arctics.clonk.parser.C4Structure;
import net.arctics.clonk.parser.ParsingException;
import net.arctics.clonk.preferences.PreferenceConstants;
import net.arctics.clonk.ui.editors.ClonkTextEditor;
import net.arctics.clonk.util.Utilities;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

/**
 * An incremental builder for all project data.<br>
 * This builder launches the parser that indexes all
 * c4objects and highlights syntax errors in one project.<br>
 * Each project has its own ClonkBuilder instance.
 * @author ZokRadonh
 */
public class ClonkBuilder extends IncrementalProjectBuilder implements IResourceDeltaVisitor, IResourceVisitor {

	private int buildPhase;
	private IProgressMonitor monitor;
	private boolean cleanedUI;

	// keeps track of parsers created for specific scripts
	private Map<C4ScriptBase, C4ScriptParser> parserMap = new HashMap<C4ScriptBase, C4ScriptParser>();

	public ClonkBuilder() {
		super();
		// ensure lib builder object
		ClonkCore.getDefault().getLibBuilder();
	}

	public void worked(int count) {
		monitor.worked(count);
	}
	
	private static final class UIRefresher implements Runnable {
		public void run() {
			IWorkbench w = PlatformUI.getWorkbench();
			if (w == null || w.getActiveWorkbenchWindow() == null || w.getActiveWorkbenchWindow().getActivePage() == null)
				return;
			IWorkbenchPage page = w.getActiveWorkbenchWindow().getActivePage();
			for (IEditorReference ref : page.getEditorReferences()) {
				IEditorPart part = ref.getEditor(false);
				if (part != null && part instanceof ClonkTextEditor) {
					((ClonkTextEditor)part).refreshOutline();
				}
			}
		}
	}

	private class ResourceCounterAndCleaner extends ResourceCounter {
		public ResourceCounterAndCleaner(int countFlags) {
			super(countFlags);
		}
		@Override
		public boolean visit(IResource resource) throws CoreException {
			if (resource instanceof IContainer) {
				C4ObjectIntern obj = C4ObjectIntern.objectCorrespondingTo((IContainer) resource);
				if (obj != null)
					obj.setObjectFolder(null);
			}
			else if (resource instanceof IFile) {
				C4Structure.unPinFrom((IFile) resource);
			}
			return super.visit(resource);
		}
		@Override
		public boolean visit(IResourceDelta delta) throws CoreException {
			if (delta.getKind() == IResourceDelta.CHANGED)
				return super.visit(delta);
			return true;
		}
	}

	@Override
	protected void clean(IProgressMonitor monitor) throws CoreException {
		// clean external libs - this does not rebuild
		ClonkCore.getDefault().getLibBuilder().clean();
		
		// clean up this project
		if (monitor != null) monitor.beginTask(Messages.ClonkBuilder_0, 1);
		IProject proj = this.getProject();
		if (proj != null) {
			ClonkProjectNature.getClonkNature(proj).getIndex().clear();
			proj.accept(new ResourceCounterAndCleaner(0));
		}
		if (monitor != null) {
			monitor.worked(1);
			monitor.done();
		}
	}

	@SuppressWarnings({"rawtypes"})
	protected IProject[] build(int kind, Map args, IProgressMonitor monitor)
	throws CoreException {
		parserMap.clear();
		synchronized (this) {
			clearUIOfReferencesBeforeBuild();
			while (!cleanedUI)
				try {
					wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
		}
		System.gc();
		
		try {
			try {
				this.monitor = monitor;
				IProject proj = getProject();

				ClonkProjectNature.getClonkNature(proj).getIndex().notifyExternalLibsSet();

				switch(kind) {
				case AUTO_BUILD:
				case INCREMENTAL_BUILD:
					IResourceDelta delta = getDelta(proj);
					if (delta != null) {

						// count num of resources to build
						ResourceCounter counter = new ResourceCounter(ResourceCounter.COUNT_CONTAINER);
						delta.accept(counter);

						// initialize progress monitor
						monitor.beginTask(Messages.ClonkBuilder_1 + proj.getName(), counter.getCount());
						// parse
						buildPhase = 0;
						delta.accept(this);
						ClonkProjectNature.getClonkNature(proj).getIndex().refreshCache();
						buildPhase = 1;
						delta.accept(this);

						// fire change event
						delta.getResource().touch(monitor);

						// refresh global func and static var cache
						ClonkProjectNature.getClonkNature(proj).getIndex().refreshCache();
					}
					break;

				case FULL_BUILD:

					// calculate build duration
					int[] operations = new int[4];
					if (proj != null) {
						// count num of resources to build and also clean...
						ResourceCounterAndCleaner counter = new ResourceCounterAndCleaner(ResourceCounter.COUNT_CONTAINER);
						proj.accept(counter);
						operations[0] = counter.getCount() * 2;
						operations[1] = counter.getCount();
					}
					else {
						operations[0] = 0;
						operations[1] = 0;
					}

					operations[2] = 0;
					operations[3] = 0;
					ClonkLibBuilder libBuilder = ClonkCore.getDefault().getLibBuilder();
					if (libBuilder.isBuildNeeded()) {
						String[] externalLibs = getExternalLibNames();
						for(String lib : externalLibs) {
							File file = new File(lib);
							if (file.exists()) {
								operations[2] += (int) (file.length() / 7000); // approximate time
							}
						}
						operations[3] = operations[2] / 2; // approximate save time
					}

					int workSum = 0;
					for(int work : operations) workSum += work;

					// initialize progress monitor
					monitor.beginTask(Messages.ClonkBuilder_2 + proj.getName(), workSum);


					// build external lib if needed
					if (libBuilder.isBuildNeeded()) {
						monitor.subTask(Messages.ClonkBuilder_3);
						libBuilder.build(new SubProgressMonitor(monitor,operations[2]));

						monitor.subTask(Messages.ClonkBuilder_4);
						ClonkCore.getDefault().saveExternIndex(
								new SubProgressMonitor(monitor,operations[3]));
					}

					// build project
					if (proj != null) {
						monitor.subTask(Messages.ClonkBuilder_5 + proj.getName());
						// parse declarations
						buildPhase = 0;
						proj.accept(this);
						ClonkProjectNature.getClonkNature(proj).getIndex().refreshCache();
						if (monitor.isCanceled()) {
							monitor.done();
							return null;
						}
						monitor.subTask(Messages.ClonkBuilder_6 + proj.getName());
						// parse code bodies
						buildPhase = 1;
						proj.accept(this);

						// fire update event
						proj.touch(monitor);
					}

				}

				if (monitor.isCanceled()) {
					monitor.done();
					return null;
				}
				monitor.subTask(Messages.ClonkBuilder_7);

				// mark index as dirty so it will be saved when eclipse is shut down
				ClonkProjectNature.getClonkNature(proj).markAsDirty();

				monitor.done();

				refreshUIAfterBuild();

				parserMap.clear();
				return new IProject[] { proj };
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		} finally {
			parserMap.clear();
		}
	}

	private void clearUIOfReferencesBeforeBuild() {
		cleanedUI = false;
		final ClonkBuilder builder = this;
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				try {
					IWorkbench w = PlatformUI.getWorkbench();
					if (w != null && w.getActiveWorkbenchWindow() != null && w.getActiveWorkbenchWindow().getActivePage() != null) {
						IWorkbenchPage page = w.getActiveWorkbenchWindow().getActivePage();
						for (IEditorReference ref : page.getEditorReferences()) {
							IEditorPart part = ref.getEditor(false);
							if (part != null && part instanceof ClonkTextEditor) {
								ClonkTextEditor ed = (ClonkTextEditor) part;
								// only if building the project this element is declared in
								if (
									ed.getTopLevelDeclaration() != null &&
									ed.getTopLevelDeclaration().getResource() != null &&
									builder.getProject() == ed.getTopLevelDeclaration().getResource().getProject()
								)
									ed.clearOutline();
							}
						}
					}
				}
				finally {
					cleanedUI = true;
					synchronized (builder) {
						builder.notify();
					}
				}
			}
		});
	}
	
	private void refreshUIAfterBuild() {
		// refresh outlines
		Display.getDefault().asyncExec(new UIRefresher());
	}

	private String[] getExternalLibNames() {
		String optionString = ClonkCore.getDefault().getPreferenceStore().getString(PreferenceConstants.STANDARD_EXT_LIBS);
		return optionString.split("<>"); //$NON-NLS-1$
	}

	private C4ScriptParser getParserFor(C4ScriptBase script) {
		C4ScriptParser result = parserMap.get(script);
		if (result == null && script != null && script.getScriptFile() instanceof IFile)
			parserMap.put(script, result = new C4ScriptParser(script));
		return result;
	}

	public boolean visit(IResourceDelta delta) throws CoreException {
		if (delta == null) 
			return false;

		if (delta.getResource() instanceof IFile) {
			IFile file = (IFile) delta.getResource();
			if (delta.getKind() == IResourceDelta.CHANGED || delta.getKind() == IResourceDelta.ADDED) {
				C4Structure structure;
				C4ScriptBase script = Utilities.getScriptForFile(file);
				if (script == null && buildPhase == 0) {
					// create if new file
					IContainer folder = delta.getResource().getParent();
					C4ObjectParser objParser;
					if (delta.getResource().getName().endsWith(".c") && folder.getName().endsWith(".c4g")) { //$NON-NLS-1$ //$NON-NLS-2$
						script = new C4ScriptIntern(delta.getResource());
						ClonkProjectNature.getClonkNature(delta.getResource()).getIndex().addScript(script);
					}
					else if ((objParser = C4ObjectParser.create(folder)) != null) {
						script = objParser.createObject();
						objParser.parseScript(getParserFor(script));
					}
				}
				if (script != null && delta.getResource().equals(script.getScriptFile())) {
					if (script != null) {
						C4ScriptParser parser = getParserFor(script);
						switch (buildPhase) {
						case 0:
							parser.clean();
							parser.parseDeclarations();
							break;
						case 1:
							try {
								parser.parseCodeOfFunctions();
							} catch (ParsingException e) {
								e.printStackTrace();
							}
						}
					}
				}
				else if (buildPhase == 0 && (structure = C4Structure.createStructureForFile(file)) != null) {
					structure.commitTo(script);
					structure.pinTo(file);
				}
				else if (buildPhase == 0) {
					C4ObjectIntern obj = C4ObjectIntern.objectCorrespondingTo(file.getParent());
					if (obj != null)
						try {
							obj.processFile(file);
						} catch (IOException e) {
							e.printStackTrace();
						}
				}
				// packed file
				//				else if (C4Group.getGroupType(file.getName()) != C4GroupType.OtherGroup) {
				//					try {
				//						C4Group g = C4Group.openFile(file);
				//						g.explode();
				//					} catch (IOException e) {
				//						e.printStackTrace();
				//					}
				//				}
			}
			else if (delta.getKind() == IResourceDelta.REMOVED && delta.getResource().getParent().exists()) {
				if (buildPhase == 0) {
					C4ScriptBase script = Utilities.getScriptForFile(file);
					if (script != null && file.equals(script.getScriptFile()))
						script.clearDeclarations();
				}
			}
			if (monitor != null)
				monitor.worked(1);
			return true;
		}
		else if (delta.getResource() instanceof IContainer) {
			// make sure the object has a reference to its folder (not to some obsolete deleted one)
			if (delta.getKind() == IResourceDelta.ADDED) {
				C4ObjectIntern object = C4ObjectIntern.objectCorrespondingTo((IContainer)delta.getResource());
				if (object != null)
					object.setObjectFolder((IContainer) delta.getResource());
			}
			else if (delta.getKind() == IResourceDelta.REMOVED) {
				// remove object when folder is removed
				C4ObjectIntern object = C4ObjectIntern.objectCorrespondingTo((IContainer)delta.getResource());
				if (object != null)
					object.getIndex().removeObject(object);
			}
			return true;
		}
		return false;
	}

	public boolean visit(IResource resource) throws CoreException {
		if (resource instanceof IContainer) {
			switch (buildPhase) {
			case 0:
				// first phase: just gather declarations
				C4ObjectParser parser = C4ObjectParser.create((IContainer) resource);
				if (parser != null) { // is complete c4d (with DefCore.txt Script.c and Graphics)
					C4ObjectIntern object = parser.createObject();
					{
						C4ScriptParser scriptParser = getParserFor(object);
						if (scriptParser == null && object.getScriptFile() != null) {
							parserMap.put(object, scriptParser = new C4ScriptParser(object));
						}
						parser.parseScript(scriptParser);
					}
				}
				if (monitor != null) monitor.worked(2);
				return true;
			case 1:
				// check correctness of function code
				ClonkIndex index = ClonkProjectNature.getClonkNature(resource).getIndex();
				C4Object obj = index.getObject((IContainer)resource);
				IFile scriptFile = (IFile) ((obj != null) ? obj.getScriptFile() : null);
				if (scriptFile != null) {
					try {
						getParserFor(obj).parseCodeOfFunctions();
					} catch (ParsingException e) {
						e.printStackTrace();
					}
				}
				if (monitor != null) monitor.worked(1);
				return true;
			}
		}
		else if (resource instanceof IFile) {
			IFile file = (IFile) resource;
			C4Structure structure;
			if (resource.getName().endsWith(".c") && resource.getParent().getName().endsWith(".c4g")) { //$NON-NLS-1$ //$NON-NLS-2$
				C4ScriptBase script = C4ScriptIntern.pinnedScript(file);
				switch (buildPhase) {
				case 0:
					if (script == null) {
						script = new C4ScriptIntern(resource);
					}
					ClonkProjectNature.getClonkNature(resource).getIndex().addScript(script);
					C4ScriptParser parser = getParserFor(script);
					parser.clean();
					parser.parseDeclarations();
					return true;
				case 1:
					if (script != null) {
						try {
							getParserFor(script).parseCodeOfFunctions();
						} catch (ParsingException e) {
							e.printStackTrace();
						}
					}
					return true;
				}
			}
			else if (buildPhase == 0 && (structure = C4Structure.pinned(file, true)) != null) {
				structure.commitTo(Utilities.getScriptForFile(file));
				return true;
			}
			else if (buildPhase == 0) {
				C4ObjectIntern obj = C4ObjectIntern.objectCorrespondingTo(file.getParent());
				if (obj != null)
					try {
						obj.processFile(file);
					} catch (IOException e) {
						e.printStackTrace();
					}
			}
		}
		return false;
	}

}