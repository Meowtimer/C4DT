package net.arctics.clonk.stringtbl;

import java.io.Reader;
import java.io.StringReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.text.Region;

import net.arctics.clonk.Core;
import net.arctics.clonk.ast.ASTNodePrinter;
import net.arctics.clonk.ast.Declaration;
import net.arctics.clonk.ast.EntityRegion;
import net.arctics.clonk.ast.NameValueAssignment;
import net.arctics.clonk.ast.Structure;
import net.arctics.clonk.parser.BufferedScanner;
import net.arctics.clonk.util.ITreeNode;
import net.arctics.clonk.util.ReadOnlyIterator;
import net.arctics.clonk.util.StreamUtil;

public class StringTbl extends Structure implements ITreeNode {

	public static final Pattern PATTERN = Pattern.compile("StringTbl(..)\\.txt", Pattern.CASE_INSENSITIVE); //$NON-NLS-1$

	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;

	private final Map<String, NameValueAssignment> map = new HashMap<String, NameValueAssignment>();
	private transient IFile file;


	@Override
	public void setFile(final IFile file) {
		this.file = file;
		setName(file != null ? file.getName() : null);
	}

	@Override
	public IFile file() { return file; }
	@Override
	public IResource resource() { return file; }
	public Map<String, NameValueAssignment> map() { return map; }
	@Override
	public void addChild(final ITreeNode node) {}
	@Override
	public Collection<? extends ITreeNode> childCollection() { return map.values(); }
	@Override
	public String nodeName() { return "StringTbl"; } //$NON-NLS-1$
	@Override
	public ITreeNode parentNode() { return null; }
	@Override
	public boolean requiresScriptReparse() { return true; }

	public void addTblEntry(final String key, final String value, final int start, final int end) {
		final NameValueAssignment nv = new NameValueAssignment(start, end, key, value);
		nv.setParent(this);
		map.put(key, nv);
	}

	@Override
	public Declaration findLocalDeclaration(final String declarationName, final Class<? extends Declaration> declarationClass) {
		if (declarationClass == NameValueAssignment.class) {
			return map.get(declarationName);
		}
		return null;
	}

	@Override
	public Declaration findDeclaration(final String declarationName) {
		return map.get(declarationName);
	}

	public static void readStringTbl(final Reader reader, final StringTbl tbl) {
		BufferedScanner scanner;
		scanner = new BufferedScanner(reader);
		while (!scanner.reachedEOF()) {
			scanner.eatWhitespace();
			if (scanner.read() == '#') {
				scanner.readStringUntil(BufferedScanner.NEWLINE_CHARS);
			} else {
				scanner.unread();
				final int start = scanner.tell();
				final String key = scanner.readStringUntil('=');
				if (scanner.read() == '=') {
					String value = scanner.readStringUntil(BufferedScanner.NEWLINE_CHARS);
					if (value == null) {
						value = "";
					}
					tbl.addTblEntry(key, value, start, scanner.tell());
				} else {
					scanner.unread();
				}
			}
		}
	}

	public void read(final Reader reader) {
		readStringTbl(reader, this);
	}

	@Override
	public Object[] subDeclarationsForOutline() {
		return map.values().toArray(new Object[map.values().size()]);
	}

	public Iterator<NameValueAssignment> iterator() {
		return new ReadOnlyIterator<NameValueAssignment>(map.values().iterator());
	}

	public static void register() {
		final Matcher stringTblFileMatcher = PATTERN.matcher(""); //$NON-NLS-1$
		registerStructureFactory((resource, duringBuild) -> {
			if (resource instanceof IFile && stringTblFileMatcher.reset(resource.getName()).matches()) {
				final IFile file = (IFile) resource;
				final StringTbl tbl = new StringTbl();
				tbl.setFile(file);
				String fileContents;
				try {
					fileContents = StreamUtil.stringFromFileDocument(file);
				} catch (final Exception e) {
					e.printStackTrace();
					return null;
				}
				final StringReader reader = new StringReader(fileContents);
				tbl.read(reader);
				return tbl;
			}
			return null;
		});
	}

	public static EntityRegion entryRegionInString(final String stringValue, final int exprStart, final int offset) {
		final int firstDollar = stringValue.lastIndexOf('$', offset-1);
		final int secondDollar = stringValue.indexOf('$', offset);
		if (firstDollar != -1 && secondDollar != -1) {
			final String entry = stringValue.substring(firstDollar+1, secondDollar);
			return new EntityRegion(null, new Region(exprStart+1+firstDollar, secondDollar-firstDollar+1), entry);
		}
		return null;
	}

	public static EntityRegion entryForLanguagePref(final String stringValue, final int exprStart, final int offset, final Declaration container, final boolean returnNullIfNotFound) {
		EntityRegion result = entryRegionInString(stringValue, exprStart, offset);
		if (result != null) {
			final StringTbl stringTbl = container.localStringTblMatchingLanguagePref();
			final Declaration e = stringTbl != null ? stringTbl.map().get(result.text()) : null;
			if (e == null && returnNullIfNotFound) {
				result = null;
			} else {
				result.setEntity(e);
			}
		}
		return result;
	}

	public static class EvaluationResult {
		public final String evaluated;
		public final EntityRegion singleDeclarationRegionUsed;
		public final boolean anySubstitutionsApplied;
		public EvaluationResult(
			String evaluated,
			EntityRegion singleDeclarationRegionUsed,
			boolean anySubstitutionsApplied
		) {
			super();
			this.evaluated = evaluated;
			this.singleDeclarationRegionUsed = singleDeclarationRegionUsed;
			this.anySubstitutionsApplied = anySubstitutionsApplied;
		}
	}

	/**
	 * Evaluate a string containing $...$ placeholders by fetching the actual strings from respective StringTbl**.txt files.
	 * @param context The declaration/script to be used as a hint on where to look for StringTbl files
	 * @param value The value to be evaluated
	 * @return The evaluated string
	 */
	public static EvaluationResult evaluateEntries(final Declaration context, final String value, final boolean evaluateEscapes) {
		final int valueLen = value.length();
		final StringBuilder builder = new StringBuilder(valueLen*2);
		// insert stringtbl entries
		EntityRegion reg = null;
		boolean moreThanOneSubstitution = false;
		boolean substitutionsApplied = false;
		Outer: for (int i = 0; i < valueLen;) {
			if (i+1 < valueLen) {
				switch (value.charAt(i)) {
				case '$':
					moreThanOneSubstitution = reg != null;
					final EntityRegion region = entryForLanguagePref(value, 0, i+1, context, true);
					if (region != null) {
						substitutionsApplied = true;
						builder.append(((NameValueAssignment)region.entityAs(Declaration.class)).stringValue());
						i += region.region().getLength();
						reg = region;
						continue Outer;
					}
					break;
				case '\\':
					if (evaluateEscapes) {
						switch (value.charAt(++i)) {
						case '"': case '\\':
							builder.append(value.charAt(i++));
							continue Outer;
						}
					}
					break;
				}
			}
			builder.append(value.charAt(i++));
		}
		return new EvaluationResult(
			builder.toString(),
			moreThanOneSubstitution ? null : reg,
			substitutionsApplied
		);
	}
	
	@Override
	public void doPrint(ASTNodePrinter output, int depth) {
		final TreeSet<String> keys = new TreeSet<String>(map.keySet());
		keys.forEach(key -> {
			final NameValueAssignment value = map.get(key);
			output.append(String.format("%s=%s", key, value.stringValue()));
			output.append("\n");
		});
	}

}