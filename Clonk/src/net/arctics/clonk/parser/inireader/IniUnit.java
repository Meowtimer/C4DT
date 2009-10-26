package net.arctics.clonk.parser.inireader;

import java.io.InputStream;
import java.io.InvalidClassException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.arctics.clonk.ClonkCore;
import net.arctics.clonk.parser.BufferedScanner;
import net.arctics.clonk.parser.C4Declaration;
import net.arctics.clonk.parser.C4Structure;
import net.arctics.clonk.parser.ParserErrorCode;
import net.arctics.clonk.parser.SourceLocation;
import net.arctics.clonk.parser.inireader.IniData.IniConfiguration;
import net.arctics.clonk.parser.inireader.IniData.IniDataEntry;
import net.arctics.clonk.parser.inireader.IniData.IniSectionData;
import net.arctics.clonk.util.IHasChildren;
import net.arctics.clonk.util.IPredicate;
import net.arctics.clonk.util.ITreeNode;
import net.arctics.clonk.util.Utilities;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.ui.ide.IDE;

/**
 * Reads Windows ini style configuration files
 */
public class IniUnit extends C4Structure implements Iterable<IniSection>, IHasChildren, ITreeNode {

	private static final long serialVersionUID = 1L;

	/**
	 * Text scanner
	 */
	protected BufferedScanner reader;
	
	/**
	 * The configuration file
	 */
	protected IFile iniFile = null;
	
	/**
	 * map to access sections by their name - only useful when sections have different names
	 */
	protected Map<String, IniSection> sectionsMap = new HashMap<String, IniSection>();
	
	/**
	 * list of all sections regardless of name (for ActMap and similar files)
	 */
	protected List<IniSection> sectionsList = new LinkedList<IniSection>();
	
	/**
	 * Name of the configuration that is to be used when no name was explicitly defined in the file. (?)
	 */
	protected String defaultName;
	
	/**
	 * Temporary reference to the section being currently parsed.
	 */
	protected IniSection currentSection;
	
	/**
	 * Creates an IniReader that reads ini information from a stream
	 * @param stream the stream
	 */
	public IniUnit(InputStream stream) {
		reader = new BufferedScanner(stream);
	}
	
	/**
	 * Creates an IniReader that reads ini information from a string
	 * @param text the string
	 */
	public IniUnit(String text) {
		reader = new BufferedScanner(text);
	}
	
	/**
	 * Creates an IniReader that reads ini information from a project file
	 * @param file the file
	 */
	public IniUnit(IFile file) {
		try {
			defaultName = file.getParent().getName();
			InputStream stream = file.getContents();
			reader = new BufferedScanner(stream);
			stream.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		iniFile = file;
	}
	
	/**
	 * Returns the file the configuration was read from
	 * @return the file
	 */
	public IFile getIniFile() {
		return iniFile;
	}
	
	public void setIniFile(IFile file) {
		iniFile = file;
	}
	
	/**
	 * Checks whether this section name is valid.<br>
	 * Default implementation consults the configuration returned from getConfiguration() to determine if the section is valid.
	 * @param name
	 * @return <tt>true</tt> if valid
	 */
	protected boolean isSectionNameValid(String name) {
		return getConfiguration() == null || getConfiguration().hasSection(name);
	}
	
	/**
	 * Checks whether this entry name/value combination is valid.<br>
	 * Clients may override. This implementation always returns unmodified <tt>entry</tt>.
	 * @param entry
	 * @param section 
	 * @param modifyMarkers 
	 * @return validated entry
	 */
	protected IniEntry validateEntry(IniEntry entry, IniSection section, boolean modifyMarkers) throws IniParserException {
		IniConfiguration configuration = getConfiguration();
		if (configuration == null)
			return entry;
		IniSectionData sectionConfig = currentSection.getSectionData();
		if (sectionConfig == null)
			return entry; // don't throw errors in unknown section
		if (!sectionConfig.hasEntry(entry.getKey())) {
			throw new IniParserException(IMarker.SEVERITY_WARNING, Messages.IniUnit_0 + entry.getKey() + Messages.IniUnit_1, entry.getStartPos(), entry.getKey().length() + entry.getStartPos()); //$NON-NLS-2$
		}
		IniDataEntry entryConfig = sectionConfig.getEntry(entry.getKey());
		try {
			try {
				Object value = configuration.getFactory().create(entryConfig.getEntryClass(), entry.getValue(), entryConfig);
				return ComplexIniEntry.adaptFrom(entry, value, entryConfig, modifyMarkers);
			}
			catch(IniParserException e) { // add offsets and throw through
				// FIXME: whitespace before and after '=' is not taken into account
				if (e.getOffset() == 0 || e.getEndOffset() == 0) {
					String key = entry.getKey();
					String value = entry.getValue();
					if (value == null)
						value = ""; //$NON-NLS-1$
					e.setOffset(entry.getStartPos() + key.length() + 1);
					e.setEndOffset(entry.getStartPos() + key.length() + 1 + value.length());
				}
				throw e;
			}
		} catch (InvalidClassException e) {
			throw new IniParserException(IMarker.SEVERITY_WARNING, Messages.IniUnit_3 + e.getMessage(),entry.getStartPos(),entry.getStartPos() + entry.getKey().length());
		}
	}
	
	public void parse(boolean modifyMarkers) {
		if (modifyMarkers && getIniFile() != null) {
			try {
				getIniFile().deleteMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ONE);
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}
		this.clear();
		reader.seek(0);
		IniSection section;
		while ((section = parseSection(modifyMarkers)) != null) {
			sectionsMap.put(section.getName(), section);
			sectionsList.add(section);
		}
		if (!reader.reachedEOF()) {
			//createMarker("Unexpected data.", IMarker.SEVERITY_WARNING, reader.getPosition() - 2, reader.getPosition());
		}
	}
	
	private void clear() {
		sectionsList.clear();
		sectionsMap.clear();
	}

	protected IniSectionData getSectionDataFor(IniSection section) {
		return getConfiguration() != null
			? getConfiguration().getSections().get(section.getName())
			: null;
	}
	
	protected IniSection parseSection(boolean modifyMarkers) {
		reader.eatWhitespace();
		int start = reader.getPosition();
		// parse head
		if (reader.read() == '[') {
			String name = reader.readStringUntil(']','\n','\r');
			if (reader.read() != ']') {
				if (modifyMarkers)
					marker(ParserErrorCode.TokenExpected, start, reader.getPosition(), IMarker.SEVERITY_ERROR, (Object)"]");					 //$NON-NLS-1$
				return null;
			}
			else {
				if (!isSectionNameValid(name)) {
					if (modifyMarkers)
						marker(ParserErrorCode.InvalidExpression, start, reader.getPosition()-1, IMarker.SEVERITY_WARNING);
				}
			}
			int end = reader.getPosition();
			IniSection section = new IniSection(new SourceLocation(start, end), name);
			section.setParentDeclaration(this);
			// parse entries
//			List<IniEntry> entries = new LinkedList<IniEntry>();
			IniEntry entry = null;
			Map<String, IniEntry> entries = new HashMap<String, IniEntry>();
			currentSection = section;
			currentSection.setSectionData(getSectionDataFor(section));
			while ((entry = parseEntry(section, modifyMarkers)) != null) {
				entries.put(entry.getKey(),entry);
			}
			section.setEntries(entries);
			return section;
		}
		else {
			reader.unread();
			return null;
		}
	}
	
	protected boolean skipComment() {
		int r;
		for (r = reader.read(); r == 0; r = reader.read());
		if (r == ';' || r == '#') {
			reader.readStringUntil('\n');
			reader.eatWhitespace();
			return true;
		}
		else if (r == '/') {
			switch (reader.read()) {
			case '/':
				reader.eatUntil(BufferedScanner.NEWLINE_CHARS);
				reader.eatWhitespace();
				return true;
			case '*':
				for (; !reader.reachedEOF();) {
					if (reader.read() == '*') {
						if (reader.read() == '/')
							break;
						else
							reader.unread();
					}
				}
				reader.eatWhitespace();
				return true;
			default:
				reader.unread(); reader.unread();
				return false;
			}
		}
		else {
			reader.unread();
			return false;
		}
	}
	
	public void marker(ParserErrorCode error, int start, int end, int markerSeverity, Object... args) {
		error.createMarker(iniFile, ClonkCore.MARKER_C4SCRIPT_ERROR, start, end, markerSeverity, args);
	}
	
	public void markerAtValue(ParserErrorCode error, IniEntry entry, int markerSeverity, Object... args) {
		marker(error, entry.getLocation().getStart(), entry.getLocation().getEnd(), markerSeverity, args);
	}
	
	protected IniEntry parseEntry(IniSection section, boolean modifyMarkers) {
		while (skipComment());
		int start = reader.getPosition();
		reader.eatWhitespace();
		if (reader.read() == '[') {
			reader.seek(start);
			return null;
		}
		reader.unread();
		if (reader.reachedEOF()) return null;
		int keyStart = reader.getPosition();
		String key = reader.readIdent();
		reader.eatWhitespace();
		if (reader.read() != '=') {
			if (modifyMarkers)
				marker(ParserErrorCode.TokenExpected, keyStart+key.length(), reader.getPosition(), IMarker.SEVERITY_ERROR, (Object)"="); //$NON-NLS-1$
		}
		reader.eat(new char[] {' ', '\t'});
		String value = reader.readStringUntil(BufferedScanner.NEWLINE_CHARS);
		int valEnd = reader.getPosition();
		reader.eat(BufferedScanner.NEWLINE_CHARS);
		IniEntry entry = new IniEntry(keyStart, valEnd, key, value);
		entry.setParentDeclaration(section);
		try {
			return validateEntry(entry, section, modifyMarkers);
		} catch (IniParserException e) {
			if (modifyMarkers)
				marker(ParserErrorCode.GenericError, e.getOffset(), e.getEndOffset(), e.getSeverity(), (Object)e.getMessage());
			return entry;
		}
	}
	
	public IniConfiguration getConfiguration() {
		return null;
	}

	public Iterator<IniSection> iterator() {
		return sectionsList.iterator();
	}
	
	public IniSection sectionWithName(String name) {
		return sectionsMap.get(name);
	}
	
	public IniSection sectionMatching(IPredicate<IniSection> predicate) {
		return Utilities.itemMatching(predicate, sectionsList);
	}
	
	public IniEntry entryInSection(String section, String entry) {
		IniSection s = sectionsMap.get(section);
		return s != null ? s.getEntry(entry) : null;
	}

	public IniSection[] getSections() {
		return sectionsList.toArray(new IniSection[sectionsList.size()]);
	}

	public Object[] getChildren() {
		return getSections();
	}

	public boolean hasChildren() {
		return !sectionsMap.isEmpty();
	}

	public void addChild(ITreeNode node) {
		// TODO Auto-generated method stub
		
	}

	public List<? extends ITreeNode> getChildCollection() {
		return sectionsList;
	}

	public String getNodeName() {
		return iniFile != null ? iniFile.getName() : toString();
	}

	public ITreeNode getParentNode() {
		return null;
	}

	public IPath getPath() {
		return ITreeNode.Default.getPath(this);
	}

	public boolean subNodeOf(ITreeNode node) {
		return ITreeNode.Default.subNodeOf(this, node);
	}
	
	@Override
	public IResource getResource() {
		return iniFile;
	}
	
	public String sectionToString(IniSection section) {
		return "["+section.getName()+"]"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	public IniSection sectionAtOffset(int offset, int addIfOverOffset) {
		IniSection section = null;
		for (IniSection sec : this.getSections()) {
			int start = sec.getLocation().getStart();
			if (start > offset)
				start += addIfOverOffset;
			if (start > offset)
				break;
			section = sec;
		}
		return section;
	}
	
	@Override
	public Object[] getSubDeclarationsForOutline() {
		return this.getChildren();
	}
	
	@Override
	public C4Declaration findLocalDeclaration(String declarationName,
			Class<? extends C4Declaration> declarationClass) {
		if (declarationClass.isAssignableFrom(IniSection.class))
			return findDeclaration(declarationName);
		return null;
	}
	
	@Override
	public C4Declaration findDeclaration(String declarationName) {
		return sectionWithName(declarationName);
	}
	
	@Override
	public C4Structure getTopLevelStructure() {
		return this;
	}
	
	@Override
	public String toString() {
		return getIniFile().getFullPath().toOSString();
	}
	
	@Override
	public void pinTo(IFile file) throws CoreException {
		super.pinTo(file);
		reader = null; // drop reader
	}
	
	public static void register() {
		C4Structure.registerStructureFactory(new IStructureFactory() {
			public C4Structure create(IFile file) {
				Class<? extends IniUnit> iniUnitClass = getIniUnitClass(file);
				if (iniUnitClass != null) {
					try {
						IniUnit reader = iniUnitClass.getConstructor(IFile.class).newInstance(file);
						reader.parse(true);
						return reader;
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				return null;
			}
		});
	}
	
	public static IniUnit createAdequateIniUnit(IFile file) throws SecurityException, NoSuchMethodException, IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {
		return createAdequateIniUnit(file, file);
	}

	public static IniUnit createAdequateIniUnit(IFile file, Object arg) throws SecurityException, NoSuchMethodException, IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {
		Class<? extends IniUnit> cls = getIniUnitClass(file);
		if (cls == null)
			return null;
		Class<?> neededArgType =
			arg instanceof String
				? String.class
				: arg instanceof IFile
					? IFile.class
					: InputStream.class;
		Constructor<? extends IniUnit> ctor = cls.getConstructor(neededArgType);
		IniUnit result = ctor.newInstance(arg);
		result.setIniFile(file);
		return result;
	}

	private static Map<String, Class<? extends IniUnit>> INIREADER_CLASSES = Utilities.map(new Object[] {
		ClonkCore.id("scenariocfg"), ScenarioUnit.class, //$NON-NLS-1$
		ClonkCore.id("actmap")     , ActMapUnit.class, //$NON-NLS-1$
		ClonkCore.id("defcore")    , DefCoreUnit.class, //$NON-NLS-1$
		ClonkCore.id("particle")   , ParticleUnit.class, //$NON-NLS-1$
		ClonkCore.id("material")   , MaterialUnit.class //$NON-NLS-1$
	});

	/**
	 * Returns the IniUnit class that is best suited to parsing the given ini file
	 * @param file the ini file to return an IniUnit class for
	 * @return the IniUnit class or null if no suitable one could be found
	 */
	public static Class<? extends IniUnit> getIniUnitClass(IFile file) {
		IContentType contentType = IDE.getContentType(file);
		if (contentType == null)
			return null;
		return INIREADER_CLASSES.get(contentType.getId());
	}

}
