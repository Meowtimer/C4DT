package net.arctics.clonk.ui.editors;

import java.net.URL;

import net.arctics.clonk.ClonkCore;
import net.arctics.clonk.parser.C4Declaration;
import net.arctics.clonk.preferences.PreferenceConstants;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.ui.browser.IWebBrowser;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;
import org.eclipse.ui.internal.browser.WorkbenchBrowserSupport;

/**
 * A hyperlink that stores a reference to the hyperlinked Clonk declaration
 */
@SuppressWarnings("restriction")
public class ClonkHyperlink implements IHyperlink {

	private final IRegion region;
	protected C4Declaration target;

	public ClonkHyperlink(IRegion region, C4Declaration target) {
		super();
		this.region = region;
		this.target = target;
	}

	public IRegion getHyperlinkRegion() {
		return region;
	}

	public String getHyperlinkText() {
		return target.getName();
	}

	public String getTypeLabel() {
		return "Clonk Hyperlink";
	}

	public void open() {
		try {
			if (ClonkTextEditor.openDeclaration(target) == null) {
				// can't open editor so try something else like opening up a documentation page in the browser
				if (target.isEngineDeclaration()) {
					String docURLTemplate = PreferenceConstants.getPreference(PreferenceConstants.DOC_URL_TEMPLATE, PreferenceConstants.DOC_URL_TEMPLATE_DEFAULT, null);
					IWorkbenchBrowserSupport support = WorkbenchBrowserSupport.getInstance();
					IWebBrowser browser;
					if (support.isInternalWebBrowserAvailable()) {
						browser = support.createBrowser(null);
					}
					else {
						browser = support.getExternalBrowser();
					}
					if (browser != null)
						browser.openURL(new URL(String.format(
							docURLTemplate,
							target.getName(), ClonkCore.getDefault().getLanguagePref().toLowerCase()
						)));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public IRegion getRegion() {
		return region;
	}

	public C4Declaration getTarget() {
		return target;
	}

}