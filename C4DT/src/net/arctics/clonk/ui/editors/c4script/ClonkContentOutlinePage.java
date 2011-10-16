package net.arctics.clonk.ui.editors.c4script;

import net.arctics.clonk.parser.Declaration;
import net.arctics.clonk.ui.editors.ClonkTextEditor;
import net.arctics.clonk.ui.navigator.ClonkOutlineProvider;
import net.arctics.clonk.util.StringUtil;

import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.views.contentoutline.ContentOutlinePage;

public class ClonkContentOutlinePage extends ContentOutlinePage {

	private Composite composite;
	private ClonkTextEditor editor;
	private Text filterBox;
	
	@Override
	public Control getControl() {
		return composite;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.views.contentoutline.ContentOutlinePage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	@Override
	public void createControl(Composite parent) {
		composite = new Composite(parent, SWT.NO_SCROLL);
		GridLayout layout = new GridLayout(1, false);
		composite.setLayout(layout);
		filterBox = new Text(composite, SWT.SEARCH | SWT.CANCEL);
		filterBox.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		filterBox.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				getTreeViewer().refresh();
			}
		});
		super.createControl(composite);
		getTreeViewer().getTree().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		getTreeViewer().setFilters(new ViewerFilter[] {
			new ViewerFilter() {
				@Override
				public boolean select(Viewer viewer, Object parentElement, Object element) {
					if (StringUtil.patternFromRegExOrWildcard(filterBox.getText()).matcher(((ILabelProvider)getTreeViewer().getLabelProvider()).getText(element)).find())
						return true;
					if (element instanceof Declaration) {
						if (((Declaration)element).hasSubDeclarationsInOutline()) {
							for (Object sd : ((Declaration)element).getSubDeclarationsForOutline())
								if (select(viewer, element, sd))
									return true;
						}
					}
					return false;
				}
			}
		});
		if (editor != null) {
			Declaration topLevelDeclaration = getEditor().topLevelDeclaration();
			if (topLevelDeclaration != null) {
				setTreeViewerInput(topLevelDeclaration);
			}
		}
		parent.layout();
	}

	private static final ViewerSorter DECLARATION_SORTER = new ViewerSorter() {
		@Override
		public int category(Object element) {
			return ((Declaration)element).sortCategory();
		}
	};
	
	private void setTreeViewerInput(Declaration obj) {
		TreeViewer treeViewer = this.getTreeViewer();
		if (treeViewer == null)
			return;
		ClonkOutlineProvider provider = new ClonkOutlineProvider();
		treeViewer.setLabelProvider(provider);
		treeViewer.setContentProvider(provider);
		treeViewer.setSorter(DECLARATION_SORTER);
		treeViewer.setInput(obj);
		treeViewer.refresh();
	}

	@Override
	public void selectionChanged(SelectionChangedEvent event) {
		if (event.getSelection().isEmpty()) {
			return;
		} else if (event.getSelection() instanceof IStructuredSelection) {
			Declaration dec = (Declaration)((IStructuredSelection)event.getSelection()).getFirstElement();
			dec = dec.latestVersion();
			if (dec != null) {
				editor.selectAndReveal(dec.getLocation());
			}
		}
	}

	/**
	 * @param clonkTextEditor the editor to set
	 */
	public void setEditor(ClonkTextEditor clonkTextEditor) {
		this.editor = clonkTextEditor;
	}

	/**
	 * @return the editor
	 */
	public ClonkTextEditor getEditor() {
		return editor;
	}

	public void refresh() {
		Declaration newInput = getEditor().topLevelDeclaration();
		if (getTreeViewer().getInput() != newInput)
			setTreeViewerInput(newInput);
		else
			getTreeViewer().refresh();
	}
	
	public void select(Declaration field) {
		TreeViewer viewer = getTreeViewer();
		viewer.removeSelectionChangedListener(this);
		try {
			this.setSelection(new StructuredSelection(field));
		} finally {
			viewer.addSelectionChangedListener(this);
		}
	}
	
	public void setInput(Object input) {
		getTreeViewer().setInput(input);
	}

	public void clear() {
		getTreeViewer().setInput(null);
	}
	
}
