package net.arctics.clonk.ui.editors.ini;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ContentAssistEvent;
import org.eclipse.jface.text.contentassist.ICompletionListener;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;

import net.arctics.clonk.builder.ClonkProjectNature;
import net.arctics.clonk.c4group.FileExtension;
import net.arctics.clonk.c4script.Function;
import net.arctics.clonk.c4script.Script;
import net.arctics.clonk.c4script.Variable;
import net.arctics.clonk.c4script.Variable.Scope;
import net.arctics.clonk.index.Definition;
import net.arctics.clonk.index.ID;
import net.arctics.clonk.index.Index;
import net.arctics.clonk.index.ProjectIndex;
import net.arctics.clonk.ini.Boolean;
import net.arctics.clonk.ini.CategoriesValue;
import net.arctics.clonk.ini.DefinitionPack;
import net.arctics.clonk.ini.FunctionEntry;
import net.arctics.clonk.ini.IDArray;
import net.arctics.clonk.ini.IconSpec;
import net.arctics.clonk.ini.IniData.IniDataBase;
import net.arctics.clonk.ini.IniData.IniEntryDefinition;
import net.arctics.clonk.ini.IniData.IniSectionDefinition;
import net.arctics.clonk.ini.IniSection;
import net.arctics.clonk.ini.IniUnit;
import net.arctics.clonk.ini.IniUnitParser;
import net.arctics.clonk.ui.editors.ProposalsSite;
import net.arctics.clonk.ui.editors.StructureCompletionProcessor;
import net.arctics.clonk.ui.editors.c4script.ProposalCycle;
import net.arctics.clonk.util.Utilities;

/**
 * Completion processor for ini files. Proposes entries and values for those entries based on their type (functions from the related script for callbacks for example)
 * @author madeen
 *
 */
public class IniCompletionProcessor extends StructureCompletionProcessor<IniUnitEditingState> implements ICompletionListener {

	public IniCompletionProcessor(final IniUnitEditingState state) { super(state); }

	@Override
	public ICompletionProposal[] computeCompletionProposals(final ITextViewer viewer, final int offset) {
		super.computeCompletionProposals(viewer, offset);

		final IDocument doc = viewer.getDocument();
		String line;
		int lineStart;
		try {
			final IRegion lineRegion = doc.getLineInformationOfOffset(offset);
			line = doc.get(lineRegion.getOffset(), lineRegion.getLength());
			lineStart = lineRegion.getOffset();
		} catch (final BadLocationException e) {
			line = ""; //$NON-NLS-1$
			lineStart = offset;
		}

		Matcher m;
		String prefix;
		String entryName = null;
		boolean assignment = false;
		int wordOffset;
		if ((m = IniUnitEditingState.ASSIGN_PATTERN.matcher(line)).matches()) {
			entryName = m.group(1);
			prefix = m.group(2);
			assignment = true;
			wordOffset = lineStart + m.start(2);
		}
		else if ((m = IniUnitEditingState.NO_ASSIGN_PATTERN.matcher(line)).matches()) {
			prefix = m.group(1);
			wordOffset = lineStart + m.start(1);
		} else {
			return new ICompletionProposal[0];
		}
		prefix = prefix.toLowerCase();

		site = new ProposalsSite(state(), offset, wordOffset, doc, prefix, new LinkedList<ICompletionProposal>(), state().structure().index(), null, null, null, null, null);

		state().ensureIniUnitUpToDate();
		final IniSection section = state().structure().sectionAtOffset(offset);

		if (section != null) {
			if (assignment) {
				final IniDataBase itemData = section.definition().entryForKey(entryName);
				if (itemData instanceof IniEntryDefinition) {
					final IniEntryDefinition entryDef = (IniEntryDefinition) itemData;
					final Class<?> entryClass = entryDef.entryClass();
					if (entryClass == ID.class || entryClass == IconSpec.class) {
						proposalsForIndex(site);
					} else if (entryClass == FunctionEntry.class) {
						proposalsForFunctionEntry(site);
					} else if (entryClass == IDArray.class) {
						final int lastDelim = prefix.lastIndexOf(';');
						prefix = prefix.substring(lastDelim+1);
						wordOffset += lastDelim+1;
						proposalsForIndex(site);
					}
					else if (entryClass == Boolean.class) {
						proposalsForBooleanEntry(site);
					} else if (entryClass == DefinitionPack.class) {
						proposalsForDefinitionPackEntry(site);
					} else if (entryClass == CategoriesValue.class) {
						final int lastDelim = prefix.lastIndexOf('|');
						prefix = prefix.substring(lastDelim+1);
						wordOffset += lastDelim+1;
						proposalsForCategoriesValue(site, entryDef);
					}
					else if (entryDef.enumValues() != null) {
						entryDef.enumValues().keySet().forEach(k -> site.addProposal(
							new CompletionProposal(k, site.wordOffset, site.prefix.length(), k.length())
						));
					}
				}
			} else {
				if (section.definition() != null) {
					proposalsForSection(site, section);
				}
				if (section.parentSection() != null && section.parentSection().definition() != null) {
					// also propose new sections
					proposalsForIniDataEntries(site, section.parentSection().definition().entries().values());
				} else if (section.parentDeclaration() instanceof IniUnit) {
					proposalsForIniDataEntries(site, ((IniUnit)section.parentDeclaration()).configuration().sections().values());
				}
				final int indentation = new IniUnitParser(state().structure()).indentationAt(offset);
				if (indentation == section.indentation()+1) {
					proposalsForIniDataEntries(site, section.definition().entries().values());
				}
			}
		}

		final ICompletionProposal[] proposals = site.finish(ProposalCycle.ALL);
		guardedSort(proposals);
		return proposals;
	}

	private void proposalsForCategoriesValue(final ProposalsSite pl, final IniEntryDefinition entryDef) {
		if (pl.prefix != null) {
			for (final Variable v : state().structure().engine().variablesWithPrefix(entryDef.constantsPrefix())) {
				if (v.scope() == Scope.CONST) {
					proposalForVar(pl, state().structure(), v);
				}
			}
		}
	}

	private void proposalsForIndex(final ProposalsSite pl) {
		final Index index = ProjectIndex.fromResource(state().structure().file());
		if (index != null) {
			for (final Index i : index.relevantIndexes()) {
				proposalsForIndexedDefinitions(pl, i);
			}
		}
	}

	private void proposalsForDefinitionPackEntry(final ProposalsSite pl) {
		final ClonkProjectNature nature = ClonkProjectNature.get(state().structure().resource().getProject());
		final List<Index> indexes = nature.index().relevantIndexes();
		for (final Index index : indexes) {
			if (index instanceof ProjectIndex) {
				try {
					for (final IResource res : ((ProjectIndex)index).nature().getProject().members()) {
						if (res instanceof IContainer && nature.index().engine().extensionForFileName(res.getName()) == FileExtension.DefinitionGroup) {
							if (res.getName().toLowerCase().contains(pl.prefix)) {
								pl.addProposal(new CompletionProposal(res.getName(), pl.wordOffset, pl.prefix.length(), res.getName().length()));
							}
						}
					}
				} catch (final CoreException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void proposalsForIniDataEntries(final ProposalsSite pl, final Iterable<? extends IniDataBase> sectionData) {
		for (final IniDataBase sec : sectionData) {
			if (sec instanceof IniSectionDefinition && ((IniSectionDefinition) sec).sectionName().toLowerCase().contains(pl.prefix)) {
				final String secString = "["+((IniSectionDefinition) sec).sectionName()+"]"; //$NON-NLS-1$ //$NON-NLS-2$
				pl.addProposal(new CompletionProposal(secString, pl.wordOffset, pl.prefix.length(), secString.length(), null, null, null, "ugh")); //$NON-NLS-1$
			}
		}
	}

	private void proposalsForSection(final ProposalsSite pl, final IniSection section) {
		for (final IniDataBase entry : section.definition().entries().values()) {
			if (entry instanceof IniEntryDefinition) {
				final IniEntryDefinition e = (IniEntryDefinition) entry;
				if (!e.name().toLowerCase().contains(pl.prefix) || section.item(e.name()) != null) {
					continue;
				}
				pl.addProposal(new CompletionProposal(e.name(), pl.wordOffset, pl.prefix.length(), e.name().length(), null, e.name(), null, e.description()));
			}
			else if (entry instanceof IniSectionDefinition) {
				// FIXME
			}
		}
	}

	private void proposalsForFunctionEntry(final ProposalsSite pl) {
		final Definition obj = Definition.at(state().structure().file().getParent());
		if (obj != null) {
			for (final Script include : obj.conglomerate()) {
				final Script script = Utilities.as(include, Script.class);
				if (script == null) {
					continue;
				}
				for (final Function f : script.functions()) {
					proposalForFunc(pl, script, f);
				}
			}
		}
	}

	private void proposalsForBooleanEntry(final ProposalsSite pl) {
		final int[] choices = new int[] {0, 1};
		for (final int i : choices) {
			pl.addProposal(new CompletionProposal(String.valueOf(i), pl.wordOffset, pl.prefix.length(), String.valueOf(i).length()));
		}
	}

	@Override
	public IContextInformation[] computeContextInformation(final ITextViewer viewer, final int offset) { return null; }
	@Override
	public IContextInformationValidator getContextInformationValidator() { return null; }
	@Override
	public void assistSessionEnded(final ContentAssistEvent event) { state().unlockUnit(); }
	@Override
	public void selectionChanged(final ICompletionProposal proposal, final boolean smartToggle) {}

	@Override
	public void assistSessionStarted(final ContentAssistEvent event) {
		try {
			state().forgetUnitParsed();
			state().lockUnit();
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

}
