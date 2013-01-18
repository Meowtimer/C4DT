package net.arctics.clonk.parser;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.arctics.clonk.Core;
import net.arctics.clonk.index.Definition;
import net.arctics.clonk.index.Engine;
import net.arctics.clonk.index.IHasSubDeclarations;
import net.arctics.clonk.index.IIndexEntity;
import net.arctics.clonk.index.Index;
import net.arctics.clonk.index.IndexEntity;
import net.arctics.clonk.index.ProjectIndex;
import net.arctics.clonk.index.Scenario;
import net.arctics.clonk.parser.c4script.DeclarationObtainmentContext;
import net.arctics.clonk.parser.c4script.FindDeclarationInfo;
import net.arctics.clonk.parser.c4script.Function;
import net.arctics.clonk.parser.c4script.Script;
import net.arctics.clonk.parser.c4script.TypeUtil;
import net.arctics.clonk.parser.stringtbl.StringTbl;
import net.arctics.clonk.preferences.ClonkPreferences;
import net.arctics.clonk.resource.ClonkProjectNature;
import net.arctics.clonk.resource.ProjectSettings.Typing;
import net.arctics.clonk.util.ArrayUtil;
import net.arctics.clonk.util.IHasRelatedResource;
import net.arctics.clonk.util.INode;
import net.arctics.clonk.util.Sink;
import net.arctics.clonk.util.Utilities;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.text.IRegion;

/**
 * Base class for all declarations (object definitions, actmaps, functions, variables etc)
 * @author madeen
 *
 */
public abstract class Declaration extends ASTNode implements Serializable, IHasRelatedResource, INode, IHasSubDeclarations, IIndexEntity, IAdaptable {

	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
	
	protected Declaration() {}
	protected Declaration(int start, int end) { super(start, end); }
	
	/**
	 * The name of this declaration
	 */
	protected String name;
	
	/**
	 * result to be returned of occurenceScope if there is no scope
	 */
	//private static final Object[] EMPTY_SCOPE = new IResource[0];

	/**
	 * @return the name
	 */
	@Override
	public String name() {
		return name;
	}
	
	/**
	 * Sets the name.
	 * @param name the new name
	 */
	public void setName(String name) {
		this.name = name;
	}
	
	/**
	 * @param Set the location of the declaration in its declaring file.
	 */
	public void setLocation(SourceLocation location) {
		this.start = location != null ? location.start() : 0;
		this.end = location != null ? location.end() : 0;
	}
	
	/**
	 * Return the region to be selected when using editor navigation commands such as jump to definition. By default, this method returns this object since it already is a location.
	 * @return The region to select when using editor navigation commands
	 */
	public IRegion regionToSelect() {
		return this;
	}
	
	/**
	 * Returns an integer that is supposed to be different for different types of declarations (functions, variables)
	 * so that sorting of declarations by type is possible based on this value.
	 * @return the category value
	 */
	public int sortCategory() {
		return 0;
	}
	
	/**
	 * Set the script of this declaration.
	 * @param script the object to set
	 */
	public void setScript(Script script) {
		setParentDeclaration(script);
	}
	
	/**
	 * Same as {@link #parentOfType(Class)}, but will return the last parent declaration matching the type instead of the first one.
	 * @param type
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public final <T> T topLevelParentDeclarationOfType(Class<T> type) {
		T result = null;
		for (ASTNode f = this; f != null; f = f.parent)
			if (type.isAssignableFrom(f.getClass()))
				result = (T) f;
		return result;
	}
	
	/**
	 * Returns the top-level {@link Structure} this declaration is declared in.
	 * @return the {@link Structure}
	 */
	public Structure topLevelStructure() {
		return topLevelParentDeclarationOfType(Structure.class);
	}
	
	/**
	 * Returns the {@link Script} this declaration is declared in.
	 * @return the {@link Script}
	 */
	public Script script() {
		return topLevelParentDeclarationOfType(Script.class);
	}
	
	/**
	 * Return the {@link Scenario} this declaration is declared in.
	 * @return The {@link Scenario}
	 */
	public Scenario scenario() {
		return parent != null ? ((Declaration)parent).scenario() : null;
	}
	
	/**
	 * Sets the parent declaration of this declaration.
	 * @param field the new parent declaration
	 */
	public void setParentDeclaration(Declaration field) {
		this.parent = field;
	}
	
	/**
	 * Returns a brief info string describing the declaration. Meant for UI presentation.
	 * @return The short info string.
	 */
	@Override
	public String infoText(IIndexEntity context) {
		return name();
	}
	
	public String displayString(IIndexEntity context) {
		return infoText(this);
	}
	
	/**
	 * Returns an array of all sub declarations meant to be displayed in the outline.
	 * @return
	 */
	public Object[] subDeclarationsForOutline() {
		return null;
	}

	/**
	 * Returns the latest version of this declaration, obtaining it by searching for a declaration with its name in its parent declaration
	 * @return The latest version of this declaration
	 */
	public Declaration latestVersion() {
		Declaration parent = parentDeclaration();
		if (parent != null)
			parent = parent.latestVersion();
		if (parent instanceof ILatestDeclarationVersionProvider) {
			Declaration latest = ((ILatestDeclarationVersionProvider)parent).latestVersionOf(this);
			if (latest != null)
				return latest;
			else
				// fallback on returning this declaration if latest version providing not properly implemented
				return this;
		}
		else
			return this;
	}
	
	/**
	 * Returns the name of this declaration
	 */
	@Override
	public String toString() {
		return name != null ? name : getClass().getSimpleName();
	}
	
	/**
	 * Returns the objects this declaration might be referenced in (includes {@link Function}s, {@link IResource}s and other {@link Script}s)
	 * @param project
	 * @return
	 */
	public Object[] occurenceScope(ClonkProjectNature project) {
		final Set<Object> result = new LinkedHashSet<Object>();
		// first, add the script this declaration is declared in. Matches will most likely be found in there
		// so it helps to make it the first item to be searched
		if (parent instanceof Script)
			result.add(parent);
		// next, in case of a definition, add items including the definition, so matches in derived definitions
		// will be found next
		if (parent instanceof Definition) {
			// first, add the definition this 
			final Definition def = (Definition)parent;
			final Index projectIndex = project.index();
			result.add(def);
			def.index().allDefinitions(new Sink<Definition>() {
				@Override
				public void receivedObject(Definition item) {
					result.add(item);
				}
				@Override
				public boolean filter(Definition item) {
					return item.doesInclude(projectIndex, def);
				}
			});
		}
		// then add all the scripts, because everything might potentially be accessed from everything
		for (Index index : project.index().relevantIndexes())
			index.allScripts(new Sink<Script>() {
				@Override
				public void receivedObject(Script item) {
					result.add(item);
				}
			});
		return result.toArray();
	}
	
	/**
	 * Returns the resource this declaration is declared in
	 */
	@Override
	public IResource resource() {
		return parentDeclaration() != null ? parentDeclaration().resource() : null;
	}
	
	/**
	 * Returns the parent declaration this one is contained in
	 * @return
	 */
	public Declaration parentDeclaration() {
		return (Declaration)parent;
	}
	
	protected static final Iterable<Declaration> NO_SUB_DECLARATIONS = ArrayUtil.iterable();
	
	/**
	 * Returns an Iterable for iterating over all sub declaration of this declaration.
	 * Might return null if there are none.
	 * @return The Iterable for iterating over sub declarations or null.
	 */
	@Override
	public Iterable<? extends Declaration> subDeclarations(Index contextIndex, int mask) {
		return NO_SUB_DECLARATIONS;
	}
	
	/**
	 * Return {@link #subDeclarations(Index, int)} with the contextIndex parameter set to {@link #index()}
	 * @param mask The mask, passed on to {@link #subDeclarations(Index, int)}
	 * @return Result of {@link #subDeclarations(Index, int)}
	 */
	public final Iterable<? extends Declaration> accessibleDeclarations(int mask) {
		return subDeclarations(index(), mask);
	}
	
	@Override
	public Function findFunction(String functionName) {
		return null;
	}
	
	@Override
	public Declaration findDeclaration(String name, FindDeclarationInfo info) {
		return null;
	}
	
	/**
	 * Adds a sub-declaration
	 * @param declaration
	 */
	public void addSubDeclaration(Declaration declaration) {
		System.out.println(String.format("Attempt to add sub declaration %s to %s", declaration, this));
	}
	
	/**
	 * Called after deserialization to restore transient references
	 * @param parent the parent
	 */
	public void postLoad(Declaration parent, Index index) {
		if (name != null)
			name = name.intern();
		setParentDeclaration(parent);
		Iterable<? extends Declaration> subDecs = this.accessibleDeclarations(ALL);
		if (subDecs != null)
			for (Declaration d : subDecs)
				d.postLoad(this, index);
	}
	
	/**
	 * Returns whether this declaration is global (functions are global when declared as "global" while variables are global when declared as "static") 
	 * @return true if global, false if not
	 */
	public boolean isGlobal() {
		return false;
	}
	
	/**
	 * Used to filter declarations based on their name
	 * @param matcher The matcher, obtained from a {@link Pattern}, that will be {@link Matcher#reset(CharSequence)} with all the strings the user might want to filter for in order to refer to this declaration.
	 * @return whether this declaration should be filtered out (false) or not (true)
	 */
	@Override
	public boolean matchedBy(Matcher matcher) {
		if (name() != null && matcher.reset(name()).lookingAt())
			return true;
		if (topLevelStructure() != null && topLevelStructure() != this && topLevelStructure().matchedBy(matcher))
			return true;
		return false;
	}
	
	@Override
	public String nodeName() {
		return name();
	}
	
	/**
	 * Returns whether the supplied name looks like the name of a constant e.g begins with a prefix in caps followed by an underscore and a name
	 * @param name the string to check
	 * @return whether it does or not
	 */
	public static boolean looksLikeConstName(String name) {
		boolean underscore = false;
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (i > 0 && c == '_')
				if (!underscore)
					underscore = true;
				else
					return false;
			if (!underscore)
				if (Character.toUpperCase(c) != c)
					return false;
		}
		return underscore || name.equals(name.toUpperCase());
	}

	public boolean isEngineDeclaration() {
		return parentDeclaration() instanceof Engine;
	}
	
	public Engine engine() {
		Declaration parent = parentDeclaration();
		return parent != null ? parent.engine() : null; 
	}

	@Override
	public Index index() {
		if (parentDeclaration() != null)
			return parentDeclaration().index();
		else {
			IResource res = resource();
			if (res != null) {
				ClonkProjectNature nat = ClonkProjectNature.get(res);
				return nat != null ? nat.index() : null;
			}
			else
				return null;
		}
	}
	
	public void sourceCodeRepresentation(StringBuilder builder, Object cookie) {
		System.out.println("dunno");
	}
	
	public StringTbl localStringTblMatchingLanguagePref() {
		IResource res = resource();
		if (res == null)
			return null;
		IContainer container = res instanceof IContainer ? (IContainer) res : res.getParent();
		String pref = ClonkPreferences.languagePref();
		IResource tblFile = Utilities.findMemberCaseInsensitively(container, "StringTbl"+pref+".txt"); //$NON-NLS-1$ //$NON-NLS-2$
		if (tblFile instanceof IFile)
			return (StringTbl) Structure.pinned(tblFile, true, false);
		return null;
	}
	
	public int absoluteExpressionsOffset() {return 0;}
	
	/**
	 * Return a {@link DeclarationObtainmentContext} describing the surrounding environment of this {@link Declaration}. 
	 * @return The context
	 */
	public DeclarationObtainmentContext declarationObtainmentContext() {
		return TypeUtil.declarationObtainmentContext(this);
	}
	
	public DeclarationLocation[] declarationLocations() {
		return new DeclarationLocation[] {
			new DeclarationLocation(this, this, resource())
		};
	}
	
	/**
	 * Return a name that uniquely identifies the declaration in its script
	 * @return The unique name
	 */
	public String makeNameUniqueToParent() {
		int othersWithSameName = 0;
		int ownIndex = -1;
		for (Declaration d : parentDeclaration().accessibleDeclarations(ALL|OTHER))
			if (d == this) {
				ownIndex = othersWithSameName++;
				continue;
			}
			else if (d.name().equals(this.name()))
				othersWithSameName++;
		if (othersWithSameName == 1)
			return name();
		else
			return name() + ownIndex;
	}
	
	/**
	 * Return the {@link Declaration}'s path, which is a concatenation of its parent declaration's and its own name, separated by '.'
	 * Concatenating the path will stop at the earliest {@link IndexEntity} in the declaration hierarchy.
	 * @return The path
	 */
	public String pathRelativeToIndexEntity() {
		StringBuilder builder = new StringBuilder();
		for (Declaration d = this; d != null; d = d.parentDeclaration())
			if (d instanceof IndexEntity)
				break;
			else {
				if (builder.length() > 0)
					builder.insert(0, '.');
				builder.insert(0, d.name());
			}
		return builder.toString();
	}
	
	/**
	 * Return a string identifying the declaration and the {@link Script} it's declared in.
	 * @return
	 */
	public String qualifiedName() {
		if (parentDeclaration() != null)
			return String.format("%s::%s", parentDeclaration().qualifiedName(), this.name());
		else
			return name();
	}
	
	/**
	 * Whether this Declaration is contained in the given one.
	 * @param parent The declaration to check for parentedness
	 * @return true or false
	 */
	public boolean containedIn(Declaration parent) {
		for (Declaration d = this.parentDeclaration(); d != null; d = d.parentDeclaration())
			if (d == parent)
				return true;
		return false;
	}
	
	@Override
	public Object getAdapter(@SuppressWarnings("rawtypes") Class adapter) {
		return adapter.isInstance(this) ? this : null;
	}
	
	@Override
	public boolean equals(Object other) {
		return this == other; // identity
	}
	
	protected Typing typing() {
		Typing typing = Typing.ParametersOptionallyTyped;
		if (index() instanceof ProjectIndex)
			typing = ((ProjectIndex)index()).nature().settings().typing;
		return typing;
	}
	
}
