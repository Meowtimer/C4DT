package net.arctics.clonk.parser.c4script;

import java.io.Serializable;
import java.util.regex.Matcher;

import net.arctics.clonk.ClonkCore;
import net.arctics.clonk.index.Definition;
import net.arctics.clonk.parser.Declaration;
import net.arctics.clonk.parser.ID;
import net.arctics.clonk.parser.ParserErrorCode;
import net.arctics.clonk.parser.ParsingException;
import net.arctics.clonk.parser.c4script.ast.ExprElm;

public class Directive extends Declaration implements Serializable {

	private static final long serialVersionUID = ClonkCore.SERIAL_VERSION_UID;

	public enum DirectiveType {
		STRICT,
		INCLUDE,
		APPENDTO;

		private final String lowerCase = name().toLowerCase();

		public static DirectiveType makeType(String arg) {
			for (DirectiveType d : values())
				if (d.toString().equals(arg))
					return d;
			return null;
		}

		@Override
		public String toString() {
			return lowerCase;
		}
	}

	private final DirectiveType type;
	private final String content;
	private transient ID cachedID;

	public Directive(DirectiveType type, String content) {
		this.content = content;
		this.type = type;
	}

	public Directive(String type, String content) {
		this(DirectiveType.makeType(type),content);
	}

	/**
	 * @return the type
	 */
	public DirectiveType getType() {
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
	public String name() {
		return type.toString();
	}

	public ExprElm getExprElm() {
		return new ExprElm() {
			private static final long serialVersionUID = ClonkCore.SERIAL_VERSION_UID;
			@Override
			public int getExprStart() {
				return location().start();
			}
			@Override
			public int getExprEnd() {
				return location().end();
			}
		};
	}

	public ID contentAsID() {
		if (cachedID == null)
			cachedID = ID.get(this.getContent());
		return cachedID;
	}

	public static String[] arrayOfDirectiveStrings() {
		String[] result = new String[DirectiveType.values().length];
		for (DirectiveType d : DirectiveType.values())
			result[d.ordinal()] = d.toString();
		return result;
	}

	public void validate(C4ScriptParser parser) throws ParsingException {
		switch (getType()) {
		case APPENDTO:
			break; // don't create error marker when appending to unknown object
		case INCLUDE:
			if (getContent() == null)
				parser.errorWithCode(ParserErrorCode.MissingDirectiveArgs, location(), C4ScriptParser.NO_THROW, this.toString());
			else {
				ID id = contentAsID();
				Definition obj = parser.containingScript().index().getDefinitionNearestTo(parser.containingScript().resource(), id);
				if (obj == null)
					parser.errorWithCode(ParserErrorCode.UndeclaredIdentifier, location(), C4ScriptParser.NO_THROW, getContent());
			}
			break;
		}
	}
	
	@Override
	public boolean matchedBy(Matcher matcher) {
		if (matcher.reset(getType().name()).lookingAt() || matcher.reset("#"+getType().name()).lookingAt())
			return true;
		return getContent() != null && matcher.reset(getContent()).lookingAt();
	}

}
