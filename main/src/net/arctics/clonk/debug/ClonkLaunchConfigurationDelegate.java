package net.arctics.clonk.debug;

import static net.arctics.clonk.util.Utilities.defaulting;

import java.io.File;
import java.util.Arrays;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.LaunchConfigurationDelegate;

import net.arctics.clonk.Core;
import net.arctics.clonk.builder.ClonkProjectNature;
import net.arctics.clonk.index.Engine;
import net.arctics.clonk.index.Index;
import net.arctics.clonk.index.ProjectIndex;
import net.arctics.clonk.util.Utilities;

public class ClonkLaunchConfigurationDelegate extends LaunchConfigurationDelegate {

	public static final String LAUNCH_TYPE = Core.id("debug.ClonkLaunch"); //$NON-NLS-1$
	public static final String ATTR_PROJECT_NAME = Core.id("debug.ProjectNameAttr"); //$NON-NLS-1$
	public static final String ATTR_SCENARIO_NAME = Core.id("debug.ScenarioNameAttr"); //$NON-NLS-1$
	public static final String ATTR_FULLSCREEN = Core.id("debug.FullscreenAttr"); //$NON-NLS-1$
	public static final String ATTR_RECORD = Core.id("debug.RecordAttr"); //$NON-NLS-1$
	public static final String ATTR_CUSTOMARGS = Core.id("debug.CustomArgs"); //$NON-NLS-1$
	public static int DEFAULT_DEBUG_PORT = 10464;

	@Override
	public synchronized void launch(final ILaunchConfiguration configuration, final String mode, final ILaunch launch, IProgressMonitor monitor) throws CoreException {
		if (monitor == null) {
			monitor = new NullProgressMonitor();
		}
		monitor.beginTask(String.format(Messages.LaunchConf, configuration.getName()), 2);
		try {
			// Get scenario and engine
			final IFolder scenario = verifyScenario(configuration);
			final File engine = verifyClonkInstall(configuration, scenario);
			final EngineLaunch launchling = new EngineLaunch(configuration, launch, scenario, engine, mode);
			launchling.launch(monitor);
		} finally {
			monitor.done();
		}
	}

	/**
	 * Searches the scenario to launch
	 */
	public IFolder verifyScenario(final ILaunchConfiguration configuration) throws CoreException {

		// Get project and scenario name from configuration
		final String projectName = configuration.getAttribute(ATTR_PROJECT_NAME, ""); //$NON-NLS-1$
		final String scenarioName = configuration.getAttribute(ATTR_SCENARIO_NAME, ""); //$NON-NLS-1$

		// Get project
		final IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		final IProject project = root.getProject(projectName);
		if (project == null || !project.isOpen()) {
			Utilities.abort(IStatus.ERROR, String.format(Messages.ProjectNotOpen, projectName));
		}

		// Get scenario
		final IFolder scenario = project.getFolder(scenarioName);
		if (scenario == null || !scenario.exists()) {
			Utilities.abort(IStatus.ERROR, String.format(Messages.ScenarioNotFound, projectName));
		}

		return scenario;
	}

	/**
	 * Searches an appropriate Clonk installation for launching the scenario.
	 *
	 * @param configuration
	 *            The launch configuration
	 * @param scenario
	 *            Scenario folder
	 * @return The path of the Clonk engine executable
	 */
	public File verifyClonkInstall(final ILaunchConfiguration configuration, final IFolder scenario) throws CoreException {
		final Index index = ProjectIndex.fromResource(scenario);
		final String gamePath = index != null ? index.engine().settings().gamePath : null;
		final ClonkProjectNature nature = ClonkProjectNature.get(scenario);
		final String enginePref = defaulting(
			nature != null && nature.settings().customEngineSettings() != null ? nature.settings().customEngineSettings().engineExecutablePath : null,
			index != null ? index.engine().settings().engineExecutablePath : null
		);
		final File enginePath = enginePref != null ? new File(enginePref) :
			Arrays.stream(Engine.possibleEngineNamesAccordingToOS())
				.map(name -> new File(gamePath, name))
				.filter(File::exists)
				.findFirst().orElse(null);

		if (enginePath == null || !enginePath.exists()) {
			Utilities.abort(IStatus.ERROR, String.format(
				Messages.CouldNotFindEngine,
				enginePath != null ? enginePath.getAbsolutePath() : "<no path>"
			));
		}

		return enginePath;
	}

	public static String resFilePath(final IResource res) {
		return new Path(res.getRawLocationURI().getSchemeSpecificPart()).toOSString();
	}

	static String cmdLineOptionString(final Engine engine, final String option) {
		return String.format(engine.settings().cmdLineOptionFormat, option);
	}

	static String cmdLineOptionString(final Engine engine, final String option, final String argument) {
		return String.format(engine.settings().cmdLineOptionWithArgumentFormat, option, argument);
	}
}
