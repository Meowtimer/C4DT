package net.arctics.clonk.index;

import static net.arctics.clonk.util.Utilities.as;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;

import net.arctics.clonk.Core;
import net.arctics.clonk.ast.Declaration;
import net.arctics.clonk.ast.SourceLocation;
import net.arctics.clonk.ast.Structure;
import net.arctics.clonk.c4script.ProplistDeclaration;
import net.arctics.clonk.c4script.Variable;
import net.arctics.clonk.c4script.Variable.Scope;
import net.arctics.clonk.ini.ScenarioUnit;

/**
 * A scenario.
 * @author madeen
 *
 */
public class Scenario extends Definition {

	public static final String PROPLIST_NAME = "Scenario";
	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;

	private transient Declaration scenarioPropList; { createScenarioProplist(); }
	
	private Map<String, Variable> staticVariables;
	
	/**
	 * Set internal static variables map by collecting the provided stream.
	 * @param staticVariables Stream of {@link Variable} objects representing static variables
	 */
	public void setStaticVariables(Stream<Variable> staticVariables) {
		this.staticVariables = staticVariables
			.collect(Collectors.groupingBy(variable -> variable.name()))
			.entrySet().stream().collect(Collectors.toMap(
				group -> group.getKey(),
				group -> group.getValue().get(0)
			));
	}
	
	/**
	 * Get a static variable declared inside this scenario
	 * @param name Name of the static variable to get
	 * @return The static variable or null if it does not exist
	 */
	public Variable getStaticVariable(String name) {
		return staticVariables != null ? staticVariables.get(name) : null;
	}

	@Override
	public void deriveInformation() {
		super.deriveInformation();
		createScenarioProplist();
	}

	private synchronized Declaration createScenarioProplist() {
		if (scenarioPropList == null && engine().settings().supportsGlobalProplists) {
			final ProplistDeclaration type = new ProplistDeclaration(PROPLIST_NAME);
			type.setLocation(SourceLocation.ZERO);
			type.setParent(this);
			final Variable v = new Variable(PROPLIST_NAME, type);
			v.setParent(this);
			v.setScope(Scope.STATIC);
			scenarioPropList = v;
		}
		return scenarioPropList;
	}

	public Declaration propList() { return scenarioPropList; }

	@Override
	public void postLoad(final Declaration parent, final Index root) {
		createScenarioProplist();
		super.postLoad(parent, root);
	}

	public Scenario(final Index index, final String name, final IContainer container) {
		super(index, ID.get(container != null ? container.getName() : name), name, container);
	}

	@Override
	public void load(final ObjectInputStream stream) throws IOException, ClassNotFoundException {
		super.load(stream);
	}

	protected static class ScenarioSaveState extends SaveState {
		private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
		public ScenarioSaveState() {}
		public Declaration scenarioProplist;
	}

	@Override
	public SaveState makeSaveState() {
		final ScenarioSaveState state = new ScenarioSaveState();
		state.scenarioProplist = scenarioPropList;
		return state;
	}

	@Override
	public void extractSaveState(final SaveState state) {
		super.extractSaveState(state);
		scenarioPropList = ((ScenarioSaveState)state).scenarioProplist;
	}

	public static Scenario get(final IContainer folder) {
		final Definition obj = at(folder);
		return obj instanceof Scenario ? (Scenario)obj : null;
	}

	public static Scenario containingScenario(final IResource res) {
		if (res == null) {
			return null;
		}
		for (IContainer c = res instanceof IContainer ? (IContainer)res : res.getParent(); c != null; c = c.getParent()) {
			final Scenario s = get(c);
			if (s != null) {
				return s;
			}
		}
		return null;
	}

	public static Scenario nearestScenario(IResource resource) {
		Scenario scenario;
		for (scenario = null; scenario == null && resource != null; resource = resource.getParent()) {
			if (resource instanceof IContainer) {
				scenario = get((IContainer)resource);
			}
		}
		return scenario;
	}

	public ScenarioUnit scenarioConfiguration() {
		final IFile scenarioFile = as(definitionFolder().findMember(ScenarioUnit.FILENAME), IFile.class);
		return scenarioFile != null ? Structure.pinned(scenarioFile, true, false) : null;
	}

	@Override
	public Declaration findLocalDeclaration(final String declarationName, final Class<? extends Declaration> declarationClass) {
		return declarationName.equals(PROPLIST_NAME) ? scenarioPropList : super.findLocalDeclaration(declarationName, declarationClass);
	}

}
