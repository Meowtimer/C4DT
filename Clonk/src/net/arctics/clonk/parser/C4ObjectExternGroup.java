package net.arctics.clonk.parser;

import java.io.Serializable;

import net.arctics.clonk.util.INodeWithParent;

public class C4ObjectExternGroup implements INodeWithParent, Serializable {

	private static final long serialVersionUID = 1L;
	
	private String nodeName;
	private INodeWithParent parentNode;
	
	public String nodeName() {
		return nodeName;
	}

	public INodeWithParent parentNode() {
		return parentNode;
	}

	public C4ObjectExternGroup(String nodeName, INodeWithParent parentNode) {
		super();
		this.nodeName = nodeName;
		this.parentNode = parentNode;
	}

}
