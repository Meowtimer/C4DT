package net.arctics.clonk.resource;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.arctics.clonk.index.Definition;
import net.arctics.clonk.index.ProjectDefinition;
import net.arctics.clonk.index.DefinitionParser;
import net.arctics.clonk.index.ClonkIndex;
import net.arctics.clonk.index.ProjectIndex;
import net.arctics.clonk.parser.c4script.ScriptBase;
import net.arctics.clonk.parser.c4script.StandaloneProjectScript;
import net.arctics.clonk.parser.c4script.C4ScriptParser;
import net.arctics.clonk.parser.c4script.Variable;
import net.arctics.clonk.parser.Declaration;
import net.arctics.clonk.parser.ID;
import net.arctics.clonk.parser.IHasIncludes;
import net.arctics.clonk.parser.Structure;
import net.arctics.clonk.parser.ParsingException;
import net.arctics.clonk.refactoring.RenameDeclarationProcessor;
import net.arctics.clonk.resource.c4group.C4Group;
import net.arctics.clonk.resource.c4group.C4Group.GroupType;
import net.arctics.clonk.ui.editors.ClonkTextEditor;
import net.arctics.clonk.ui.editors.actions.c4script.RenameDeclarationAction;
import net.arctics.clonk.util.Pair;
import net.arctics.clonk.util.UI;
import net.arctics.clonk.util.Utilities;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.navigator.CommonNavigator;

/**
 * An incremental builder for all project data.<br>
 * This builder launches the parser that indexes all
 * {@link Definition}s and standalone scripts and highlights syntax errors in one project.<br>
 * Each project has its own ClonkBuilder instance.
 * @author ZokRadonh
 */
public class ClonkBuilder extends IncrementalProjectBuilder {
	
	private static final boolean INDEX_C4GROUPS = true;

	private static final class UIRefresher implements Runnable {
		
		private List<IResource> listOfResourcesToBeRefreshed;
		
		public UIRefresher(List<IResource> listOfResourcesToBeRefreshed) {
			super();
			this.listOfResourcesToBeRefreshed = listOfResourcesToBeRefreshed;
		}

		public void run() {
			IWorkbench w = PlatformUI.getWorkbench();
			for (IWorkbenchWindow window : w.getWorkbenchWindows()) {
				for (IWorkbenchPage page : window.getPages()) {
					for (IEditorReference ref : page.getEditorReferences()) {
						IEditorPart part = ref.getEditor(false);
						if (part != null && part instanceof ClonkTextEditor) {
							((ClonkTextEditor)part).refreshOutline();
						}
					}
				}
				CommonNavigator projectExplorer = Utilities.getProjectExplorer(window);
				if (projectExplorer != null) {
					for (IResource r : listOfResourcesToBeRefreshed) {
						projectExplorer.getCommonViewer().refresh(r);
					}
				}
			}
		}
	}

	private static class ResourceCounterAndCleaner extends ResourceCounter {
		public ResourceCounterAndCleaner(int countFlags) {
			super(countFlags);
		}
		@Override
		public boolean visit(IResource resource) throws CoreException {
			if (resource instanceof IContainer) {
				ProjectDefinition obj = ProjectDefinition.definitionCorrespondingToFolder((IContainer) resource);
				if (obj != null)
					obj.setObjectFolder(null);
			}
			else if (resource instanceof IFile) {
				Structure.unPinFrom((IFile) resource);
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
	
	private class C4GroupStreamHandler implements IResourceDeltaVisitor, IResourceVisitor {
		
		public static final int OPEN = 0;
		public static final int CLOSE = 1;
		
		private int operation;
		
		public C4GroupStreamHandler(int operation) {
			this.operation = operation;
		}
		
		@Override
		public boolean visit(IResourceDelta delta) throws CoreException {
			IResource res = delta.getResource();
			return visit(res);
		}

		@Override
		public boolean visit(IResource res) throws CoreException {
			if (res.getParent() != null && res.getParent().equals(res.getProject()) && res instanceof IContainer) {
				URI uri = null;
				try {
					uri = res.getLocationURI();
				} catch (Exception e) {
					System.out.println(res.getFullPath().toString());
				}
				IFileStore store = EFS.getStore(uri);
				if (store instanceof C4Group) {
					C4Group group = (C4Group) store;
					try {
						switch (operation) {
						case OPEN:
							group.requireStream();
							break;
						case CLOSE:
							group.releaseStream();
							break;
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			return res instanceof IProject;
		}
	}
	
	public class ScriptGatherer implements IResourceDeltaVisitor, IResourceVisitor {
		public boolean visit(IResourceDelta delta) throws CoreException {
			if (delta == null) 
				return false;

			if (delta.getResource() instanceof IFile) {
				IFile file = (IFile) delta.getResource();
				ScriptBase script;
				switch (delta.getKind()) {
				case IResourceDelta.CHANGED: case IResourceDelta.ADDED:
					script = ScriptBase.get(file, true);
					if (script == null) {
						// create if new file
						IContainer folder = delta.getResource().getParent();
						DefinitionParser objParser;
						// script in a resource group
						if (delta.getResource().getName().toLowerCase().endsWith(".c") && folder.getName().toLowerCase().endsWith(".c4g")) //$NON-NLS-1$ //$NON-NLS-2$
							script = new StandaloneProjectScript(file);
						// object script
						else if (delta.getResource().getName().equals("Script.c") && (objParser = DefinitionParser.create(folder)) != null) //$NON-NLS-1$
							script = objParser.createObject();
						// some other file but a script is still needed so get the definition for the folder
						else
							script = ProjectDefinition.definitionCorrespondingToFolder(folder);
					}
					if (script != null && delta.getResource().equals(script.getScriptStorage())) {
						queueScript(script);
					} else
						processAuxiliaryFiles(file, script);
					// packed file
					//				else if (C4Group.getGroupType(file.getName()) != C4GroupType.OtherGroup) {
					//					try {
					//						C4Group g = C4Group.openFile(file);
					//						g.explode();
					//					} catch (IOException e) {
					//						e.printStackTrace();
					//					}
					//				}
					break;
				case IResourceDelta.REMOVED:
					script = StandaloneProjectScript.scriptCorrespondingTo(file);
					if (script != null && file.equals(script.getScriptStorage()))
						script.getIndex().removeScript(script);
				}
				return true;
			}
			else if (delta.getResource() instanceof IContainer) {
				if (!INDEX_C4GROUPS)
					if (EFS.getStore(delta.getResource().getLocationURI()) instanceof C4Group)
						return false;
				// make sure the object has a reference to its folder (not to some obsolete deleted one)
				ProjectDefinition object;
				switch (delta.getKind()) {
				case IResourceDelta.ADDED:
					object = ProjectDefinition.definitionCorrespondingToFolder((IContainer)delta.getResource());
					if (object != null)
						object.setObjectFolder((IContainer) delta.getResource());
					break;
				case IResourceDelta.REMOVED:
					// remove object when folder is removed
					object = ProjectDefinition.definitionCorrespondingToFolder((IContainer)delta.getResource());
					if (object != null)
						object.getIndex().removeDefinition(object);
					break;
				}
				return true;
			}
			return false;
		}
		public boolean visit(IResource resource) throws CoreException {
			if (currentSubProgressMonitor != null && currentSubProgressMonitor.isCanceled())
				return false;
			if (resource instanceof IContainer) {
				if (!INDEX_C4GROUPS)
					if (EFS.getStore(resource.getLocationURI()) instanceof C4Group)
						return false;
				DefinitionParser parser = DefinitionParser.create((IContainer) resource);
				if (parser != null) { // is complete c4d (with DefCore.txt Script.c and Graphics)
					ProjectDefinition object = parser.createObject();
					if (object != null) {
						queueScript(object);
					}
				}
				return true;
			}
			else if (resource instanceof IFile) {
				IFile file = (IFile) resource;
				// only create standalone-scripts for *.c files residing in System groups
				String systemName = nature.getIndex().getEngine().groupName("System", GroupType.ResourceGroup);
				if (
					resource.getName().toLowerCase().endsWith(".c") &&
					systemName.equals(resource.getParent().getName())
				) {
					ScriptBase script = StandaloneProjectScript.pinnedScript(file, true);
					if (script == null) {
						script = new StandaloneProjectScript(file);
					}
					queueScript(script);
					return true;
				}
				else if (processAuxiliaryFiles(file, ScriptBase.get(file, true))) {
					return true;
				}
			}
			return false;
		}
		private boolean processAuxiliaryFiles(IFile file, ScriptBase script) throws CoreException {
			boolean result = true;
			Structure structure;
			if ((structure = Structure.createStructureForFile(file, true)) != null) {
				structure.commitTo(script, ClonkBuilder.this);
				structure.pinTo(file);
			}
			else
				structure = Structure.pinned(file, false, true);
			if (structure != null)
				gatheredStructures.add(structure);
			// not parsed as Structure - let definition process the file
			else if (script instanceof ProjectDefinition) {
				ProjectDefinition def = (ProjectDefinition)script;
				try {
					def.processFile(file);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			else
				result = false;
			return result;
		}
	}

	private IProgressMonitor currentSubProgressMonitor;
	private ClonkProjectNature nature;

	/**
	 *  Gathered list of scripts to be parsed
	 */
	private Map<ScriptBase, C4ScriptParser> parserMap = new HashMap<ScriptBase, C4ScriptParser>();

	/**
	 * Set of structures that have been validated during one build round - keeping track of them so when parsing dependent scripts, scripts that might lose some warnings
	 * due to structure files having been revalidated can also be reparsed (string tables and such)
	 */
	private Set<Structure> gatheredStructures = new HashSet<Structure>();
	
	/**
	 * Set of {@link Definition}s whose ids have been changed by reparsing of their DefCore.txt files
	 */
	private Set<Pair<Definition, ID>> renamedDefinitions = new HashSet<Pair<Definition, ID>>();

	@Override
	protected void clean(IProgressMonitor monitor) throws CoreException {
		System.out.println(String.format(Messages.ClonkBuilder_CleaningProject, getProject().getName()));
		// clean up this project
		if (monitor != null)
			monitor.beginTask(Messages.CleaningUp, 1);
		IProject proj = this.getProject();
		if (proj != null) {
			proj.deleteMarkers(null, true, IResource.DEPTH_INFINITE);
			ProjectIndex projIndex = ClonkProjectNature.get(proj).forceIndexRecreation();
			proj.accept(new ResourceCounterAndCleaner(0));
			projIndex.clear();
		}
		if (monitor != null) {
			monitor.worked(1);
			monitor.done();
		}
	}
	
	private int buildKind;

	@SuppressWarnings({"rawtypes"})
	protected IProject[] build(int kind, Map args, IProgressMonitor monitor) throws CoreException {
		this.buildKind = kind;
		clearState();
		List<IResource> listOfResourcesToBeRefreshed = new LinkedList<IResource>();
		
		clearUIOfReferencesBeforeBuild();
		
		try {
			try {
				IProject proj = getProject();

				performBuildPhases(
					monitor, listOfResourcesToBeRefreshed, proj,
					getDelta(proj)
				);

				if (monitor.isCanceled()) {
					monitor.done();
					return null;
				}

				// mark index as dirty so it will be saved when eclipse is shut down
				ClonkProjectNature.get(proj).getIndex().setDirty(true);

				refreshUIAfterBuild(listOfResourcesToBeRefreshed);
				handleDefinitionRenaming();

				// validate files related to the scripts that have been parsed
				for (ScriptBase script : parserMap.keySet()) {
					validateRelatedFiles(script);
				}
				
				return new IProject[] { proj };
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		} finally {
			clearState();
		}
	}

	private void handleDefinitionRenaming() {
		for (Pair<Definition, ID> rnd : renamedDefinitions) {
			final Definition def = rnd.first();
			final ID newID = rnd.second();
			Display.getDefault().asyncExec(new Runnable() {
				@Override
				public void run() {
					ID oldID = def.id();
					if (oldID == null || oldID == ID.NULL)
						return;
					// FIXME: implement for CR?
					Variable var = def.proxyVar();
					if (var != null && UI.confirm(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
						String.format(Messages.ClonkBuilder_RenameRefactoringPrompt, oldID.getName(), newID.getName()),
						String.format(Messages.ClonkBuilder_RenameRefactoringTitle, oldID.getName()))
					)
						// perform a refactoring - the RenameDeclarationProcessor will take care of renaming the proxyvar which in turn will cause the id of the definition to actually be changed
						RenameDeclarationAction.performRenameRefactoring(var, newID.getName(), RenameDeclarationProcessor.CONSIDER_DEFCORE_ID_ALREADY_CHANGED);
					else
						// simply set the new id
						def.setId(newID); 
				}
			});
		}
	}

	private static <T extends IResourceVisitor & IResourceDeltaVisitor> void visitDeltaOrWholeProject(IResourceDelta delta, IProject proj, T ultimateVisit0r) throws CoreException {
		if (delta != null)
			delta.accept(ultimateVisit0r);
		else
			proj.accept(ultimateVisit0r);
	}
	
	private void performBuildPhases(
		IProgressMonitor monitor,
		List<IResource> listOfResourcesToBeRefreshed,
		IProject proj,
		IResourceDelta delta
	) throws CoreException {

		nature = ClonkProjectNature.get(proj); 
		ClonkIndex index = nature.getIndex();
		
		// visit files to open C4Groups if files are contained in c4group file system
		visitDeltaOrWholeProject(delta, proj, new C4GroupStreamHandler(C4GroupStreamHandler.OPEN));
		try {

			// count num of resources to build
			ResourceCounter counter = new ResourceCounter(ResourceCounter.COUNT_CONTAINER);
			visitDeltaOrWholeProject(delta, proj, counter);

			// initialize progress monitor
			monitor.beginTask(String.format(Messages.BuildProject, proj.getName()), counter.getCount()*2);
			
			// populate toBeParsed list
			parserMap.clear();
			visitDeltaOrWholeProject(delta, proj, new ScriptGatherer());
			
			// delete old declarations
			for (ScriptBase script : parserMap.keySet()) {
				script.clearDeclarations();
			}
			index.refreshIndex();
			
			// parse declarations
			currentSubProgressMonitor = new SubProgressMonitor(monitor, parserMap.size());
			currentSubProgressMonitor.beginTask(Messages.ClonkBuilder_ParseDeclarationsTask, parserMap.size());
			int parserMapSize;
			Map<ScriptBase, C4ScriptParser> newlyEnqueuedParsers = new HashMap<ScriptBase, C4ScriptParser>();
			Map<ScriptBase, C4ScriptParser> enqueuedFromLastIteration = new HashMap<ScriptBase, C4ScriptParser>();
			newlyEnqueuedParsers.putAll(parserMap);
			do {
				parserMapSize = parserMap.size();
				for (ScriptBase script : newlyEnqueuedParsers.keySet()) {
					if (monitor.isCanceled())
						return;
					performBuildPhaseOne(script);
				}
				// refresh now so gathered structures will be validated with an index that has valid appendages maps and such.
				// without refreshing the index here, error markers would be created for TimerCall=... etc. assignments in ActMaps for example
				// if the function being referenced is defined in an appendto from this index
				index.refreshIndex();
				// don't queue dependent scripts during a clean build - if everything works right all scripts will have been added anyway
				if (buildKind == CLEAN_BUILD || buildKind == FULL_BUILD)
					break;
				enqueuedFromLastIteration.clear();
				enqueuedFromLastIteration.putAll(newlyEnqueuedParsers);
				newlyEnqueuedParsers.clear();
				queueDependentScripts(enqueuedFromLastIteration, newlyEnqueuedParsers);
			}
			while (parserMapSize != parserMap.size());
			currentSubProgressMonitor.done();

			if (delta != null) {
				listOfResourcesToBeRefreshed.add(delta.getResource());
			}

			// refresh global func and static var cache
			//index.refreshIndex();
			
			// parse function code
			currentSubProgressMonitor = new SubProgressMonitor(monitor, parserMap.size());
			currentSubProgressMonitor.beginTask(Messages.ClonkBuilder_ParseCodeTask, parserMap.size());
			while (!parserMap.isEmpty()) {
				if (currentSubProgressMonitor.isCanceled())
					return;
				performBuildPhaseTwo(parserMap.keySet().iterator().next());
			}
			currentSubProgressMonitor.done();

			applyLatentMarkers();

		} finally {
			monitor.done();
			visitDeltaOrWholeProject(delta, proj, new C4GroupStreamHandler(C4GroupStreamHandler.CLOSE));
		}
	}

	private void clearState() {
		gatheredStructures.clear();
		parserMap.clear();
		renamedDefinitions.clear();
	}

	private void queueDependentScripts(Map<ScriptBase, C4ScriptParser> scriptsToQueueDependenciesFrom, Map<ScriptBase, C4ScriptParser> newlyAddedParsers) {
		for (C4ScriptParser parser : scriptsToQueueDependenciesFrom.values()) {
			if (currentSubProgressMonitor.isCanceled())
				break;
			if (parser == null)
				continue;
			//System.out.println("Queueing dependent scripts for " + parser.getContainer().toString());
			for (ScriptBase dep : parser.getContainer().dependentScripts()) {
				if (!parserMap.containsKey(dep)) {
					C4ScriptParser p = queueScript(dep);
					newlyAddedParsers.put(dep, p);
				}
			}
		}
		for (Structure s : gatheredStructures) {
			s.validate();
			if (s.requiresScriptReparse()) {
				ScriptBase script = ScriptBase.get(s.getResource(), false);
				if (script != null) {
					C4ScriptParser p = queueScript(script);
					newlyAddedParsers.put(script, p);
				}
			}
		}
		gatheredStructures.clear();
	}

	private void applyLatentMarkers() {
		for (C4ScriptParser p : parserMap.values()) {
			p.applyLatentMarkers();
		}
	}

	private void validateRelatedFiles(ScriptBase script) throws CoreException {
		if (script instanceof ProjectDefinition) {
			ProjectDefinition def = (ProjectDefinition) script;
			for (IResource r : def.definitionFolder().members()) {
				if (r instanceof IFile) {
					Structure pinned = Structure.pinned((IFile) r, false, true);
					if (pinned != null)
						pinned.validate();
				}
			}
		}
	}

	private void clearUIOfReferencesBeforeBuild() {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				try {
					IWorkbench w = PlatformUI.getWorkbench();
					for (IWorkbenchWindow window : w.getWorkbenchWindows()) {
						if (window.getActivePage() != null) {
							IWorkbenchPage page = window.getActivePage();
							for (IEditorReference ref : page.getEditorReferences()) {
								IEditorPart part = ref.getEditor(false);
								if (part != null && part instanceof ClonkTextEditor) {
									ClonkTextEditor ed = (ClonkTextEditor) part;
									// only if building the project this element is declared in
									Declaration topLevelDeclaration = ed.topLevelDeclaration();
									if (
										topLevelDeclaration != null &&
										topLevelDeclaration.getResource() != null &&
										ClonkBuilder.this.getProject().equals(topLevelDeclaration.getResource().getProject())
									)
										ed.clearOutline();
								}
							}
						}
					}
				}
				finally {
					synchronized (ClonkBuilder.this) {
						ClonkBuilder.this.notify();
					}
				}
			}
		});
	}
	
	private void refreshUIAfterBuild(List<IResource> listOfResourcesToBeRefreshed) {
		// refresh outlines
		Display.getDefault().asyncExec(new UIRefresher(listOfResourcesToBeRefreshed));
	}
	
	private C4ScriptParser queueScript(ScriptBase script) {
		C4ScriptParser result;
		if (!parserMap.containsKey(script)) {
			IStorage storage = script.getScriptStorage();
			if (storage != null) {
				result = new C4ScriptParser(script);
				result.setBuilder(this);
			} else
				result = null;
			parserMap.put(script, result);
		} else
			result = parserMap.get(script);
		return result;
	}
	
	private void performBuildPhaseOne(ScriptBase script) {
		C4ScriptParser parser = parserMap.get(script);
		nature.getIndex().addScript(script);
		if (parser != null) {
			parser.clean();
			parser.parseDeclarations();
		}
		if (currentSubProgressMonitor != null) {
			currentSubProgressMonitor.worked(1);
		}
	}
	
	/**
	 * Parse function code/validate variable initialization code.
	 * An attempt is made to parse included scripts before the passed one.
	 * @param script The script to parse
	 */
	private void performBuildPhaseTwo(ScriptBase script) {
		if (parserMap.containsKey(script)) {
			C4ScriptParser parser = parserMap.remove(script);
			if (parser != null) {
				try {
					// parse #included scripts before this one
					for (IHasIncludes include : script.getIncludes(nature.getIndex(), false)) {
						if (include instanceof ScriptBase)
							performBuildPhaseTwo((ScriptBase) include);
					}
					parser.parseCodeOfFunctionsAndValidate();
				} catch (ParsingException e) {
					e.printStackTrace();
				}
			}
			if (currentSubProgressMonitor != null) {
				currentSubProgressMonitor.worked(1);
			}
		}
		//nature.getIndex().refreshIndex();
	}

	/**
	 * Inform the builder about a Definition renaming caused by modifying its DefCore.txt file.
	 * @param def The {@link Definition} whose DefCore.txt id value changed
	 * @param ID newID The new id the builder is supposed to assign to the definition eventually
	 */
	public void queueDefinitionRenaming(Definition def, ID newID) {
		renamedDefinitions.add(new Pair<Definition, ID>(def, newID));
	}

}