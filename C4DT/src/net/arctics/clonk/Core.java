package net.arctics.clonk;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.arctics.clonk.builder.ClonkProjectNature;
import net.arctics.clonk.c4script.SystemScript;
import net.arctics.clonk.index.Engine;
import net.arctics.clonk.index.ProjectIndex;
import net.arctics.clonk.ini.IniUnit;
import net.arctics.clonk.landscapescript.LandscapeScript;
import net.arctics.clonk.preferences.ClonkPreferences;
import net.arctics.clonk.stringtbl.StringTbl;
import net.arctics.clonk.util.APIReflection;
import net.arctics.clonk.util.FolderStorageLocation;
import net.arctics.clonk.util.IStorageLocation;
import net.arctics.clonk.util.PathUtil;
import net.arctics.clonk.util.ReadOnlyIterator;
import net.arctics.clonk.util.StreamUtil;
import net.arctics.clonk.util.StreamUtil.StreamWriteRunnable;
import net.arctics.clonk.util.UI;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.ISaveContext;
import org.eclipse.core.resources.ISaveParticipant;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.editors.text.TextFileDocumentProvider;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Version;

/**
 * The core of the plugin. The singleton instance of this class stores various global things, like engine objects and preferences.
 */
public class Core extends AbstractUIPlugin implements ISaveParticipant, IResourceChangeListener {

	public static final String HUMAN_READABLE_NAME = Messages.HumanReadableName;
	private static final String VERSION_REMEMBERANCE_FILE = "version.txt"; //$NON-NLS-1$

	/**
	 * The Plugin-ID
	 */
	public static final String PLUGIN_ID = Core.class.getPackage().getName();

	/**
	 * id for Clonk project natures
	 */
	public static final String NATURE_ID = id("clonknature"); //$NON-NLS-1$

	/**
	 * Binding context for Clonk related editing activities
	 */
	public static final String CONTEXT_ID = id("context");

	/**
	 * id for error markers that denote errors in a script
	 */
	public static final String MARKER_C4SCRIPT_ERROR = id("c4scripterror"); //$NON-NLS-1$

	/**
	 * id for error markers that denote errors in a ini file
	 */
	public static final String MARKER_INI_ERROR = id("inierror"); //$NON-NLS-1$

	public static final QualifiedName FOLDER_C4ID_PROPERTY_ID = new QualifiedName(PLUGIN_ID, "c4id"); //$NON-NLS-1$
	public static final QualifiedName FOLDER_DEFINITION_REFERENCE_ID = new QualifiedName(PLUGIN_ID, "c4object"); //$NON-NLS-1$
	public static final QualifiedName FILE_STRUCTURE_REFERENCE_ID = new QualifiedName(PLUGIN_ID, "structure"); //$NON-NLS-1$

	public static final String MENU_GROUP_CLONK = id("ui.editors.actions.clonkGroup");

	public static final long SERIAL_VERSION_UID = 1L;

	/**
	 * The engine object contains global functions and variables defined by Clonk itself
	 */
	private Engine activeEngine;

	/**
	 * List of engines currently loaded
	 */
	private final Map<String, Engine> loadedEngines = new HashMap<String, Engine>();

	/**
	 * Shared instance
	 */
	private static Core instance;

	/**
	 * Provider used by the plugin to provide text of documents
	 */
	private final TextFileDocumentProvider textFileDocumentProvider;

	private String engineConfigurationFolder;
	private Version versionFromLastRun;
	private final boolean runsHeadless;

	public Core(boolean runsHeadless) {
		this.runsHeadless = runsHeadless;
		this.textFileDocumentProvider = runsHeadless() ? null : new TextFileDocumentProvider();
	}
	public Core() { this(false); }

	public Version versionFromLastRun() {
		return versionFromLastRun;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
	 */
	@SuppressWarnings("deprecation")
	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		try {
			versionFromLastRun = new Version(StreamUtil.stringFromFile(new File(getStateLocation().toFile(), VERSION_REMEMBERANCE_FILE)));
		} catch (final Exception e) {
			versionFromLastRun = new Version(0, 5, 0); // oold
		}

		instance = this;

		loadActiveEngine();

		try {
			APIReflection.call(ResourcesPlugin.getWorkspace(), "addSaveParticipant", PLUGIN_ID, APIReflection.typed(this, ISaveParticipant.class));
		} catch (NoSuchMethodException | SecurityException e) {
			ResourcesPlugin.getWorkspace().addSaveParticipant(this, this);
		}

		registerStructureClasses();

		// react to active engine being changed
		getPreferenceStore().addPropertyChangeListener(new IPropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent event) {
				if (event.getProperty().equals(ClonkPreferences.ACTIVE_ENGINE))
					setActiveEngineByName(ClonkPreferences.value(ClonkPreferences.ACTIVE_ENGINE));
				else if (event.getProperty().equals(ClonkPreferences.PREFERRED_LANGID))
					for (final Engine e : loadedEngines())
						e.loadDeclarations();
			}
		});

		if (wasUpdated())
			informAboutUpdate(versionFromLastRun, getBundle().getVersion());
	}

	private static boolean versionAtLeast(Version version, int major, int minor, int micro) {
		return
			version.getMajor() >= major &&
			(minor == -1 || version.getMinor() >= minor) &&
			(micro == -1 || version.getMicro() >= micro);
	}

	private static boolean versionGap(Version oldVersion, Version newVersion, int baselineMajor, int baselineMinor, int baselineMicro) {
		return
			versionAtLeast(newVersion, baselineMajor, baselineMinor, baselineMicro) &&
			!versionAtLeast(oldVersion, baselineMajor, baselineMinor, baselineMicro);
	}

	private void informAboutUpdate(Version oldVersion, Version newVersion) {
		// only if there are projects at all
		if (ClonkProjectNature.clonkProjectsInWorkspace().length > 0)
			if (versionGap(oldVersion, newVersion, 1, 5, 9))
				UI.message(Messages.UpdateNotes_1_5_9);
	}

	private void registerStructureClasses() {
		IniUnit.register();
		StringTbl.register();
		LandscapeScript.register();
		SystemScript.register();
	}

	private String engineNameFromPath(String path) {
		final String folderName = path.endsWith("/") //$NON-NLS-1$
			? path.substring(path.lastIndexOf('/', path.length()-2)+1, path.length()-1)
			: path.substring(path.lastIndexOf('/')+1);
		return folderName.startsWith(".") ? null : folderName; //$NON-NLS-1$
	}

	public List<String> namesOfAvailableEngines() {
		final List<String> result = new LinkedList<String>();
		// get built-in engine definitions
		for (final Enumeration<String> paths = getBundle().getEntryPaths("res/engines"); paths.hasMoreElements();) { //$NON-NLS-1$
			final String engineName = engineNameFromPath(paths.nextElement());
			if (engineName != null)
				result.add(engineName);
		}
		// get engine definitions from workspace
		final File[] workspaceEngines = workspaceStorageLocationForEngines().toFile().listFiles();
		if (workspaceEngines != null)
			for (final File wEngine : workspaceEngines) {
				// only accepting folders should be sufficient
				if (!wEngine.isDirectory())
					continue;
				final String engineName = engineNameFromPath(new Path(wEngine.getAbsolutePath()).toString());
				if (engineName != null && !result.contains(engineName))
					result.add(engineName);
			}
		return result;
	}

	public Iterable<Engine> loadedEngines() {
		return new Iterable<Engine>() {
			@Override
			public Iterator<Engine> iterator() {
				return new ReadOnlyIterator<Engine>(loadedEngines.values().iterator());
			}
		};
	}

	public void reloadEngines() {
		final String[] ens = loadedEngines.keySet().toArray(new String[loadedEngines.keySet().size()]);
		loadedEngines.clear();
		for (final String e : ens)
			loadEngine(e);
	}

	public Engine loadEngine(final String engineName) {
		if (engineName == null || engineName.equals("")) //$NON-NLS-1$
			return null;
		synchronized (loadedEngines) {
			Engine result = loadedEngines.get(engineName);
			if (result != null)
				return result;
			IStorageLocation[] locations;
			if (getBundle() != null)
				// bundle given; assume the usual storage locations (workspace and plugin bundle contents) are present
				locations = storageLocations(engineName);
			else
				// no bundle? seems to run headlessly
				locations = headlessStorageLocations(engineName);
			result = Engine.loadFromStorageLocations(locations);
			if (result != null)
				loadedEngines.put(engineName, result);
			return result;
		}
	}

	private IStorageLocation[] storageLocations(final String engineName) {
		return new IStorageLocation[] {
			new FolderStorageLocation(engineName) {
				@Override
				protected IPath storageLocationForEngine(String engineName) {
					return workspaceStorageLocationForEngine(engineName);
				}
				@Override
				public File toFolder() {
					return new File(workspaceStorageLocationForActiveEngine().toOSString());
				};
			},

			new IStorageLocation() {
				@Override
				public URL locatorForEntry(String entryName, boolean create) {
					return create ? null : getBundle().getEntry(String.format("res/engines/%s/%s", engineName, entryName)); //$NON-NLS-1$
				}
				@Override
				public String name() {
					return engineName;
				}
				@Override
				public OutputStream outputStreamForURL(URL storageURL) {
					return null;
				}
				@Override
				public void collectURLsOfContainer(String containerPath, boolean recurse, List<URL> listToAddTo) {
					final Enumeration<URL> urls = Core.instance().getBundle().findEntries(String.format("res/engines/%s/%s", engineName, containerPath), "*.*", recurse); //$NON-NLS-1$ //$NON-NLS-2$
					containerPath = name() + "/" + containerPath;
					if (urls != null)
						while (urls.hasMoreElements()) {
							final URL url = urls.nextElement();
							PathUtil.addURLIfNotDuplicate(containerPath, url, listToAddTo);
						}
				};
				@Override
				public File toFolder() {
					return null;
				};
			}
		};
	}

	private IStorageLocation[] headlessStorageLocations(String engineName) {
		return new IStorageLocation[] {
			new FolderStorageLocation(engineName) {
				private final IPath storageLocationPath = new Path(engineConfigurationFolder).append(this.engineName);
				@Override
				protected IPath storageLocationForEngine(String engineName) {
					return storageLocationPath;
				}
				@Override
				public File toFolder() {
					return new File(storageLocationPath.toOSString());
				};
			}
		};
	}

	public void loadActiveEngine() {
		setActiveEngineByName(ClonkPreferences.value(ClonkPreferences.ACTIVE_ENGINE));
	}

	public IPath workspaceStorageLocationForActiveEngine() {
		return workspaceStorageLocationForEngine(ClonkPreferences.value(ClonkPreferences.ACTIVE_ENGINE));
	}

	public IPath workspaceStorageLocationForEngine(String engineName) {
		IPath path = workspaceStorageLocationForEngines();
		path = path.append(String.format("%s", engineName));
		final File dir = path.toFile();
		if (!dir.exists())
			dir.mkdir();
		return path;
	}

	private IPath workspaceStorageLocationForEngines() {
	    return getStateLocation().append("engines"); //$NON-NLS-1$
    }

	/**
	 * Request that a folder with the supplied name be created in the plugin state folder.<br>
	 * Utilization of the folder is up to the caller.
	 * @param name The name of the folder to create
	 * @return Reference to the folder
	 */
	public File requestFolderInStateLocation(String name) {
		final File result = new File(new File(getStateLocation().toOSString()), name);
		return result.mkdirs() ? result : null;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	@Override
	@SuppressWarnings("deprecation")
	public void stop(BundleContext context) throws Exception {
		//saveExternIndex(); clean build causes save
		try {
			APIReflection.call(ResourcesPlugin.getWorkspace(), "removeSaveParticipant", PLUGIN_ID);
		} catch (NoSuchMethodException | SecurityException e) {
			ResourcesPlugin.getWorkspace().removeSaveParticipant(this);
		}
		instance = null;
		super.stop(context);
	}

	public static boolean stopped() { return instance == null; }

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static Core instance() { return instance; }
	/** Whether the plugin runs in headless mode. */
	public boolean runsHeadless() { return runsHeadless; }

	public static void headlessInitialize(String engineConfigurationFolder, String engine) {
		if (instance == null) {
			instance = new Core(true);
			instance.engineConfigurationFolder = engineConfigurationFolder;
			instance.setActiveEngineByName(engine);
		}
	}

	/**
	 * Returns an image descriptor for the image file at the given
	 * plug-in relative path
	 *
	 * @param path the path
	 * @return the image descriptor
	 */
	public static ImageDescriptor imageDescriptorFor(String path) {
		return imageDescriptorFromPlugin(PLUGIN_ID, path);
	}

	/**
	 * Returns an icon image (uses image registry where possible). If the icon doesn't exist,
	 * a "missing" image is returned.
	 *
	 * @param iconName Name of the icon
	 */
	public Image iconImageFor(String iconName) {

		// Already exists?
		final ImageRegistry reg = getImageRegistry();
		Image img = reg.get(iconName);
		if (img != null)
			return img;

		// Create
		final ImageDescriptor descriptor = iconImageDescriptorFor(iconName);
		reg.put(iconName, img = descriptor.createImage(true));
		return img;
	}

	public ImageDescriptor iconImageDescriptorFor(String iconName) {
		ImageDescriptor descriptor = imageDescriptorFor("icons/" + iconName + ".png"); //$NON-NLS-1$ //$NON-NLS-2$
		if(descriptor == null)
			descriptor = ImageDescriptor.getMissingImageDescriptor();
		return descriptor;
	}

	@Override
	public void doneSaving(ISaveContext context) {}

	@Override
	public void prepareToSave(ISaveContext context) throws CoreException {}

	@Override
	public void rollback(ISaveContext context) {}

	@Override
	public void saving(ISaveContext context) throws CoreException {
		ClonkProjectNature clonkProj;
		switch (context.getKind()) {
		case ISaveContext.PROJECT_SAVE:
			clonkProj = ClonkProjectNature.get(context.getProject());
			if (clonkProj != null)
				clonkProj.saveIndex();
			break;
		case ISaveContext.SNAPSHOT:
		case ISaveContext.FULL_SAVE:
			rememberCurrentVersion();
			for (final Engine engine : loadedEngines.values())
				engine.saveSettings();
			for (final IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects())
				try {
					if (!project.isOpen())
						continue;
					clonkProj = ClonkProjectNature.get(project);
					if (clonkProj != null)
						clonkProj.saveIndex();
				} catch (final Exception e) {
					UI.informAboutException(Messages.ErrorWhileSavingIndex, e, project.getName());
				}
			removeOldIndexes();
			break;
		}
	}

	private void rememberCurrentVersion() {
		final File currentVersionMarker = new File(getStateLocation().toFile(), VERSION_REMEMBERANCE_FILE);
		try {
			StreamUtil.writeToFile(currentVersionMarker, new StreamWriteRunnable() {
				@Override
				public void run(File file, OutputStream stream, OutputStreamWriter writer) throws IOException {
					writer.append(getBundle().getVersion().toString());
				}
			});
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

	private static void removeRecursively(File f) {
		if (f.isDirectory())
			for (final File fi : f.listFiles())
				removeRecursively(fi);
		f.delete();
	}

	private void removeOldIndexes() {
		final File stateDir = getStateLocation().toFile();
		for (final String file : stateDir.list())
			if (file.endsWith(ProjectIndex.INDEXFILE_SUFFIX) && ResourcesPlugin.getWorkspace().getRoot().findMember(file.substring(0, file.length()-ProjectIndex.INDEXFILE_SUFFIX.length())) == null)
				removeRecursively(new File(stateDir, file));
	}

	/**
	 * Prepend {@link #PLUGIN_ID} to the parameter, making it a complete plugin id
	 * @param id The parameter to make a full plugin-id from
	 * @return The plugin id
	 */
	public static String id(String id) {
		return PLUGIN_ID + "." + id; //$NON-NLS-1$
	}

	/**
	 * @param activeEngine the engineObject to set
	 */
	private void setActiveEngine(Engine activeEngine) {
		this.activeEngine = activeEngine;
	}

	public void setActiveEngineByName(String engineName) {
		final Engine e = loadEngine(engineName);
		// make sure names are correct
		if (e != null) {
			e.setName(engineName);
			setActiveEngine(e);
		}
	}

	/**
	 * @return the engineObject
	 */
	public Engine activeEngine() {
		return activeEngine;
	}

	public interface IDocumentAction<T> {
		T run(IDocument document);
	}

	public <T> T performActionsOnFileDocument(IStorage file, IDocumentAction<T> action, boolean save) {
		synchronized (textFileDocumentProvider) {
			try {
				textFileDocumentProvider.connect(file);
			} catch (final CoreException e) {
				e.printStackTrace();
				return null;
			}
			try {
				final IDocument document = textFileDocumentProvider.getDocument(file);
				final T result = action.run(document);
				if (save)
					try {
						textFileDocumentProvider.setEncoding(document, textFileDocumentProvider.getDefaultEncoding());
						textFileDocumentProvider.saveDocument(null, file, document, true);
					} catch (final CoreException e) {
						e.printStackTrace();
					}
				return result;
			} finally {
				textFileDocumentProvider.disconnect(file);
			}
		}
	}

	@Override
	public void resourceChanged(IResourceChangeEvent event) {
		try {
			switch (event.getType()) {
			case IResourceChangeEvent.PRE_DELETE:
				// delete old index - could be renamed i guess but renaming a project is not exactly a common activity
				if (event.getResource() instanceof IProject && ((IProject)event.getResource()).hasNature(NATURE_ID)) {
					final ClonkProjectNature proj = ClonkProjectNature.get(event.getResource());
					Core.instance().getStateLocation().append(proj.getProject().getName()+ProjectIndex.INDEXFILE_SUFFIX).toFile().delete();
				}
			}
		} catch (final CoreException e) {
			e.printStackTrace();
		}
	}

	public boolean wasUpdated() {
		return !getBundle().getVersion().equals(versionFromLastRun);
	}

	@Override
	public IPreferenceStore getPreferenceStore() {
		try {
			return super.getPreferenceStore();
		} catch (final NullPointerException npe) {
			return null;
		}
	}

}
