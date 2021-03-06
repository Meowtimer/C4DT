package net.arctics.clonk.ini;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IMarker;

import net.arctics.clonk.Core;
import net.arctics.clonk.ast.ASTNode;
import net.arctics.clonk.ast.ASTNodeWrap;
import net.arctics.clonk.ini.IniData.IniEntryDefinition;
import net.arctics.clonk.util.IHasChildrenWithContext;
import net.arctics.clonk.util.IHasContext;
import net.arctics.clonk.util.ITreeNode;
import net.arctics.clonk.util.KeyValuePair;
import net.arctics.clonk.util.StringUtil;

public abstract class ArrayValue<KeyType, ValueType> extends IniEntryValue implements IHasChildrenWithContext, ITreeNode {
	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
	private final List<KeyValuePair<KeyType, ValueType>> components = new ArrayList<KeyValuePair<KeyType, ValueType>>();
	public ArrayValue(final String value, final IniEntryDefinition entryData, final IniUnit context) throws IniParserException { setInput(value, entryData, context); }
	public ArrayValue() {}
	public void add(final KeyType id, final ValueType num) { components.add(new KeyValuePair<KeyType, ValueType>(id,num)); }
	public void add(final KeyValuePair<KeyType, ValueType> pair) { components.add(pair); }
	public KeyValuePair<KeyType, ValueType> find(final KeyType key) {
		for (final KeyValuePair<KeyType, ValueType> kv : components) {
			if (kv.key().equals(key)) {
				return kv;
			}
		}
		return null;
	}
	public List<KeyValuePair<KeyType, ValueType>> components() { return components; }
	public abstract KeyValuePair<KeyType, ValueType> singleComponentFromString(int offset, String s);
	@Override
	public boolean hasChildren() { return components.size() > 0; }
	@Override
	public Object valueOfChildAt(final int index) { return components.get(index); }
	@Override
	public String nodeName() { return null; }
	@Override
	public void addChild(final ITreeNode node) {}
	@Override
	public ITreeNode parentNode() { return null; }
	@Override
	public boolean isEmpty() { return components == null || components.size() == 0; }
	@Override
	public List<KeyValuePair<KeyType, ValueType>> childCollection() { return components; }
	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder(components.size() * 7); // MYID=1;
		final Iterator<KeyValuePair<KeyType, ValueType>> it = components.iterator();
		while (it.hasNext()) {
			final KeyValuePair<KeyType, ValueType> pair = it.next();
			builder.append(pair.toString());
			if (it.hasNext()) {
				builder.append(';');
			}
		}
		return builder.toString();
	}
	@Override
	public void setInput(final String input, final IniEntryDefinition entryData, final IniUnit context) throws IniParserException {
		// CLNK=1;STIN=10;
		components.clear();
		final String[] parts = input.split(";|,"); //$NON-NLS-1$
		List<String> invalidParts = null;
		int off = 0;
		for (final String part : parts) {
			boolean valid = false;
			if (part.contains("=")) { //$NON-NLS-1$
				KeyValuePair<KeyType, ValueType> kv;
				try {
					kv = singleComponentFromString(off, part);
				} catch (final IllegalArgumentException e) {
					kv = null;
				}
				if (kv != null) {
					components.add(kv);
					valid = true;
				}
			}
			if (!valid) {
				if (invalidParts == null) {
					invalidParts = new ArrayList<>(3);
				}
					invalidParts.add(part);
			}
			off += part.length()+1;
		}
		assignParentToSubElements();
		if (invalidParts != null)
		 {
			throw new IniParserException(IMarker.SEVERITY_ERROR,
				String.format(Messages.InvalidParts, StringUtil.blockString("", "", ",  ", invalidParts))); //$NON-NLS-2$ //$NON-NLS-3$ 
		}
	}

	@Override
	public IHasContext[] children(final Object context) {
		final IHasContext[] result = new IHasContext[components.size()];
		for (int i = 0; i < components.size(); i++) {
			result[i] = new EntrySubItem<ArrayValue<KeyType, ValueType>>(this, context, i);
		}
		return result;
	}
	@Override
	public ASTNode[] subElements() {
		final ASTNode[] nodes = new ASTNode[components.size()*2];
		for (int i = 0, j = 0; j < components.size(); i += 2, j++) {
			nodes[i]   = ASTNodeWrap.wrap(components.get(j).key());
			nodes[i+1] = ASTNodeWrap.wrap(components.get(j).value());
		}
		return nodes;
	}
	@SuppressWarnings("unchecked")
	@Override
	public void setSubElements(ASTNode[] elms) {
		components.clear();
		for (int i = 0; i+1 < elms.length; i += 2) {
			components.add(new KeyValuePair<KeyType, ValueType>((KeyType)ASTNodeWrap.unwrap(elms[i]), (ValueType)ASTNodeWrap.unwrap(elms[i+1])));
		}
	}
}
