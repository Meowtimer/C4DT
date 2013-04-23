package net.arctics.clonk.c4group;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import net.arctics.clonk.Core;
import net.arctics.clonk.c4group.C4Group.StreamReadCallback;
import net.arctics.clonk.util.ITreeNode;

import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.provider.FileInfo;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;

/**
 * Represents one file in a {@link C4Group} file.
 * @author ZokRadonh
 *
 */
public class C4GroupFile extends C4GroupItem implements IStorage, Serializable {
	
	private static class EntryCache {
		
		private static class CachedEntry {
			public File file;
			public long creationTime;
			public CachedEntry(File file, long creationTime) {
				super();
				this.file = file;
				this.creationTime = creationTime;
			}
			public boolean modified() {
				return file.lastModified() != creationTime;
			}
		}
		
		private final Map<C4GroupFile, CachedEntry> files = new HashMap<C4GroupFile, CachedEntry>();
		
		public File getCachedFile(C4GroupFile groupEntry) throws IOException, CoreException {
			CachedEntry e = files.get(groupEntry);
			if (e == null || e.modified()) {
				File f = File.createTempFile("c4dt", "c4groupcache"); //$NON-NLS-1$ //$NON-NLS-2$
				FileOutputStream fileStream = new FileOutputStream(f);
				try {
					ByteArrayInputStream contents = groupEntry.getContents();
					byte[] buf = new byte[1024];
					int read;
					while ((read = contents.read(buf)) != -1) {
						fileStream.write(buf, 0, read);
					}
				} finally {
					fileStream.close();
				}
				f.deleteOnExit();
				e = new CachedEntry(f, f.lastModified());
			}
			return e.file;
		}
	}
	
	private static final EntryCache CACHE = new EntryCache();

	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
	private static final String[] NO_CHILDNAMES = new String[0];

	public static final int STORED_SIZE = 316;

	private transient C4GroupEntryHeader header;
	private transient C4Group parentGroup;
	private transient boolean completed;
	private byte[] contents;

	private transient File exportFromFile;

	public C4GroupFile(C4Group parentGroup, C4GroupEntryHeader header) {
		this.parentGroup = parentGroup;
		this.header = header;
	}

	protected C4GroupFile() {
		completed = true;
	}

	public static C4GroupFile makeEntry(C4Group parent, C4GroupEntryHeader header, File exportFromFile) {
		C4GroupFile entry = new C4GroupFile();
		entry.parentGroup = parent;
		entry.header = header;
		entry.contents = null;
		entry.exportFromFile = exportFromFile;
		return entry;
	}

	@Override
	public void readIntoMemory(boolean recursively, C4GroupHeaderFilterBase filter, InputStream stream) throws C4GroupInvalidDataException, IOException, CoreException {
		if (completed) return;
		completed = true;

		if ((filter.flagsForEntry(this) & C4GroupHeaderFilterBase.DONTREADINTOMEMORY) == 0) {
			fetchContents(stream);
		}
		else {
			stream.skip(getSize());
		}

		// process contents (contents could be null after this call)
		filter.processGroupItem(this);

	}

	private void fetchContents(InputStream stream) {
		//System.out.println("Fetching contents of " + this);
		contents = new byte[getSize()];
		try {
			for (
				int readCount = 0;
				readCount != contents.length;
				readCount += stream.read(contents, readCount, contents.length - readCount)
			);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public ByteArrayInputStream getContents() throws CoreException {
		if (contents == null) {
			try {
				parentGroup().readFromStream(this, parentGroup().baseOffset() + header.offset(), new StreamReadCallback() {
					@Override
					public void readStream(InputStream stream) {
						fetchContents(stream);
					}
				});
				try {
					return new ByteArrayInputStream(getContentsAsArray());
				} finally {
					contents = null; // don't store
				}
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
		}
		return new ByteArrayInputStream(contents);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (C4GroupItem item = this; item != null; item = item.parentGroup()) {
			builder.insert(0, item.getName());
			builder.insert(0, '/');
		}
		return builder.toString();
	}

	/**
	 * @return the entryName
	 */
	public String getEntryName() {
		return header.entryName();
	}

	/**
	 * @return the packed
	 */
	public boolean isPacked() {
		return header.isPacked();
	}

	/**
	 * @return the hasChildren
	 */
	@Override
	public boolean hasChildren() {
		return false;
	}

	/**
	 * @return the size
	 */
	public int getSize() {
		return header.size();
	}

	/**
	 * @return the entrySize
	 */
	public int getEntrySize() {
		return header.entrySize();
	}

	/**
	 * @return the offset
	 */
	public int getOffset() {
		return header.offset();
	}

	/**
	 * @return the time
	 */
	public int getTime() {
		return header.time();
	}

	/**
	 * @return the hasCRC
	 */
	public boolean hasCRC() {
		return header.hasCRC();
	}

	/**
	 * @return the contents
	 */
	public byte[] getContentsAsArray() {
		return contents;
	}

	/**
	 * Return the contents of this entry as a string
	 * @return the contents of this entry as string
	 */
	public String getContentsAsString() {
		return new String(getContentsAsArray());
	}

	@Override
	public String getName() {
		return header.entryName();
	}

	@Override
	public C4Group parentGroup() {
		return parentGroup;
	}

	/**
	 * @param contents the contents to set
	 */
	public void setContents(byte[] contents) {
		this.contents = contents;
	}

	/**
	 * Release the contents of this item to preserve memory
	 */
	@Override
	public void releaseData() {
		setContents(null);
	}

	@Override
	public void extractToFileSystem(IContainer parent) throws CoreException {
		extractToFileSystem(parent, null);
	}

	@Override
	public void extractToFileSystem(IContainer parent, IProgressMonitor monitor) throws CoreException {
		IFile me = null;
		if (parent instanceof IFolder) {
			me = ((IFolder)parent).getFile(getName());
		}
		else if (parent instanceof IProject) {
			me = ((IProject)parent).getFile(getName());
		}
		if (me != null) try {
			me.create(getContents(), IResource.NONE, monitor);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

	@Override
	public int computeSize() {
		if (completed)
			return contents.length;
		else
			return header.size();
	}

	@Override
	public C4GroupEntryHeader entryHeader() {
		return header;
	}

	@Override
	public void writeTo(OutputStream stream) throws IOException {
		InputStream inStream = new java.io.FileInputStream(exportFromFile);
		try {
			byte[] buffer = new byte[1024];
			int read = 0;
			try {
				while((read = inStream.read(buffer)) > 0) {
					stream.write(buffer, 0, read);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		} finally {
			inStream.close();
		}
	}

	@Override
	public IPath getFullPath() {
		return ITreeNode.Default.path(this);
	}

	@Override
	public IPath path() {
		return getFullPath();
	}

	@Override
	public boolean isReadOnly() {
		return true;
	}

	@Override
	@SuppressWarnings("rawtypes")
	public Object getAdapter(Class cls) {
		if (cls == C4GroupFile.class)
			return this;
		return null;
	}

	@Override
	public String nodeName() {
		return getName();
	}

	@Override
	public String[] childNames(int options, IProgressMonitor monitor) throws CoreException {
		return NO_CHILDNAMES;
	}

	@Override
	public IFileInfo fetchInfo(int options, IProgressMonitor monitor) throws CoreException {
		FileInfo fileInfo = new FileInfo(getName());
		fileInfo.setExists(true);
		fileInfo.setLastModified(parentGroup().lastModified());
		return fileInfo;
	}

	@Override
	public IFileStore getChild(String name) {
		return null; // go away :c
	}

	@Override
	public IFileStore getParent() {
		return parentGroup();
	}

	@Override
	public InputStream openInputStream(int options, IProgressMonitor monitor) throws CoreException {
		return getContents();
	}
	
	@Override
	public File toLocalFile(int options, IProgressMonitor monitor) throws CoreException {
		try {
			return CACHE.getCachedFile(this);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

}