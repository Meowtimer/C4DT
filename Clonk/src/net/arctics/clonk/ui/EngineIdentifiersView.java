package net.arctics.clonk.ui;

import java.util.ArrayList;
import java.util.List;

import net.arctics.clonk.ClonkCore;
import net.arctics.clonk.parser.C4Field;
import net.arctics.clonk.parser.C4Function;
import net.arctics.clonk.parser.C4Type;
import net.arctics.clonk.parser.C4Variable;
import net.arctics.clonk.parser.C4Function.C4FunctionScope;
import net.arctics.clonk.parser.C4Variable.C4VariableScope;
import net.arctics.clonk.ui.editors.ClonkContentOutlineLabelAndContentProvider;
import net.arctics.clonk.ui.editors.TestVisualEditor;

import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.part.*;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.jface.action.*;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.TrayDialog;
import org.eclipse.ui.*;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.SWT;
import org.eclipse.core.runtime.IAdaptable;


/**
 * This sample class demonstrates how to plug-in a new
 * workbench view. The view shows data obtained from the
 * model. The sample creates a dummy model on the fly,
 * but a real implementation would connect to the model
 * available either in this or another plug-in (e.g. the workspace).
 * The view is connected to the model using a content provider.
 * <p>
 * The view uses a label provider to define how model
 * objects should be presented in the view. Each
 * view can present the same model objects using
 * different labels and icons, if needed. Alternatively,
 * a single label provider can be shared between views
 * in order to ensure that objects of the same type are
 * presented in the same way everywhere.
 * <p>
 */

public class EngineIdentifiersView extends ViewPart {
	
	protected class EditIdentifierInputDialog extends Dialog {
		
		private class ParameterCombination {
			private Combo type;
			private Text name;
			
			public ParameterCombination(Combo type, Text name) {
				this.type = type;
				this.name = name;
			}

			/**
			 * @return the type
			 */
			public Combo getType() {
				return type;
			}

			/**
			 * @return the name
			 */
			public Text getName() {
				return name;
			}
		}
		
		private Button newParameter;
		private C4Field identifier;
		private Text identifierNameField;
		private Combo returnTypeBox;
		private Combo scopeBox;
		private List<ParameterCombination> parameters = new ArrayList<ParameterCombination>();
		
		public EditIdentifierInputDialog(Shell parent) {
			super(parent);
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jface.dialogs.Dialog#createDialogArea(org.eclipse.swt.widgets.Composite)
		 */
		@Override
		protected Control createDialogArea(Composite parent) {
			
			Composite composite = (Composite) super.createDialogArea(parent);
			
			composite.setLayout(new GridLayout(2,false));
			
			Object activeElement = getActiveElement();
			if (!(activeElement instanceof C4Field)) {
				return null;
			}
			identifier = (C4Field) activeElement;
			if (activeElement instanceof C4Function) {
				createFunctionEditDialog(composite,(C4Function) identifier);
			}
			else if (activeElement instanceof C4Variable) {
				createVariableEditDialog(composite, (C4Variable) identifier);
			}
			
			return composite;
		}
		
		private void createNewParameterButton(final Composite parent) {
			if (newParameter != null) {
				newParameter.dispose();
				newParameter = null;
			}
			newParameter = new Button(parent, SWT.PUSH);
			newParameter.setText("New Parameter");
			newParameter.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					newParameter.dispose();
					newParameter = null;

					createParameterControls(parent);
					createNewParameterButton(parent);

					parent.layout(true); // show new controls
					parent.pack(true); // resize sub composite
					parent.getParent().pack(true); // resize composite
					parent.getParent().getParent().pack(true); // resize window 
				}
			});
		}
		
		@Override
		protected void okPressed() {
			if (identifier instanceof C4Function) {
				C4Function func = (C4Function) identifier;
				func.setName(identifierNameField.getText());
				func.setReturnType(C4Type.makeType(returnTypeBox.getItem(returnTypeBox.getSelectionIndex())));
				func.setVisibility(C4FunctionScope.makeScope(scopeBox.getItem(scopeBox.getSelectionIndex())));
				
				func.getParameter().clear();
				for(ParameterCombination par : parameters) {
					C4Variable var = new C4Variable(par.getName().getText(),C4VariableScope.VAR_LOCAL);
					var.setType(getSelectedType(par.getType()));
					func.getParameter().add(var);
				}
			}
			else if (identifier instanceof C4Variable) {
				C4Variable var = (C4Variable) identifier;
				var.setName(identifierNameField.getText());
				var.setType(C4Type.makeType(returnTypeBox.getItem(returnTypeBox.getSelectionIndex())));
				var.setScope(C4VariableScope.makeScope(scopeBox.getItem(scopeBox.getSelectionIndex())));
			}
			
			super.okPressed();
		}
		
		private C4Type getSelectedType(Combo combo) {
			return C4Type.makeType(combo.getItem(combo.getSelectionIndex()));
		}

		private void createVariableEditDialog(Composite parent,
				C4Variable var) {
			// set title
			parent.getShell().setText("Edit variable " + var.getName());
			
			new Label(parent, SWT.NONE).setText("Name: ");
			
			identifierNameField = new Text(parent, SWT.BORDER | SWT.SINGLE);
			identifierNameField.setText(var.getName());
			
			new Label(parent, SWT.NONE).setText("Type: ");
			returnTypeBox = createComboBoxForType(parent, var.getType());
		
			new Label(parent, SWT.NONE).setText("Scope: ");
			scopeBox = createComboBoxForScope(parent, var.getScope());
		}

		private void createFunctionEditDialog(Composite parent, C4Function func) {
			
			// set title
			parent.getShell().setText("Edit function " + func.getName());
			
			new Label(parent, SWT.NONE).setText("Name: ");
			
			identifierNameField = new Text(parent, SWT.BORDER | SWT.SINGLE);
			identifierNameField.setText(func.getName());
			
			new Label(parent, SWT.NONE).setText("Return type: ");
			returnTypeBox = createComboBoxForType(parent, func.getReturnType());
			
			new Label(parent, SWT.NONE).setText("Scope: ");
			scopeBox = createComboBoxForScope(parent, func.getVisibility());
			
			new Label(parent, SWT.NONE).setText(" "); // placeholder
			new Label(parent, SWT.NONE).setText(" ");
			
			for(C4Variable par : func.getParameter()) {
				createParameterControls(parent, par.getType(), par.getName());
			}
			
			createNewParameterButton(parent);
			
		}
		
		private void createParameterControls(Composite parent) {
			createParameterControls(parent, C4Type.ANY, "");
		}
		
		private void createParameterControls(Composite parent, C4Type type, String parameterName) {
			Combo combo = createComboBoxForType(parent, type);
			Text parNameField = new Text(parent, SWT.BORDER | SWT.SINGLE);
			parNameField.setText(parameterName);
			parameters.add(new ParameterCombination(combo, parNameField));
		}
		
		private Combo createComboBoxForScope(Composite parent, Object scope) {
			Object[] values = null;
			if (scope instanceof C4VariableScope) {
				values = C4VariableScope.values();
			}
			else if (scope instanceof C4FunctionScope) {
				values = C4FunctionScope.values();
			}
			Combo combo = new Combo(parent, SWT.READ_ONLY);
			int select = 0;
			List<String> items = new ArrayList<String>(values.length);
			for(int i = 0; i < values.length;i++) {
				items.add(values[i].toString());
				if (scope == values[i])
					select = i;
			}
			combo.setItems(items.toArray(new String[items.size()]));
			combo.select(select);	
			
			return combo;
		}
		
		private Combo createComboBoxForType(Composite parent, C4Type currentType) {
			Combo combo = new Combo(parent, SWT.READ_ONLY);
			int select = 0;
			List<String> items = new ArrayList<String>(C4Type.values().length);
			for(int i = 0; i < C4Type.values().length;i++) {
				items.add(C4Type.values()[i].toString());
				if (currentType == C4Type.values()[i])
					select = i;
			}
			combo.setItems(items.toArray(new String[items.size()]));
			combo.select(select);	
			
			return combo;
		}
		
		private Object getActiveElement() {
			return viewer.getTree().getSelection()[0].getData();
		}
	}
	
	protected TreeViewer viewer;
	private DrillDownAdapter drillDownAdapter;
	private Action editAction;
	private Action saveAction;
	private Action doubleClickAction;

	/**
	 * The constructor.
	 */
	public EngineIdentifiersView() {
	}

	/**
	 * This is a callback that will allow us
	 * to create the viewer and initialize it.
	 */
	public void createPartControl(Composite parent) {
		viewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		drillDownAdapter = new DrillDownAdapter(viewer);
		
		ClonkContentOutlineLabelAndContentProvider provider = new ClonkContentOutlineLabelAndContentProvider();
		viewer.setContentProvider(provider);
		viewer.setLabelProvider(provider);
		viewer.setSorter(new ViewerSorter() {
			public int category(Object element) {
				return ((C4Field)element).sortCategory();
			}
		});
		viewer.setInput(ClonkCore.ENGINE_OBJECT);
		
		makeActions();
		hookContextMenu();
		hookDoubleClickAction();
		contributeToActionBars();
	}

	/**
	 * Refreshes this viewer completely with information freshly obtained from this viewer's model.
	 */
	public void refresh() {
		viewer.refresh();
	}
	
	private void hookContextMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				EngineIdentifiersView.this.fillContextMenu(manager);
			}
		});
		Menu menu = menuMgr.createContextMenu(viewer.getControl());
		viewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, viewer);
	}

	private void contributeToActionBars() {
		IActionBars bars = getViewSite().getActionBars();
		fillLocalPullDown(bars.getMenuManager());
		fillLocalToolBar(bars.getToolBarManager());
	}

	private void fillLocalPullDown(IMenuManager manager) {
		manager.add(editAction);
		manager.add(new Separator());
		manager.add(saveAction);
	}

	private void fillContextMenu(IMenuManager manager) {
		manager.add(editAction);
		manager.add(saveAction);
		manager.add(new Separator());
		drillDownAdapter.addNavigationActions(manager);
		// Other plug-ins can contribute there actions here
		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	}
	
	private void fillLocalToolBar(IToolBarManager manager) {
//		manager.add(editAction);
		manager.add(saveAction);
		manager.add(new Separator());
		drillDownAdapter.addNavigationActions(manager);
	}

	private void makeActions() {
		editAction = new Action() {
			public void run() {
//				Tree tree = viewer.getTree();
//				TreeItem item = tree.getSelection()[0];
//				Object data = item.getData();
//				viewer.editElement(data, 0);
//				viewer.editElement(((IStructuredSelection)viewer.getSelection()).getFirstElement(), 0);
				Dialog dialog = new EditIdentifierInputDialog(viewer.getControl().getShell());
				dialog.open();
				refresh();
			}
		};
		editAction.setText("Edit");
		editAction.setToolTipText("Edit this identifier");
		editAction.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().
			getImageDescriptor(ISharedImages.IMG_OBJS_INFO_TSK));
		
		saveAction = new Action() {
			public void run() {
				ClonkCore.saveEngineObject();
			}
		};
		saveAction.setText("Save");
		saveAction.setToolTipText("Save changes");
		saveAction.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().
				getImageDescriptor(ISharedImages.IMG_ETOOL_SAVE_EDIT));
		doubleClickAction = new Action() {
			public void run() {
				ISelection selection = viewer.getSelection();
				Object obj = ((IStructuredSelection)selection).getFirstElement();
				showMessage("Double-click detected on "+obj.toString());
			}
		};
	}

	private void hookDoubleClickAction() {
		viewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				doubleClickAction.run();
			}
		});
	}
	private void showMessage(String message) {
		MessageDialog.openInformation(
			viewer.getControl().getShell(),
			"Engine identifiers",
			message);
	}

	/**
	 * Passing the focus request to the viewer's control.
	 */
	public void setFocus() {
		viewer.getControl().setFocus();
	}
}