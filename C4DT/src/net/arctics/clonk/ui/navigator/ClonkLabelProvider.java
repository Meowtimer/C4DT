package net.arctics.clonk.ui.navigator;

import net.arctics.clonk.ClonkCore;
import net.arctics.clonk.index.Definition;
import net.arctics.clonk.resource.ClonkProjectNature;
import net.arctics.clonk.resource.c4group.C4Group.GroupType;
import net.arctics.clonk.ui.OverlayIcon;
import net.arctics.clonk.util.INode;
import net.arctics.clonk.util.UI;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.LabelProviderChangedEvent;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

public class ClonkLabelProvider extends LabelProvider implements IStyledLabelProvider {
	
	public ClonkLabelProvider() {
	}
	
	public Image getImage(Object element) {
		if (element instanceof IProject) {
			return super.getImage(element);
		}
		else if (element instanceof IFile) {
			if (element.toString().endsWith(".c")) { //$NON-NLS-1$
				return UI.SCRIPT_ICON;
			}
			if (element.toString().endsWith(".txt")) { //$NON-NLS-1$
				return UI.TEXT_ICON;
			}
			if (element.toString().endsWith(".c4m")) { //$NON-NLS-1$
				return UI.MATERIAL_ICON;
			}
		}
		else if (element instanceof IFolder) {
			IFolder folder = (IFolder)element;
			GroupType groupType = ClonkProjectNature.getEngine(folder).getGroupTypeForFileName(folder.getName());
			
			if (groupType == GroupType.FolderGroup) {
				return UI.FOLDER_ICON;
			}
			else if (groupType == GroupType.DefinitionGroup) {
				return UI.GENERAL_OBJECT_ICON;
			}
			else if (groupType == GroupType.ScenarioGroup) {
				return UI.SCENARIO_ICON;
			}
			else if (groupType == GroupType.ResourceGroup) {
				return UI.GROUP_ICON;
			}
		}
		return UI.getIconForObject(element);
	}

	public String getText(Object element) {
		if (element instanceof IProject) {
			return ((IProject)element).getName();
		}
		else if (element instanceof IFile) {
			return ((IFile)element).getName();
		}
		return super.getText(element);
	}

	public static String stringWithoutExtension(String s) {
		return s.substring(0,s.lastIndexOf(".")); //$NON-NLS-1$
	}
	
	public StyledString getStyledText(Object element) {
		if (element instanceof IFolder) {
			IFolder folder = (IFolder)element;
			GroupType groupType = ClonkProjectNature.getEngine(folder).getGroupTypeForFileName(folder.getName());
			if (groupType == GroupType.DefinitionGroup) {
				// add [C4ID] to .c4d folders
				try {
					String c4id = folder.getPersistentProperty(ClonkCore.FOLDER_C4ID_PROPERTY_ID);
					return getIDText(folder.getName(), c4id, false);
				} catch (CoreException e) {
					e.printStackTrace();
				}
			}
			if (groupType == GroupType.FolderGroup || groupType == GroupType.ScenarioGroup || groupType == GroupType.ResourceGroup)
				return new StyledString(folder.getName().substring(0,folder.getName().lastIndexOf("."))); //$NON-NLS-1$
			return new StyledString(((IFolder)element).getName());
		}
		else if (element instanceof IResource) {
			return new StyledString(((IResource)element).getName());
		}
		else if (element instanceof Definition) {
			Definition obj = (Definition) element;
			String c4id = obj.getId().toString();
			return getIDText(obj.getNodeName(), c4id, true);
		}
		else if (element instanceof INode) {
			return new StyledString(element.toString(), StyledString.COUNTER_STYLER);
		}
		return new StyledString(element.toString());
	}

	private StyledString getIDText(String baseName, String id, boolean virtual) {
		StyledString buf = new StyledString();
		if (virtual)
			buf.append(stringWithoutExtension(baseName), StyledString.COUNTER_STYLER);
		else
			buf.append(stringWithoutExtension(baseName));
		if (id != null) {
			buf.append(" [",StyledString.DECORATIONS_STYLER); //$NON-NLS-1$
			buf.append(id,StyledString.DECORATIONS_STYLER);
			buf.append("]",StyledString.DECORATIONS_STYLER); //$NON-NLS-1$
		}
		return buf;
	}
	
	protected static ImageDescriptor decorateImage(ImageDescriptor input,
			Object element) {
		return new OverlayIcon(input,computeOverlays(element),new Point(22,16));
	}
	
	protected static ImageDescriptor[][] computeOverlays(Object element) {
		ImageDescriptor[][] result = new ImageDescriptor[4][1];
		if (element instanceof IResource) {
			IResource res = (IResource)element;
			try {
				IMarker[] markers = res.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
				if (markers.length > 0) {
					for(IMarker marker : markers) {
						if (marker.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO) == IMarker.SEVERITY_ERROR) {
							result[2][0] = UI.getIconDescriptor("icons/error_co.gif"); //$NON-NLS-1$
							break;
						}
						if (marker.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO) == IMarker.SEVERITY_WARNING) {
							result[2][0] = UI.getIconDescriptor("icons/warning_co.gif"); //$NON-NLS-1$
						}
					}
				}
			} catch (CoreException e) {
				e.printStackTrace();
				return null;
			}
		}
		return result;
	}
//
//
//	protected String decorateText(String input, Object element) {
//		if (element instanceof IProject) {
//			return ((IProject)element).getName();
//		}
//		else if (element instanceof IFile) {
//			return ((IFile)element).getName();
//		}
//		return super.decorateText(input, element);
//	}

	public void dispose() {
		super.dispose();
	}

	public boolean isLabelProperty(Object element, String property) {
		// TODO Auto-generated method stub
		return true;
	}
	
	public void testRefresh() {
		fireLabelProviderChanged(new LabelProviderChangedEvent(this));
	}
	
//	public static ClonkLabelProvider instance;
//
//	public static ClonkLabelProvider getInstance() {
//		return instance;
//	}
	
	public void addListener(ILabelProviderListener listener) {
		// TODO Auto-generated method stub
		
	}

	public void removeListener(ILabelProviderListener listener) {
		// TODO Auto-generated method stub
		
	}
	
}
