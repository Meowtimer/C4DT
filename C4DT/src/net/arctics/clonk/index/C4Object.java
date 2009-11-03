package net.arctics.clonk.index;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.arctics.clonk.parser.C4ID;
import net.arctics.clonk.parser.c4script.C4ScriptBase;
import net.arctics.clonk.parser.c4script.FindDeclarationInfo;
import net.arctics.clonk.preferences.ClonkPreferences;

public abstract class C4Object extends C4ScriptBase {

	private static final long serialVersionUID = 1L;
	
	/**
	 * localized name of the object; key is language code like "DE" and "US"
	 */
	private Map<String, String> localizedNames;
	
	/**
	 * id of the object
	 */
	protected C4ID id;
	
	/**
	 * Template to construct the info text of an object definition from
	 */
	public static final String INFO_TEXT_TEMPLATE = Messages.C4Object_InfoTextTemplate;
	
	/**
	 * Creates a new C4Object
	 * @param id C4ID (e.g. CLNK)
	 * @param name human-readable name
	 */
	protected C4Object(C4ID id, String name) {
		this.id = id;
		this.name = name;
	}

	@Override
	public String toString() {
		return getName() + (id != null ? " (" + id.toString() + ")" : ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
	
	/**
	 * The id of this object. (e.g. CLNK)
	 * @return the id
	 */
	public C4ID getId() {
		return id;
	}
	
	/**
	 * Sets the id property of this object.
	 * This method does not change resources.
	 * @param newId
	 */
	public void setId(C4ID newId) {
		if (id.equals(newId))
			return;
		ClonkIndex index = this.getIndex();
		index.removeObject(this);
		id = newId;
		index.addObject(this);
	}
	

	@Override
	protected boolean refersToThis(String name, FindDeclarationInfo info) {
		if (info.getDeclarationClass() == null || info.getDeclarationClass() == C4Object.class) {
			if (id != null && id.getName().equals(name))
				return true;
		}
		return false;
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
		if (getId().getName().indexOf(text) != -1)
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
	protected void gatherIncludes(List<C4ScriptBase> list, ClonkIndex index) {
		super.gatherIncludes(list, index);
		if (index != null) {
			List<C4ScriptBase> appendages = index.appendagesOf(this);
			if (appendages != null)
				list.addAll(appendages);
		}
	}
	
}
