package net.arctics.clonk.parser.defcore;

import net.arctics.clonk.parser.inireader.IEntryCreateable;
import net.arctics.clonk.parser.inireader.IniParserException;

import org.eclipse.core.resources.IMarker;

public class IntegerArray implements IEntryCreateable {

	private int[] integers;
	
	public IntegerArray() {
	}
	
	public IntegerArray(String value) throws IniParserException {
		setInput(value);
	}
	
	public String getStringRepresentation() {
		return toString();
	}
	
	public String toString() {
		StringBuilder builder = new StringBuilder(integers.length * 2);
		for(int i = 0; i < integers.length;i++) {
			builder.append(integers[i]);
			if (i < integers.length - 1) builder.append(",");
		}
		return builder.toString();
	}

	public int get(int i) {
		return integers[i];
	}
	
	public int[] getIntegers() {
		return integers;
	}

	public void setIntegers(int[] integers) {
		this.integers = integers;
	}

	public void setInput(String input) throws IniParserException {
		try {
			String[] parts = input.split(",");
			if (parts.length > 0) {
				int[] integers = new int[parts.length];
				for(int i = 0; i < parts.length;i++) {
					parts[i] = parts[i].trim();
					if (parts[i].startsWith("+")) parts[i] = parts[i].substring(1);
					integers[i] = Integer.parseInt(parts[i].trim());
				}
				this.integers = integers;
			}
			else {
				throw new IniParserException(IMarker.SEVERITY_WARNING, "Expected an integer array");
			}
		}
		catch(NumberFormatException e) {
			IniParserException exp = new IniParserException(IMarker.SEVERITY_ERROR, "Expected an integer array");
			exp.setInnerException(e);
			throw exp;
		}
	}

}
