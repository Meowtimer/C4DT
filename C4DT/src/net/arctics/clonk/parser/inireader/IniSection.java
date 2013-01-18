package net.arctics.clonk.parser.inireader;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.arctics.clonk.Core;
import net.arctics.clonk.index.IIndexEntity;
import net.arctics.clonk.parser.Declaration;
import net.arctics.clonk.parser.SourceLocation;
import net.arctics.clonk.parser.inireader.IniData.IniSectionDefinition;
import net.arctics.clonk.util.IHasChildren;
import net.arctics.clonk.util.IHasKeyAndValue;
import net.arctics.clonk.util.ITreeNode;
import net.arctics.clonk.util.ReadOnlyIterator;

import org.eclipse.core.runtime.IPath;

public class IniSection extends Declaration implements
		IHasKeyAndValue<String, String>, IHasChildren, Iterable<IniItem>,
		IniItem {

	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;

	private Map<String, IniItem> itemMap;
	private List<IniItem> itemList;
	private IniSectionDefinition definition;
	private int indentation;
	private int sectionEnd;

	public int sectionEnd() {
		return sectionEnd;
	}

	public void setSectionEnd(int sectionEnd) {
		this.sectionEnd = sectionEnd;
	}

	public IniSectionDefinition definition() {
		return definition;
	}

	public void setDefinition(IniSectionDefinition sectionData) {
		this.definition = sectionData;
	}

	protected IniSection(SourceLocation location, String name) {
		setLocation(location);
		this.name = name;
	}

	public Map<String, IniItem> subItemMap() {
		return itemMap;
	}

	public void setSubItems(Map<String, IniItem> map, List<IniItem> list) {
		this.itemMap = map;
		this.itemList = list;
	}

	public IniItem subItemByKey(String key) {
		return itemMap.get(key);
	}
	
	public List<IniItem> subItemList() {
		return itemList;
	}

	@Override
	public String key() {
		return name();
	}

	@Override
	public String stringValue() {
		return ""; //$NON-NLS-1$
	}

	@Override
	public Object[] children() {
		return subItemList().toArray(new Object[subItemList().size()]);
	}

	@Override
	public boolean hasChildren() {
		return !itemMap.isEmpty();
	}

	@Override
	public void setStringValue(String value, Object context) {
		// FIXME?
	}

	@Override
	public void addChild(ITreeNode node) {
		if (node instanceof IniItem)
			addItem((IniItem)node);
		else
			throw new IllegalArgumentException("node");
	}
	
	@SuppressWarnings("unchecked")
	public <T extends IniItem> T addItem(T item) {
		if (!itemMap.containsKey(item.key())) {
			itemMap.put(item.key(), item);
			itemList.add(item);
			((Declaration)item).setParentDeclaration(this);
			try {
				return item instanceof IniEntry
					? (T)topLevelParentDeclarationOfType(IniUnit.class).validateEntry((IniEntry)item, this, false)
					: item;
			} catch (IniParserException e) {
				return null;
			}
		}
		else
			throw new IllegalArgumentException("item");
	}
	
	public void removeItem(IniItem item) {
		itemMap.remove(item.key());
		itemList.remove(item);
	}

	@Override
	public Collection<? extends IniItem> childCollection() {
		return itemList;
	}

	@Override
	public String nodeName() {
		return name();
	}

	@Override
	public ITreeNode parentNode() {
		return null;
	}

	@Override
	public IPath path() {
		return ITreeNode.Default.path(this);
	}

	@Override
	public boolean subNodeOf(ITreeNode node) {
		return ITreeNode.Default.subNodeOf(this, node);
	}

	@Override
	public String toString() {
		IniUnit unit = iniUnit();
		return unit != null ? unit.sectionToString(this) : name();
	}

	@Override
	public Object[] subDeclarationsForOutline() {
		return hasChildren() ? this.children() : null;
	}

	@Override
	public Iterator<IniItem> iterator() {
		return new ReadOnlyIterator<IniItem>(this.itemMap.values().iterator());
	}

	public void putEntry(IniEntry entry) {
		itemMap.put(entry.name(), entry);
		itemList.add(entry);
		entry.setParentDeclaration(this);
	}

	@Override
	public void writeTextRepresentation(Writer writer, int indentation) throws IOException {
		writer.append('[');
		writer.append(name());
		writer.append(']');
		writer.append('\n');

		for (IniItem item : subItemList()) {
			if (item.isTransient())
				continue;
			item.writeTextRepresentation(writer, indentation + 1);
			writer.append('\n');
		}
	}
	
	public boolean hasPersistentItems() {
		for (IniItem item : subItemList())
			if (!item.isTransient())
				return true;
		return false;
	}

	@Override
	public void validate() {
		for (IniItem e : this)
			e.validate();
	}

	public int indentation() {
		return indentation;
	}

	public void setIndentation(int indentation) {
		this.indentation = indentation;
	}

	@Override
	public int sortCategory() {
		return 1;
	}

	public Iterable<IniSection> sections() {
		// unable to make this work generically ;c
		List<IniSection> sections = new LinkedList<IniSection>();
		for (ITreeNode node : childCollection())
			if (node instanceof IniSection)
				sections.add((IniSection) node);
		return sections;
	}

	public IniSection parentSection() {
		return parentDeclaration() instanceof IniSection ? (IniSection) parentDeclaration()
				: null;
	}

	public IniUnit iniUnit() {
		return firstParentDeclarationOfType(IniUnit.class);
	}

	@Override
	public String infoText(IIndexEntity context) {
		IniUnit unit = iniUnit();
		return String.format(Messages.IniSection_InfoTextFormat, unit.sectionToString(this), unit.infoText(context));
	}

	@Override
	public boolean isTransient() {
		return false;
	}
	
	private static void setFromString(Field f, Object object, String val) throws NumberFormatException, IllegalArgumentException, IllegalAccessException {	
		if (f.getType() == Integer.TYPE)
			f.set(object, Integer.valueOf(val));
		else if (f.getType() == Long.TYPE)
			f.set(object, Long.valueOf(val));
		else if (f.getType() == java.lang.Boolean.TYPE)
			f.set(object, java.lang.Boolean.valueOf(val));
	}
	
	public void commit(Object object, boolean takeIntoAccountCategory) {
		for (IniItem item : subItemMap().values())
			if (item instanceof IniSection)
				((IniSection)item).commit(object, takeIntoAccountCategory);
			else if (item instanceof IniEntry) {
				IniEntry entry = (IniEntry) item;
				Field f;
				try {
					f = object.getClass().getField(entry.name());
				} catch (Exception e) {
					// don't panic - probably unknown field
					//e.printStackTrace();
					continue;
				}
				IniField annot;
				if (f != null && (annot = f.getAnnotation(IniField.class)) != null && (!takeIntoAccountCategory || IniUnitParser.category(annot, object.getClass()).equals(name()))) {
					Object val = entry.value();
					if (val instanceof IConvertibleToPrimitive)
						val = ((IConvertibleToPrimitive)val).convertToPrimitive();
					try {
						if (f.getType() != String.class && val instanceof String)
							setFromString(f, object, (String)val);
						else try {
							f.set(object, val);
						} catch (IllegalArgumentException e) {
							if (val instanceof Long && f.getType() == Integer.TYPE) {
								f.set(object, (int)(long)(Long)val);
								continue;
							} else if (val instanceof Long && f.getType() == java.lang.Boolean.TYPE) {
								f.set(object, (Long)val != 0);
								continue;
							}
							// unboxing failed
							try {
								Constructor<?> ctor = f.getType().getConstructor(val.getClass());
								f.set(object, ctor.newInstance(val));
							} catch (NoSuchMethodException nsm) {
								System.out.println(f.getName());
								nsm.printStackTrace();
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
						continue;
					}
				}
			}
	}
}