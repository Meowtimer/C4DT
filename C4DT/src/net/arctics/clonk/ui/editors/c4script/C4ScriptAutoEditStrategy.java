package net.arctics.clonk.ui.editors.c4script;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import net.arctics.clonk.ClonkCore;
import net.arctics.clonk.parser.c4script.Function;
import net.arctics.clonk.parser.c4script.MutableRegion;
import net.arctics.clonk.parser.c4script.ast.Conf;
import net.arctics.clonk.preferences.ClonkPreferences;
import net.arctics.clonk.ui.editors.ClonkCompletionProposal;
import net.arctics.clonk.util.WeakListenerManager;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DefaultIndentLineAutoEditStrategy;
import org.eclipse.jface.text.DocumentCommand;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;

/**
 * Planned edit strategies:
 * <ul>
 * <li>smart indent</li>
 * <li><tt>}</tt> insertion after <tt>{</tt>+<tt>enter</tt></li>
 * <li>?automatic closing of <tt>)</tt>? this needs some really good intelligence</li>
 * <li>instead of <tt>\t</tt> always insert two spaces</li>
 * <li>complete parameter insertion when defining an object callback func(e.g. Contained* funcs)</li>
 * </ul>
 * @author ZokRadonh
 *
 */
public class C4ScriptAutoEditStrategy extends DefaultIndentLineAutoEditStrategy implements IPropertyChangeListener {
	
	private static class Autopair {
		public static final int FOLLOWSIDENT = 1;
		public static final int PRECEDESWHITESPACE = 2;
		
		public String start, end;
		public int flags;

		public Autopair(String start, String end, int flags) {
			super();
			this.start = start;
			this.end = end;
			this.flags = flags;
		}

		public boolean applies(DocumentCommand c, IDocument d, int situation) {
			return (flags & situation) == flags && c.text.endsWith(start);
		}
	}
	
	private static final Autopair PARM_BRACKETS_AUTOPAIR = new Autopair("(", ")", Autopair.FOLLOWSIDENT|Autopair.PRECEDESWHITESPACE);
	private static final Autopair[] AUTOPAIRS = {
		PARM_BRACKETS_AUTOPAIR,
		new Autopair("[", "]", Autopair.FOLLOWSIDENT|Autopair.PRECEDESWHITESPACE)
	};
	
	private static class AutoInsertedRegion extends MutableRegion {
		public MutableRegion cause;
		public AutoInsertedRegion(int offset, int length, MutableRegion cause) {
			super(offset, length);
			this.cause = cause;
		}
	}
	
	private static class WeakListenerManagerPCL extends WeakListenerManager<IPropertyChangeListener> implements IPropertyChangeListener	{
		public WeakListenerManagerPCL() {
			ClonkCore.getDefault().getPreferenceStore().addPropertyChangeListener(this);
		}
		@Override
		public void propertyChange(PropertyChangeEvent event) {
			purge();
			for (WeakReference<? extends IPropertyChangeListener> ref : listeners) {
				if (ref.get() != null)
					ref.get().propertyChange(event);
			}
		}
	}
	
	private static final WeakListenerManagerPCL weakListenerManager = new WeakListenerManagerPCL();
	
	private C4ScriptSourceViewerConfiguration configuration;
	private List<AutoInsertedRegion> overrideRegions = new ArrayList<AutoInsertedRegion>(3);
	private boolean disabled;
	
	public C4ScriptSourceViewerConfiguration getConfiguration() {
		return configuration;
	}

	public C4ScriptAutoEditStrategy(C4ScriptSourceViewerConfiguration configuration) {
		this.configuration = configuration;
		weakListenerManager.addListener(this);
	}

	private static boolean looksLikeIdent(IDocument d, int position) throws BadLocationException {
		final int END = 0, DIGITS = 1;
		int state = END;
		while (position >= 0) {
			char c = d.getChar(position);
			switch (state) {
			case DIGITS:
				if (Character.isLetter(c))
					return true;
				else if (!Character.isDigit(c))
					return false;
			case END:
				if (Character.isDigit(c))
					state = DIGITS;
				else if (Character.isLetter(c))
					return true;
				else if (!Character.isWhitespace(c))
					return false;
			}
			--position;
		}
		return false;
	}
	
	@Override
	public void customizeDocumentCommand(IDocument d, DocumentCommand c) {
		if (tabOverOverrideRegion(c))
			return;

		// auto-block
		if (!disabled)
			tryAutoBlock(d, c);

		if (c.text.length() == 0 && c.length > 0) {
			regionDeleted(c.offset, c.length, null, c, d);
		}
		else if (c.text.length() > 0 && c.length > 0) {
			// too complex; give up o_o
			overrideRegions.clear();
		}
		else {
			textAdded(d, c);
		}

		super.customizeDocumentCommand(d, c);
	}

	private boolean tabOverOverrideRegion(DocumentCommand c) {
		// tabbing over override regions
		if (c.text.equals("\t")) { //$NON-NLS-1$
			for (int i = overrideRegions.size()-1; i >= 0; i--) {
				MutableRegion r = overrideRegions.get(i);
				if (r.getOffset() == c.offset) {
					overrideRegions.remove(i);
					c.text = ""; //$NON-NLS-1$
					c.shiftsCaret = false;
					c.caretOffset = r.getOffset()+r.getLength();
					return true;
				}
			}
		}
		return false;
	}

	private void tryAutoBlock(IDocument d, DocumentCommand c) {
		try {
			if (c.text.endsWith("\n") && c.offset > 0 && d.getChar(c.offset-1) == '{') { //$NON-NLS-1$
				Function f = ((C4ScriptEditor)getConfiguration().getEditor()).getFuncAtCursor();
				if (f != null && unbalanced(d, f.getBody())) {
					IRegion r = d.getLineInformationOfOffset(c.offset);
					int start = r.getOffset();
					int end = findEndOfWhiteSpace(d, start, c.offset);
					if (end > start) {
						c.text += d.get(start, end-start) + Conf.indentString;
					}
					c.caretOffset = c.offset + c.text.length();
					c.shiftsCaret = false;
					c.text += "\n" + d.get(start, end-start) + "}"; //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
		} catch (BadLocationException e1) {
			e1.printStackTrace();
		}
	}

	private void textAdded(IDocument d, DocumentCommand c) {
		// user writes override region text himself - noop
		boolean overrideRegionTrespassed = false;
		overrideRegionTrespassed = ignoreUserInputIfDuplicatingAutoPair(d, c);

		AutoInsertedRegion newOne = null;
		
		if (!overrideRegionTrespassed && !disabled) {
			
			// look out for creation of new override region
			int situation = 0;
			try {
				if (looksLikeIdent(d, c.offset-1))
					situation |= Autopair.FOLLOWSIDENT;
			} catch (BadLocationException e) {}
			try {
				if (Character.isWhitespace(d.getChar(c.offset)))
					situation |= Autopair.PRECEDESWHITESPACE;
			} catch (BadLocationException e) {}
			
			for (Autopair autopair : AUTOPAIRS) {
				if (autopair.applies(c, d, situation)) {
					overrideRegions.add(0, newOne = new AutoInsertedRegion(c.offset+c.text.length(), autopair.end.length(), new MutableRegion(c.offset, c.text.length())));
					c.text += autopair.end;
					c.shiftsCaret = false;
					c.caretOffset = c.offset+autopair.end.length();
					break;
				}
			}
		}
		
		// inc offset of existing regions
		for (int i = newOne != null ? 1 : 0; i < overrideRegions.size(); i++) {
			AutoInsertedRegion r = overrideRegions.get(i);
			r.maybeIncOffset(c.offset, c.text.length());
			r.cause.maybeIncOffset(c.offset, c.text.length());
		}
	}

	protected boolean ignoreUserInputIfDuplicatingAutoPair(IDocument d, DocumentCommand c) {
		for (int i = overrideRegions.size()-1; i >= 0; i--) {
			MutableRegion r = overrideRegions.get(i);
			try {
				if (r.getOffset() == c.offset && c.text.length() == 1 && d.getChar(r.getOffset()) == c.text.charAt(0)) {
					c.text = ""; //$NON-NLS-1$
					c.shiftsCaret = false;
					c.caretOffset = c.offset+1;
					r.incOffset(1);
					r.incLength(-1);
					if (r.getLength() == 0) {
						overrideRegions.remove(i);
					}
					return true;
				}
			} catch (BadLocationException e) {
				e.printStackTrace();
				break;
			}
		}
		return false;
	}

	private void regionDeleted(int offset, int length, MutableRegion exclude, DocumentCommand command, IDocument document) {
		for (int i = overrideRegions.size()-1; i >= 0; i--) {
			AutoInsertedRegion r = overrideRegions.get(i);
			if (r == exclude)
				continue;
			// look if the text that caused the auto-insertion is being deleted
			if (!r.cause.maybeIncOffset(offset+length, -length)) {
				if (r.cause.getOffset() >= offset && r.cause.getOffset() < offset+length) {
					overrideRegions.remove(i);
					if (command != null && document != null) {
						try {
							command.text = document.get(command.offset+command.length, r.getOffset()-command.offset-command.length);
							command.length = r.getEnd() - command.offset;
							command.caretOffset = command.offset;
							command.shiftsCaret = false;
						} catch (BadLocationException e) {
							//e.printStackTrace();
						}
					}
					continue;
				}
			}
			// adjust offset of auto-inserted region itself
			if (!r.maybeIncOffset(offset+length, -length)) {
				if (r.getOffset() >= offset && r.getOffset() < offset+length) {
					overrideRegions.remove(i);
					continue;
				}
				if (r.getOffset() < offset && r.getOffset()+r.getLength() > offset)
					r.incLength(offset-r.getOffset()-r.getLength());
			}
		}
	}
	
	public void handleCursorPositionChanged(int cursorPos, IDocument d) {
		if (!overrideRegions.isEmpty()) {
			try {
				IRegion r = d.getLineInformationOfOffset(cursorPos);
				for (int i = overrideRegions.size()-1; i >= 0; i--) {
					MutableRegion or = overrideRegions.get(i);
					if (or.getOffset() < r.getOffset() || or.getOffset() > r.getOffset()+r.getLength())
						overrideRegions.remove(i);
				}
			} catch (BadLocationException e) {
				e.printStackTrace();
			}
		}
	}

	public void completionProposalApplied(ClonkCompletionProposal proposal) {
		AutoInsertedRegion newOne = null;
		if (proposal.getReplacementString().endsWith(")") && proposal.getCursorPosition() < proposal.getReplacementString().length()) { //$NON-NLS-1$
			overrideRegions.add(newOne = new AutoInsertedRegion(
				proposal.getReplacementOffset()+proposal.getReplacementString().length()-1, 1,
				new MutableRegion(proposal.getReplacementOffset()+proposal.getReplacementString().length()-2, proposal.getReplacementLength())
			));
		}
		if (proposal.getReplacementLength() > 0)
			regionDeleted(proposal.getReplacementOffset(), proposal.getReplacementLength(), newOne, null, null);
		for (MutableRegion r : overrideRegions) {
			if (r == newOne)
				continue;
			if (r.getOffset() >= proposal.getReplacementOffset())
				r.incOffset(proposal.getReplacementString().length());
		}
	}

	private boolean unbalanced(IDocument d, IRegion body) throws BadLocationException {
		int open, close;
		open = close = 0;
		for (int x = 0; x < body.getLength()-1 && body.getOffset()+x < d.getLength(); x++) {
			char c = d.getChar(body.getOffset()+x);
			switch (c) {
			case '{':
				open++;
				break;
			case '}':
				close++;
				break;
			}
		}
		return open > close;
	}

	@Override
	public void propertyChange(PropertyChangeEvent event) {
		if (event.getProperty().equals(ClonkPreferences.NO_AUTOBRACKETPAIRS))
			disabled = ClonkPreferences.getPreferenceToggle(ClonkPreferences.NO_AUTOBRACKETPAIRS, false);
	}
	
}
