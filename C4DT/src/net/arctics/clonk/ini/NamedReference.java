package net.arctics.clonk.ini;

import net.arctics.clonk.ini.IniData.IniEntryDefinition;

public class NamedReference extends IniEntryValueBase {

	private String value;
	
	@Override
	public void setInput(String value, IniEntryDefinition entryData, IniUnit context) throws IniParserException {
		this.value = value;
	}
	
	@Override
	public String toString() {
		return value;
	}

}