package net.arctics.clonk;

import static java.util.Arrays.stream;
import static net.arctics.clonk.util.Utilities.consumingException;
import static net.arctics.clonk.util.Utilities.defaulting;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.ISaveContext;
import org.eclipse.core.resources.ISaveParticipant;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Version;

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
import net.arctics.clonk.util.UI;

/**
 * The core plug-in class.
 * The singleton instance of this class stores various global things, like engine objects and preferences.
 */
public class Core extends AbstractUIPlugin implements ISaveParticipant, IResourceChangeListener {

	public static final String HUMAN_READABLE_NAME = Messages.HumanReadableName;
	private static final String VERSION_REMEMBERANCE_FILE = "version.txt"; //$NON-NLS-1$

	/** plugin-id, same as Core's package */
	public static final String PLUGIN_ID = Core.class.getPackage().getName();

	/** id for Clonk project natures */
	public static final String NATURE_ID = id("clonknature"); //$NON-NLS-1$

	/** Binding context for Clonk related editing activities */
	public static final String CONTEXT_ID = id("context");

	/** id for error markers that denote errors in a script */
	public static final String MARKER_C4SCRIPT_ERROR = id("c4scripterror"); //$NON-NLS-1$

	/** id for error markers that denote errors in a ini file */
	public static final String MARKER_INI_ERROR = id("inierror"); //$NON-NLS-1$

	public static final String MARKER_ADDEDASTNODE = id("addedastnode");

	public static final QualifiedName FOLDER_C4ID_PROPERTY_ID = new QualifiedName(PLUGIN_ID, "c4id"); //$NON-NLS-1$
	public static final QualifiedName FOLDER_DEFINITION_REFERENCE_ID = new QualifiedName(PLUGIN_ID, "c4object"); //$NON-NLS-1$
	public static final QualifiedName FILE_STRUCTURE_REFERENCE_ID = new QualifiedName(PLUGIN_ID, "structure"); //$NON-NLS-1$

	public static final String MENU_GROUP_CLONK = id("ui.editors.actions.clonkGroup");

	public static final long SERIAL_VERSION_UID = 2L;

	/** The engine object contains global functions and variables defined by Clonk itself */
	private Engine activeEngine;

	/** List of engines currently loaded */
	private final Map<String, Engine> loadedEngines = new HashMap<String, Engine>();

	/** Shared instance */
	private static Core instance;

	private String engineConfigurationFolder;
	private Version versionFromLastRun;
	private boolean runsHeadless;

	public Version versionFromLastRun() {
		return versionFromLastRun;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
	 */
	@SuppressWarnings("deprecation")
	@Override
	public void start(final BundleContext context) throws Exception {
		super.start(context);
		try {
			versionFromLastRun = new Version(StreamUtil.stringFromFile(new File(getStateLocation().toFile(), VERSION_REMEMBERANCE_FILE)));
		} catch (final Exception e) {
			versionFromLastRun = new Version(0, 5, 0); // old
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
		getPreferenceStore().addPropertyChangeListener(event -> {
			if (event.getProperty().equals(ClonkPreferences.ACTIVE_ENGINE)) {
				setActiveEngineByName(ClonkPreferences.value(ClonkPreferences.ACTIVE_ENGINE));
			} else if (event.getProperty().equals(ClonkPreferences.PREFERRED_LANGID)) {
				for (final Engine e : loadedEngines()) {
					e.loadDeclarations();
				}
			}
		});
	}

	private void registerStructureClasses() {
		IniUnit.register();
		StringTbl.register();
		LandscapeScript.register();
		SystemScript.register();
	}

	private String engineNameFromPath(final String path) {
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
			if (engineName != null) {
				result.add(engineName);
			}
		}
		// get engine definitions from workspace
		final File[] workspaceEngines = workspaceStorageLocationForEngines().toFile().listFiles();
		if (workspaceEngines != null) {
			for (final File wEngine : workspaceEngines) {
				// only accepting folders should be sufficient
				if (!wEngine.isDirectory()) {
					continue;
				}
				final String engineName = engineNameFromPath(new Path(wEngine.getAbsolutePath()).toString());
				if (engineName != null && !result.contains(engineName)) {
					result.add(engineName);
				}
			}
		}
		return result;
	}

	public Iterable<Engine> loadedEngines() {
		return () -> new ReadOnlyIterator<Engine>(loadedEngines.values().iterator());
	}

	public void reloadEngines() {
		final String[] ens = loadedEngines.keySet().toArray(new String[loadedEngines.keySet().size()]);
		loadedEngines.clear();
		for (final String e : ens) {
			loadEngine(e);
		}
	}

	public Engine loadEngine(final String engineName) {
		if (engineName == null || engineName.equals("")) {
			return null;
		}
		synchronized (loadedEngines) {
			final Engine loaded = loadedEngines.get(engineName);
			if (loaded != null) {
				return loaded;
			}
			final IStorageLocation[] locations = getBundle() != null
				// bundle given; assume the usual storage locations (workspace and plugin bundle contents) are present
				? storageLocations(engineName)
				// no bundle? seems to run headlessly
				: headlessStorageLocations(engineName);
			final Engine engine = Engine.loadFromStorageLocations(locations);
			if (engine != null) {
				loadedEngines.put(engineName, engine);
			}
			return engine;
		}
	}

	private IStorageLocation[] storageLocations(final String engineName) {
		return new IStorageLocation[] {

			new FolderStorageLocation(engineName) {
				@Override
				protected IPath storageLocationForEngine(final String engineName) {
					return workspaceStorageLocationForEngine(engineName);
				}
			},

			new IStorageLocation() {
				@Override
				public URL locatorForEntry(final String entryName, final boolean create) {
					return create ? null : getBundle().getEntry(String.format("res/engines/%s/%s", engineName, entryName)); //$NON-NLS-1$
				}
				@Override
				public String name() {
					return engineName;
				}
				@Override
				public OutputStream outputStreamForURL(final URL storageURL) {
					return null;
				}
				@Override
				public void collectURLsOfContainer(String containerPath, final boolean recurse, final List<URL> listToAddTo) {
					final Enumeration<URL> urls = Core.instance().getBundle().findEntries(String.format("res/engines/%s/%s", engineName, containerPath), "*.*", recurse); //$NON-NLS-1$ //$NON-NLS-2$
					containerPath = name() + "/" + containerPath;
					if (urls != null) {
						while (urls.hasMoreElements()) {
							final URL url = urls.nextElement();
							PathUtil.addURLIfNotDuplicate(containerPath, url, listToAddTo);
						}
					}
				};
				@Override
				public File toFolder() {
					return null;
				};
			}

		};
	}

	private IStorageLocation[] headlessStorageLocations(final String engineName) {
		return new IStorageLocation[] {
			new FolderStorageLocation(engineName) {
				private final IPath storageLocationPath = new Path(engineConfigurationFolder).append(this.engineName);
				@Override
				protected IPath storageLocationForEngine(final String engineName) {
					return storageLocationPath;
				}
			}
		};
	}

	public void loadActiveEngine() {
		setActiveEngineByName(ClonkPreferences.value(ClonkPreferences.ACTIVE_ENGINE));
	}

	public IPath workspaceStorageLocationForActiveEngine() {
		return workspaceStorageLocationForEngine(ClonkPreferences.value(ClonkPreferences.ACTIVE_ENGINE));
	}

	public IPath workspaceStorageLocationForEngine(final String engineName) {
		final IPath path = workspaceStorageLocationForEngines()
			.append(String.format("%s", engineName));
		final File directory = path.toFile();
		if (!directory.exists()) {
			directory.mkdirs();
		}
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
	public File requestFolderInStateLocation(final String name) {
		final File result = new File(new File(getStateLocation().toOSString()), name);
		return result.mkdirs() ? result : null;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	@Override
	@SuppressWarnings("deprecation")
	public void stop(final BundleContext context) throws Exception {
		try {
			APIReflection.call(ResourcesPlugin.getWorkspace(), "removeSaveParticipant", PLUGIN_ID);
		} catch (NoSuchMethodException | SecurityException e) {
			ResourcesPlugin.getWorkspace().removeSaveParticipant(this);
		}
		instance = null;
		super.stop(context);
	}

	public static boolean stopped() { return instance == null; }

	/** Return the shared instance */
	public static Core instance() { return instance; }

	/** Whether the plug-in runs in headless mode. */
	public boolean runsHeadless() { return runsHeadless; }

	public static Core headlessInitialize(final String engineConfigurationFolder, final String engine) {
		return defaulting(
			instance,
			() -> {
				final Core instance = new Core();
				instance.runsHeadless = true;
				instance.engineConfigurationFolder = engineConfigurationFolder;
				instance.setActiveEngineByName(engine);
				return Core.instance = instance;
			}
		);
	}

	/**
	 * Returns an image descriptor for the image file at the given
	 * plug-in relative path
	 *
	 * @param path the path
	 * @return the image descriptor
	 */
	public static ImageDescriptor imageDescriptorFor(final String path) {
		return imageDescriptorFromPlugin(PLUGIN_ID, path);
	}

	/**
	 * Returns an icon image (uses image registry where possible). If the icon doesn't exist,
	 * a "missing" image is returned.
	 *
	 * @param iconName Name of the icon
	 */
	public Image iconImageFor(final String iconName) {
		// Already exists?
		final ImageRegistry imageRegistry = getImageRegistry();
		return defaulting(
			imageRegistry.get(iconName),
			() -> {
				// Create
				final ImageDescriptor descriptor = iconImageDescriptorFor(iconName);
				final Image image = descriptor.createImage(true);
				imageRegistry.put(iconName, image);
				return image;
			}
		);
	}

	public ImageDescriptor iconImageDescriptorFor(final String iconName) {
		return defaulting(
			imageDescriptorFor("icons/" + iconName + ".png"), //$NON-NLS-1$ //$NON-NLS-2$
			ImageDescriptor::getMissingImageDescriptor
		);
	}

	@Override
	public void doneSaving(final ISaveContext context) {}

	@Override
	public void prepareToSave(final ISaveContext context) throws CoreException {}

	@Override
	public void rollback(final ISaveContext context) {}

	@Override
	public void saving(final ISaveContext context) throws CoreException {
		switch (context.getKind()) {
		case ISaveContext.PROJECT_SAVE:
			{
				final ClonkProjectNature projectNature = ClonkProjectNature.get(context.getProject());
				if (projectNature != null) {
					projectNature.saveIndex();
				}
			}
			break;
		case ISaveContext.SNAPSHOT:
		case ISaveContext.FULL_SAVE:
			rememberCurrentVersion();
			loadedEngines.values().forEach(Engine::saveSettings);
			stream(ClonkProjectNature.clonkProjectsInWorkspace())
				.map(ClonkProjectNature::get)
				.filter(x -> x != null)
				.forEach(consumingException(
					ClonkProjectNature::saveIndex,
					(ClonkProjectNature nature, Exception e) -> {
						UI.informAboutException(Messages.ErrorWhileSavingIndex, e, nature.getProject().getName());
					}
				));
			break;
		}
	}

	private void rememberCurrentVersion() {
		final File currentVersionMarker = new File(getStateLocation().toFile(), VERSION_REMEMBERANCE_FILE);
		try {
			StreamUtil.writeToFile(currentVersionMarker, (file, stream, writer) -> writer.append(getBundle().getVersion().toString()));
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Prepend {@link #PLUGIN_ID} to the parameter, making it a complete plugin id
	 * @param id The parameter to make a full plugin-id from
	 * @return The plugin id
	 */
	public static String id(final String id) {
		return PLUGIN_ID + "." + id; //$NON-NLS-1$
	}

	/** @param activeEngine the engineObject to set */
	private void setActiveEngine(final Engine activeEngine) {
		this.activeEngine = activeEngine;
	}

	public void setActiveEngineByName(final String engineName) {
		final Engine e = loadEngine(engineName);
		// make sure names are correct
		if (e != null) {
			e.setName(engineName);
			setActiveEngine(e);
		}
	}

	/** @return the engineObject */
	public Engine activeEngine() {
		return activeEngine;
	}

	static IProgressMonitor NPM = new NullProgressMonitor();

	@Override
	public void resourceChanged(final IResourceChangeEvent event) {
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
