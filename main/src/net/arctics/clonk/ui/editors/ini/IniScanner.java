package net.arctics.clonk.ui.editors.ini;

import net.arctics.clonk.c4script.Variable.Scope;
import net.arctics.clonk.index.Engine;
import net.arctics.clonk.ui.editors.ColorManager;
import net.arctics.clonk.ui.editors.CombinedWordRule;
import net.arctics.clonk.ui.editors.StructureTextScanner;
import net.arctics.clonk.ui.editors.WhitespaceDetector;
import net.arctics.clonk.ui.editors.WordScanner;

import org.eclipse.jface.text.rules.EndOfLineRule;
import org.eclipse.jface.text.rules.ICharacterScanner;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.rules.WhitespaceRule;

public class IniScanner extends StructureTextScanner {
	private static final class OperatorRule implements IRule {
		private final char[] OPERATORS = { '=', '[', ']', ',', '|', ';' };
		private final IToken token;
		public OperatorRule(final IToken token) { this.token = token; }
		public boolean isOperator(final char character) {
			for (int index = 0; index < OPERATORS.length; index++)
				if (OPERATORS[index] == character)
					return true;
			return false;
		}
		@Override
		public IToken evaluate(final ICharacterScanner scanner) {
			int character= scanner.read();
			if (isOperator((char) character)) {
				do
					character = scanner.read();
				while (isOperator((char) character));
				scanner.unread();
				return token;
			} else {
				scanner.unread();
				return Token.UNDEFINED;
			}
		}
	}
	public IniScanner(final ColorManager manager, final Engine engine) { super(manager, engine, "DEFAULT"); }
	@Override
	protected void commitRules(final ColorManager manager, final Engine engine) {
		final IToken defaultToken = createToken(manager, "DEFAULT"); //$NON-NLS-1$
		final IToken operator = createToken(manager, "OPERATOR"); //$NON-NLS-1$
		final IToken section = createToken(manager, "KEYWORD"); //$NON-NLS-1$
		final IToken number = createToken(manager, "NUMBER"); //$NON-NLS-1$
		final IToken constant = createToken(manager, "ENGINE_FUNCTION"); //$NON-NLS-1$
		final IToken comment = createToken(manager, "COMMENT"); //$NON-NLS-1$
		final WordScanner wordDetector = new WordScanner();
		final CombinedWordRule combinedWordRule = new CombinedWordRule(wordDetector, defaultToken);
		final CombinedWordRule.WordMatcher wordRule = new CombinedWordRule.WordMatcher();
		if (engine != null)
			engine.variables().stream()
				.filter(var -> var.scope() == Scope.CONST)
				.forEach(var -> wordRule.addWord(var.name(), constant));
		combinedWordRule.addWordMatcher(wordRule);

		setRules(new IRule[] {
			new SingleLineRule("[", "]", section, '\\'), //$NON-NLS-1$ //$NON-NLS-2$
			new EndOfLineRule("#", comment), //$NON-NLS-1$
			new EndOfLineRule("//", comment), //$NON-NLS-1$
			new OperatorRule(operator),
			new WhitespaceRule(new WhitespaceDetector()),
			combinedWordRule,
			new NumberRule(number)
		});
	}
}
