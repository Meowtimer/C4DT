package net.arctics.clonk.ui.editors.ini;

import java.io.InputStream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.editor.FormEditor;
import org.eclipse.ui.forms.editor.FormPage;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ScrolledForm;
import org.eclipse.ui.part.IShowInSource;
import org.eclipse.ui.part.IShowInTargetList;
import org.eclipse.ui.texteditor.IDocumentProvider;

import net.arctics.clonk.ClonkCore;
import net.arctics.clonk.parser.C4Object;
import net.arctics.clonk.ui.editors.c4script.ColorManager;
import net.arctics.clonk.ui.editors.c4script.ShowInAdapter;
import net.arctics.clonk.ui.editors.ini.IniDocumentProvider;
import net.arctics.clonk.ui.editors.ini.IniSourceViewerConfiguration;
import net.arctics.clonk.util.IHasKeyAndValue;
import net.arctics.clonk.util.Utilities;

import org.eclipse.jface.dialogs.IPageChangedListener;
import org.eclipse.jface.dialogs.PageChangedEvent;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.CheckboxCellEditor;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.ui.forms.SectionPart;
import org.eclipse.ui.forms.widgets.Section;

import net.arctics.clonk.parser.inireader.Boolean;
import net.arctics.clonk.parser.inireader.ComplexIniEntry;
import net.arctics.clonk.parser.inireader.IniReader;
import net.arctics.clonk.ui.editors.ini.IniEditor;
import net.arctics.clonk.ui.editors.ini.IniEditorColumnLabelAndContentProvider;

public abstract class IniEditor extends FormEditor {

	private IDocumentProvider documentProvider;
	private ShowInAdapter showInAdapter;
	private RawSourcePage sourcePage;
	private IniSectionPage sectionPage;
	
	public IniEditor() {
	}

	public static class IniSectionPage extends FormPage {
		
		protected IDocumentProvider documentProvider;
		protected IniReader iniReader;
		protected Class<? extends IniReader> iniReaderClass;
		protected TreeViewer treeViewer;
		
		public <T extends IniReader> T createIniReader(Class<T> cls, Object arg) {
			iniReaderClass = cls;
			T result;
			try {
				Class<?> argClass = arg instanceof IFile ? IFile.class : InputStream.class;
				result = cls.getConstructor(argClass).newInstance(arg);
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
			result.parse();
			iniReader = result;
			return result;
		}
		
		public void updateIniReader(Object arg) {
			if (iniReaderClass != null) {
				createIniReader(iniReaderClass, arg);
				if (treeViewer != null)
					treeViewer.setInput(iniReader);
			}
		}
		
		public IniSectionPage(FormEditor editor, String id, String title, IDocumentProvider docProvider, Class<? extends IniReader> iniReaderClass) {
			super(editor, id, title);
			this.iniReaderClass = iniReaderClass;
			documentProvider = docProvider;
			if (iniReaderClass != null)
				updateIniReader(Utilities.getEditingFile(getEditor()));
		}

		@Override
		public void doSave(IProgressMonitor monitor) {
			super.doSave(monitor);
			try {
				documentProvider.saveDocument(monitor, getEditorInput(), documentProvider.getDocument(getEditorInput()), true);
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}
		
		protected String subHeader() {
			return "Contents";
		}
		
		protected void createFormContent(IManagedForm managedForm) {
			super.createFormContent(managedForm);

			FormToolkit toolkit = managedForm.getToolkit();
			ScrolledForm form = managedForm.getForm();
			toolkit.decorateFormHeading(form.getForm());
			
			IFile input = Utilities.getEditingFile(getEditor());
			if (input != null) {
				try { // XXX values should come from document - not from builder cache
					//IContainer cont = input.getParent();
					C4Object obj = (C4Object) input.getParent().getSessionProperty(ClonkCore.C4OBJECT_PROPERTY_ID);
					if (obj != null) {
						form.setText(obj.getName() + "(" + obj.getId().getName() + ")");
					}
					else {
						form.setText("Options");
					}
				} catch (CoreException e) {
					e.printStackTrace();
				}
			}

			Layout layout = new GridLayout(1, true);
			form.getBody().setLayout(layout);
			form.getBody().setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

			SectionPart part = new SectionPart(form.getBody(), toolkit,Section.CLIENT_INDENT | Section.TITLE_BAR | Section.EXPANDED);
			part.getSection().setText(subHeader());
			part.getSection().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

			Composite sectionComp = toolkit.createComposite(part.getSection());
			sectionComp.setLayout(new GridLayout(1, false));
			sectionComp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

			part.getSection().setClient(sectionComp);

			createTreeViewer(toolkit, sectionComp);

		}

		private void createTreeViewer(FormToolkit toolkit, Composite sectionComp) {
			Tree tree = toolkit.createTree(sectionComp, SWT.BOTTOM | SWT.FULL_SELECTION);
			tree.setHeaderVisible(true);
			tree.setLinesVisible(true);
			tree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			
//			TreeColumn keyCol = new TreeColumn(tree, SWT.CENTER, 0);
//			keyCol.setText("Key");
//			keyCol.setWidth(200);
//			
//			TreeColumn valCol = new TreeColumn(tree, SWT.LEFT, 1);
//			valCol.setText("Value");
//			valCol.setWidth(200);
			
//			TreeColumn descCol = new TreeColumn(actionsTree, SWT.LEFT, 2);
//			descCol.setText("Description");
//			descCol.setWidth(200);
			
			treeViewer = new TreeViewer(tree);
			
			TreeViewerColumn keyCol = new TreeViewerColumn(treeViewer, SWT.CENTER, 0);
			keyCol.getColumn().setText("Key");
			keyCol.getColumn().setWidth(200);
			
			TreeViewerColumn valCol = new TreeViewerColumn(treeViewer, SWT.LEFT, 1);
			valCol.getColumn().setText("Value");
			valCol.getColumn().setWidth(200);
			valCol.setEditingSupport(new EditingSupport(treeViewer) {

				private TextCellEditor textEditor;
				private CheckboxCellEditor checkboxEditor;
				
				@Override
				protected boolean canEdit(Object element) {
					return true;
				}

				@Override
				protected CellEditor getCellEditor(Object element) {
					if (element instanceof ComplexIniEntry) {
						ComplexIniEntry complex = (ComplexIniEntry) element;
						if (complex.getExtendedValue() instanceof Boolean)
							return getCheckboxEditor();
					}
					return getTextEditor();
				}

				@SuppressWarnings("unchecked")
				@Override
				protected Object getValue(Object element) {
					if (element instanceof ComplexIniEntry) {
						ComplexIniEntry complex = (ComplexIniEntry) element;
						if (complex.getExtendedValue() instanceof Boolean)
							return ((Boolean)complex.getExtendedValue()).getNumber() != 0 ? true : false;
					}
					return ((IHasKeyAndValue<String, String>)element).getValue();
				}

				@SuppressWarnings("unchecked")
				@Override
				protected void setValue(Object element, Object value) {
					if (element instanceof ComplexIniEntry) {
						ComplexIniEntry complex = (ComplexIniEntry) element;
						if (complex.getExtendedValue() instanceof Boolean) {
							((Boolean)complex.getExtendedValue()).setNumber(value.equals(true) ? 1 : 0);
							this.getViewer().refresh();
							return;
						}
					}
					((IHasKeyAndValue<String, String>)element).setValue(value.toString());
					this.getViewer().refresh();
				}

				public TextCellEditor getTextEditor() {
					if (textEditor == null)
						textEditor = new TextCellEditor((Composite)this.getViewer().getControl());
					return textEditor;
				}

				public CheckboxCellEditor getCheckboxEditor() {
					if (checkboxEditor == null)
						checkboxEditor = new CheckboxCellEditor((Composite)this.getViewer().getControl());
					return checkboxEditor;
				}
				
			});
			
			TreeViewerColumn descCol = new TreeViewerColumn(treeViewer, SWT.LEFT, 2);
			descCol.getColumn().setText("Description");
			descCol.getColumn().setWidth(200);
			
			treeViewer.setColumnProperties(new String[] {"key", "value"});
			IniEditorColumnLabelAndContentProvider provider = new IniEditorColumnLabelAndContentProvider(iniReader.getConfiguration());
			treeViewer.setLabelProvider(provider);
			treeViewer.setContentProvider(provider);
			treeViewer.setComparator(new ViewerComparator() {
				@SuppressWarnings("unchecked")
				@Override
				public int compare(Viewer viewer, Object e1, Object e2) {
					IHasKeyAndValue<String, String> a = (IHasKeyAndValue<String, String>) e1;
					IHasKeyAndValue<String, String> b = (IHasKeyAndValue<String, String>) e2;
					return a.getKey().compareToIgnoreCase(b.getKey());
				}
			});
			
			treeViewer.setInput(iniReader);
		}

	}

	public static class RawSourcePage extends TextEditor {
		
		public static final String PAGE_ID = "rawIniEditor";
		
		private ColorManager colorManager;
		private String title;
		
		@Override
		public void doSave(IProgressMonitor progressMonitor) {
			super.doSave(progressMonitor);
		}

		public RawSourcePage(FormEditor editor, String id, String title, IDocumentProvider documentProvider) {
			colorManager = new ColorManager();
			setPartName(title);
			setContentDescription(title);
			this.title = title;
			setSourceViewerConfiguration(new IniSourceViewerConfiguration(colorManager, this));
			setDocumentProvider(documentProvider);
		}

		public void resetPartName() {
			setPartName(title);
		}
	}
	
	@Override
	public void init(IEditorSite site, IEditorInput input)
			throws PartInitException {
		super.init(site, input);
		IResource res = (IResource) input.getAdapter(IResource.class);
		if (res != null) {
			setPartName(res.getParent().getName() + "/" + res.getName());
		}
	}
	
	public enum PageAttribRequest {
		SectionPageClass,
		SectionPageId,
		SectionPageTitle,
		RawSourcePageTitle
	}
	
	protected abstract Object getPageConfiguration(PageAttribRequest request);

	@SuppressWarnings("unchecked")
	@Override
	protected void addPages() {
		try {
			documentProvider = new IniDocumentProvider();
			Class<IniSectionPage> iniSectionPageClass = (Class<IniSectionPage>)getPageConfiguration(PageAttribRequest.SectionPageClass);
			sectionPage = iniSectionPageClass.getConstructor(FormEditor.class, String.class, String.class, IDocumentProvider.class).newInstance(
					this, getPageConfiguration(PageAttribRequest.SectionPageId), getPageConfiguration(PageAttribRequest.SectionPageTitle), documentProvider);
			addPage(sectionPage);
			sourcePage = new RawSourcePage(this, RawSourcePage.PAGE_ID, (String) getPageConfiguration(PageAttribRequest.RawSourcePageTitle), documentProvider);
			int index = addPage(sourcePage, this.getEditorInput());
			// editors as pages are not able to handle tab title strings
			// so here is a dirty trick:
			if (getContainer() instanceof CTabFolder)
				((CTabFolder)getContainer()).getItem(index).setText((String)getPageConfiguration(PageAttribRequest.RawSourcePageTitle));
			addPageChangedListener(new IPageChangedListener() {

				public void pageChanged(PageChangedEvent event) {
					try {
						if (event.getSelectedPage() == sectionPage) {
							IEditorInput input = sectionPage.getEditorInput();
							System.out.println(input);
							IStorage sourceStorage = ((IFileEditorInput)sourcePage.getEditorInput()).getStorage();
							sectionPage.updateIniReader(sourceStorage.getContents());
						}
					} catch (CoreException e) {
						e.printStackTrace();
					}
				}
				
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void doSave(IProgressMonitor monitor) {
		try {
			documentProvider.saveDocument(monitor, getEditorInput(), documentProvider.getDocument(getEditorInput()), true);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void doSaveAs() {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean isSaveAsAllowed() {
		// TODO Auto-generated method stub
		return false;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public Object getAdapter(Class adapter) {
		if (adapter.equals(IShowInSource.class) || adapter.equals(IShowInTargetList.class)) {
			if (showInAdapter == null)
				showInAdapter = new ShowInAdapter(this);
			return showInAdapter;
		}
		return super.getAdapter(adapter);
	}

}
