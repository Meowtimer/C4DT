package net.arctics.clonk.parser.c4script;

import java.io.Serializable;

import net.arctics.clonk.ClonkCore;
import net.arctics.clonk.index.Definition;
import net.arctics.clonk.parser.Declaration;
import net.arctics.clonk.parser.ID;
import net.arctics.clonk.parser.ParserErrorCode;
import net.arctics.clonk.parser.ParsingException;
import net.arctics.clonk.parser.c4script.ast.ExprElm;

public class Directive extends Declaration implements Serializable {

	private static final long serialVersionUID = ClonkCore.SERIAL_VERSION_UID;

	public enum C4DirectiveType {
		STRICT,
		INCLUDE,
		APPENDTO;

		private String lowerCase = name().toLowerCase();

		public static C4DirectiveType makeType(String arg) {
			for (C4DirectiveType d : values())
				if (d.toString().equals(arg))
					return d;
			return null;
		}

		@Override
		public String toString() {
			return lowerCase;
		}
	}

	private C4DirectiveType type;
	private String content;
	private transient ID cachedID;

	public Directive(C4DirectiveType type, String content) {
		this.content = content;
		this.type = type;
	}

	public Directive(String type, String content) {
		this(C4DirectiveType.makeType(type),content);
	}

	/**
	 * @return the type
	 */
	public C4DirectiveType getType() {
		return type;
	}

	/**
	 * @return the content
	 */
	public String getContent() {
		return content;
	}

	@Override
	public String toString() {
		if (content != "" && content != null) //$NON-NLS-1$
			return "#" + type.toString() + " " + content; //$NON-NLS-1$ //$NON-NLS-2$
		return "#" + type.toString(); //$NON-NLS-1$
	}
	
	@Override
	public String getName() {
		return type.toString();
	}

	public ExprElm getExprElm() {
		return new ExprElm() {
			/**
			 * 
			 */
			private static final long serialVersionUID = ClonkCore.SERIAL_VERSION_UID;
			@Override
			public int getExprStart() {
				return getLocation().getStart();
			}
			@Override
			public int getExprEnd() {
				return getLocation().getEnd();
			}
		};
	}

	public ID contentAsID() {
		if (cachedID == null)
			cachedID = ID.getID(this.getContent());
		return cachedID;
	}

	public static String[] arrayOfDirectiveStrings() {
		String[] result = new String[C4DirectiveType.values().length];
		for (C4DirectiveType d : C4DirectiveType.values())
			result[d.ordinal()] = d.toString();
		return result;
	}

	public void validate(C4ScriptParser parser) throws ParsingException {
		switch (getType()) {
		case APPENDTO:
			break; // don't create error marker when appending to unknown object
		case INCLUDE:
			if (getContent() == null)
				parser.errorWithCode(ParserErrorCode.MissingDirectiveArgs, getLocation(), true, this.toString());
			else {
				ID id = contentAsID();
				Definition obj = parser.getContainer().getIndex().getObjectNearestTo(parser.getContainer().getResource(), id);
				if (obj == null)
					parser.errorWithCode(ParserErrorCode.UndeclaredIdentifier, getLocation(), true, getContent());
			}
			break;
		}
	}

}