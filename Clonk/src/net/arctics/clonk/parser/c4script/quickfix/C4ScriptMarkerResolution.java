package net.arctics.clonk.parser.c4script.quickfix;

import net.arctics.clonk.ClonkCore;
import net.arctics.clonk.parser.c4script.C4Function;
import net.arctics.clonk.parser.c4script.C4ScriptBase;
import net.arctics.clonk.parser.c4script.C4ScriptParser;
import net.arctics.clonk.parser.c4script.C4ScriptExprTree.ExprElm;
import net.arctics.clonk.ui.editors.c4script.ExpressionLocator;
import net.arctics.clonk.util.Utilities;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.IMarkerResolution2;
import org.eclipse.ui.editors.text.TextFileDocumentProvider;

public class C4ScriptMarkerResolution implements IMarkerResolution, IMarkerResolution2 {

	private String label;
	private String description;
	private Image image;
	private IRegion region;
	
	public C4ScriptMarkerResolution(IMarker marker) {
		this.label = "Fix " + marker.getAttribute(IMarker.MESSAGE, "Mysterious");
		this.description = "Automated fix";
		int charStart = marker.getAttribute(IMarker.CHAR_START, 0), charEnd = marker.getAttribute(IMarker.CHAR_END, 0);
		this.region = new Region(charStart, charEnd-charStart);
	}
	
	public String getLabel() {
		return label;
	}

	public void run(IMarker marker) {
		C4ScriptBase script = Utilities.getScriptForFile((IFile) marker.getResource());
		C4Function func = script.funcAt(region.getOffset()); 
		ExpressionLocator locator = new ExpressionLocator(region.getOffset()-func.getBody().getOffset());
		TextFileDocumentProvider provider = ClonkCore.getDefault().getTextFileDocumentProvider();
		try {
			provider.connect(marker.getResource());
		} catch (CoreException e1) {
			e1.printStackTrace();
			return;
		}
		IDocument doc = provider.getDocument(marker.getResource());
		C4ScriptParser parser;
		try {
			parser = C4ScriptParser.reportExpressionsAndStatements(doc, func.getBody(), script, func, locator);
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		ExprElm expr = locator.getTopLevelInRegion();
		if (expr != null) {
			try {
				doc.replace(expr.getOffset()+func.getBody().getOffset(), expr.getLength(), expr.exhaustiveNewStyleReplacement(parser).toString());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public String getDescription() {
		return description;
	}

	public Image getImage() {
		return image;
	}

}
