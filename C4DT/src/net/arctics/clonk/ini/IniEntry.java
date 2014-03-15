package net.arctics.clonk.ini;

import java.util.Collection;

import net.arctics.clonk.Core;
import net.arctics.clonk.ProblemException;
import net.arctics.clonk.ast.ASTNode;
import net.arctics.clonk.ast.ASTNodePrinter;
import net.arctics.clonk.ast.ASTNodeWrap;
import net.arctics.clonk.ast.IASTSection;
import net.arctics.clonk.ast.NameValueAssignment;
import net.arctics.clonk.ini.IniData.IniEntryDefinition;
import net.arctics.clonk.parser.Markers;
import net.arctics.clonk.util.IHasChildren;
import net.arctics.clonk.util.IHasChildrenWithContext;
import net.arctics.clonk.util.IHasContext;
import net.arctics.clonk.util.INode;
import net.arctics.clonk.util.ITreeNode;
import net.arctics.clonk.util.StringUtil;

public class IniEntry extends NameValueAssignment implements IHasChildren, IHasContext, IniItem, IASTSection {
	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
	private IniEntryDefinition definition;
	@Override
	public void doPrint(final ASTNodePrinter writer, final int indentation) {
		writer.append(StringUtil.multiply("\t", indentation));
		writer.append(toString());
	}
	@Override
	public int sortCategory() { return 0; }
	public IniEntry(final int pos, final int endPos, final String key, final Object value) { super(pos,endPos, key, value); }
	public IniEntry update(final Object value, final IniEntryDefinition definition) {
		this.value = value;
		this.definition = definition;
		assignParentToSubElements();
		return this;
	}
	public void setDefinition(final IniEntryDefinition entryConfig) { this.definition = entryConfig; }
	public IniEntryDefinition definition() { return definition; }
	public IniUnit unit() { return parent(IniUnit.class); }
	@Override
	public Object context() { return this; }
	@Override
	public boolean isTransient() { return value() instanceof IniEntryValue && ((IniEntryValue)value()).isEmpty(); }
	@Override
	public String stringValue() { return value.toString(); }
	public Object value() { return value; }
	@Override
	public int absoluteOffset() { return sectionOffset()+start; }
	@Override
	public Object[] children() {
		if (value instanceof IHasChildrenWithContext)
			return ((IHasChildrenWithContext)value).children(this);
		else if (value instanceof IHasChildren)
			return ((IHasChildren)value).children();
		return null;
	}
	@Override
	public Collection<? extends INode> childCollection() {
		if (value instanceof ITreeNode)
			return ((ITreeNode) value).childCollection();
		return null;
	}
	@Override
	public boolean hasChildren() {
		return
			(value instanceof IHasChildren && ((IHasChildren)value).hasChildren()) ||
			(value instanceof IHasChildrenWithContext && ((IHasChildrenWithContext)value).hasChildren());
	}
	@Override
	public void validate(final Markers markers) throws ProblemException {
		if (value instanceof ISelfValidatingIniEntryValue)
			((ISelfValidatingIniEntryValue)value).validate(markers, this);
	}
	@Override
	public ASTNode[] subElements() { return new ASTNode[] {ASTNodeWrap.wrap(value)}; }
	@Override
	public void setSubElements(ASTNode[] elms) { this.value = ASTNodeWrap.unwrap(elms[0]); }
}
