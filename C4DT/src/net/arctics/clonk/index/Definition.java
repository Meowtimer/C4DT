package net.arctics.clonk.index;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.swt.graphics.Image;

import net.arctics.clonk.ClonkCore;
import net.arctics.clonk.parser.Declaration;
import net.arctics.clonk.parser.ID;
import net.arctics.clonk.parser.c4script.ConstrainedType;
import net.arctics.clonk.parser.c4script.ScriptBase;
import net.arctics.clonk.parser.c4script.PrimitiveType;
import net.arctics.clonk.parser.c4script.Variable;
import net.arctics.clonk.parser.c4script.FindDeclarationInfo;
import net.arctics.clonk.parser.c4script.IType;
import net.arctics.clonk.preferences.ClonkPreferences;
import net.arctics.clonk.util.ArrayUtil;

/**
 * A Clonk object definition.
 * @author madeen
 *
 */
public abstract class Definition extends ScriptBase implements IType {

	private static final long serialVersionUID = ClonkCore.SERIAL_VERSION_UID;

	/**
	 * Template to construct the info text of an object definition from
	 */
	public static final String INFO_TEXT_TEMPLATE = Messages.C4Object_InfoTextTemplate;

	/**
	 * localized name of the object; key is language code like "DE" and "US"
	 */
	private Map<String, String> localizedNames;

	/**
	 * id of the object
	 */
	protected ID id;

	/**
	 * Cached picture from Graphics.png
	 */
	private transient Image cachedPicture;

	private transient ConstrainedType objectType;

	/**
	 * Creates a new C4Object
	 * @param id C4ID (e.g. CLNK)
	 * @param name human-readable name
	 */
	protected Definition(ID id, String name) {
		this.id = id;
		this.name = name;
	}

	@Override
	public String toString() {
		return getName() + (id != null ? " (" + id.toString() + ")" : ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	public String idWithName() {
		return getId() != null ? String.format(Messages.C4Object_IDWithName, getName(), getId().toString()) : getName();
	}

	/**
	 * The id of this object. (e.g. CLNK)
	 * @return the id
	 */
	public ID getId() {
		return id;
	}

	/**
	 * Sets the id property of this object.
	 * This method does not change resources.
	 * @param newId
	 */
	public void setId(ID newId) {
		if (id.equals(newId))
			return;
		ClonkIndex index = this.getIndex();
		index.removeObject(this);
		id = newId;
		index.addObject(this);
	}

	public Variable getStaticVariable() {
		return null;
	}

	@Override
	protected Declaration getThisDeclaration(String name, FindDeclarationInfo info) {
		Class<?> cls = info.getDeclarationClass();
		boolean variableRequired = false;
		if (
				cls == null ||
				cls == Definition.class ||
				(getEngine() != null && getEngine().getCurrentSettings().definitionsHaveStaticVariables && (variableRequired = Variable.class.isAssignableFrom(cls)))
		) {
			if (id != null && id.getName().equals(name))
				return variableRequired ? this.getStaticVariable() : this;
		}
		return null;
	}

	private static Pattern langNamePairPattern = Pattern.compile("(..):(.*)"); //$NON-NLS-1$

	public void readNames(String namesText) throws IOException {
		Matcher matcher = langNamePairPattern.matcher(namesText);
		if (localizedNames == null)
			localizedNames = new HashMap<String, String>();
		else
			localizedNames.clear();
		while (matcher.find()) {
			localizedNames.put(matcher.group(1), matcher.group(2));
		}
		chooseLocalizedName();
	}

	public void chooseLocalizedName() {
		if (localizedNames != null) {
			String preferredName = localizedNames.get(ClonkPreferences.getLanguagePref());
			if (preferredName != null)
				setName(preferredName);
		}
	}

	public Map<String, String> getLocalizedNames() {
		return localizedNames;
	}

	@Override
	public boolean nameContains(String text) {
		if (getId().getName().toUpperCase().indexOf(text) != -1)
			return true;
		if (getName().toUpperCase().contains(text))
			return true;
		if (localizedNames != null) {
			for (String key : localizedNames.keySet()) {
				String value = localizedNames.get(key);
				if (value.toUpperCase().indexOf(text) != -1)
					return true;
			}
		}
		return false;
	}

	@Override
	protected void gatherIncludes(List<ScriptBase> list, ClonkIndex index) {
		super.gatherIncludes(list, index);
		if (index != null) {
			List<ScriptBase> appendages = index.appendagesOf(this);
			if (appendages != null)
				list.addAll(appendages);
		}
	}

	@Override
	protected void finalize() throws Throwable {
		if (cachedPicture != null)
			cachedPicture.dispose();
		super.finalize();
	}

	public Image getCachedPicture() {
		return cachedPicture;
	}

	public void setCachedPicture(Image cachedPicture) {
		this.cachedPicture = cachedPicture;
	}

	@Override
	public boolean canBeAssignedFrom(IType other) {
		return PrimitiveType.OBJECT.canBeAssignedFrom(other) || PrimitiveType.PROPLIST.canBeAssignedFrom(other);
	}

	@Override
	public boolean containsType(IType type) {
		return
			type == PrimitiveType.OBJECT ||
			type == PrimitiveType.PROPLIST ||
			type == this ||
			type == PrimitiveType.ID; // gets rid of type sets <id or Clonk>
	}
	
	@Override
	public boolean containsAnyTypeOf(IType... types) {
		return IType.Default.containsAnyTypeOf(this, types);
	}

	@Override
	public int specificness() {
		return PrimitiveType.OBJECT.specificness()+1;
	}

	@Override
	public String typeName(boolean special) {
		return getName();
	}

	@Override
	public Iterator<IType> iterator() {
		return ArrayUtil.arrayIterable(new IType[] {PrimitiveType.OBJECT, this}).iterator();
	}

	@Override
	public boolean intersects(IType typeSet) {
		for (IType t : typeSet) {
			if (t.canBeAssignedFrom(PrimitiveType.OBJECT))
				return true;
			if (t instanceof Definition) {
				Definition obj = (Definition) t;
				if (this.includes(obj))
					return true;
			}
		}
		return false;
	}

	@Override
	public IType staticType() {
		return PrimitiveType.OBJECT;
	}

	public ConstrainedType getObjectType() {
		if (objectType == null) {
			objectType = new ConstrainedType(this, ConstraintKind.Exact);
		}
		return objectType;
	}

}