package net.arctics.clonk.ui.editors.c4script

import net.arctics.clonk.DefinitionInfo
import net.arctics.clonk.TestBase;
import net.arctics.clonk.parser.c4script.inference.dabble.DabbleInferenceTest.Setup

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.swt.graphics.Point
import org.junit.Assert
import org.junit.Test

public class C4ScriptCompletionProcessorTest extends TestBase {
	@Test
	public void testProposalsBasedOnRevisiting() {
		def baseSource =
		"""
		local count;
		public func SetStackCount(int amount)
		{
			count = amount;
		}
		public func TakeObject()
		{
			if (count == 1)
			{
				Exit();
				return this;
			}
			else if (count > 1)
			{
				var take = CreateObject(GetID(), 0, 0, GetOwner());
				take->SetStackCount(1);
				return take;
			}
		}
		"""
		def derivedSourcePart1 =
		"""
		#include Base
		func Test()
		{
			return TakeObject()->"""
		def derivedSourcePart2 = """
		}
		"""
		def derivedSource = derivedSourcePart1 + derivedSourcePart2
		def setup = new Setup(
			new DefinitionInfo(name: "Base", source: baseSource),
			new DefinitionInfo(name: "Derived", source: derivedSource)
		)
		
		IDocument derivedDocument = documentMock(derivedSource)
		ITextViewer viewer = [
			getDocument: {derivedDocument},
			getSelectedRange: { new Point(derivedSourcePart1.length(), 0) }
		] as ITextViewer
		System.out.println(viewer.getSelectedRange().x)
		
		setup.parsers.each { it.run() }
		setup.index.refresh()
		setup.scripts.each { it.generateCaches() }
		setup.inference.run()
		
		def (base, derived) = setup.scripts
		
		C4ScriptCompletionProcessor processor = new C4ScriptCompletionProcessor(derived)
		processor.computeCompletionProposals(viewer, derivedSourcePart1.length())
	}
}