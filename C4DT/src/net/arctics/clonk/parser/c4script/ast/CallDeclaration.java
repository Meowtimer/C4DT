package net.arctics.clonk.parser.c4script.ast;

import static net.arctics.clonk.util.Utilities.as;
import static net.arctics.clonk.util.Utilities.isAnyOf;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import net.arctics.clonk.Core;
import net.arctics.clonk.index.CachedEngineDeclarations;
import net.arctics.clonk.index.Definition;
import net.arctics.clonk.index.Engine;
import net.arctics.clonk.index.Index;
import net.arctics.clonk.parser.Declaration;
import net.arctics.clonk.parser.EntityRegion;
import net.arctics.clonk.parser.IHasIncludes;
import net.arctics.clonk.parser.ParserErrorCode;
import net.arctics.clonk.parser.ParsingException;
import net.arctics.clonk.parser.c4script.C4ScriptParser;
import net.arctics.clonk.parser.c4script.ConstrainedProplist;
import net.arctics.clonk.parser.c4script.DeclarationObtainmentContext;
import net.arctics.clonk.parser.c4script.FindDeclarationInfo;
import net.arctics.clonk.parser.c4script.Function;
import net.arctics.clonk.parser.c4script.Function.FunctionScope;
import net.arctics.clonk.parser.c4script.FunctionType;
import net.arctics.clonk.parser.c4script.IHasConstraint;
import net.arctics.clonk.parser.c4script.IHasConstraint.ConstraintKind;
import net.arctics.clonk.parser.c4script.IIndexEntity;
import net.arctics.clonk.parser.c4script.IType;
import net.arctics.clonk.parser.c4script.Keywords;
import net.arctics.clonk.parser.c4script.Operator;
import net.arctics.clonk.parser.c4script.PrimitiveType;
import net.arctics.clonk.parser.c4script.Script;
import net.arctics.clonk.parser.c4script.SpecialScriptRules;
import net.arctics.clonk.parser.c4script.SpecialScriptRules.SpecialFuncRule;
import net.arctics.clonk.parser.c4script.SpecialScriptRules.SpecialRule;
import net.arctics.clonk.parser.c4script.TypeSet;
import net.arctics.clonk.parser.c4script.Variable;
import net.arctics.clonk.parser.c4script.ast.UnaryOp.Placement;
import net.arctics.clonk.parser.c4script.ast.evaluate.IEvaluationContext;
import net.arctics.clonk.util.ArrayUtil;

import org.eclipse.core.resources.IMarker;
import org.eclipse.jface.text.Region;

/**
 * An identifier followed by parenthesized parameters. The {@link Declaration} being referenced will more likely be a {@link Function} but may also be a {@link Variable}
 * in which case that variable will probably be typed as {@link FunctionType}/{@link PrimitiveType#FUNCTION}
 * @author madeen
 *
 */
public class CallDeclaration extends AccessDeclaration implements IFunctionCall {

	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;

	private final static class FunctionReturnTypeInfo extends TypeInfo {
		private Function function;

		public FunctionReturnTypeInfo(Function function) {
			super();
			this.function = function;
			if (function != null)
				type = function.returnType();
		}
		
		@Override
		public void storeType(IType type) {
			// don't store if function.getReturnType() already specifies a type (including any)
			// this is to prevent cases where for example the result of EffectVar in one instance is
			// used as int and then as something else which leads to an erroneous type incompatibility warning
			if (type == PrimitiveType.UNKNOWN)
				super.storeType(type);
		}
		
		@Override
		public boolean storesTypeInformationFor(ExprElm expr, C4ScriptParser parser) {
			if (expr instanceof CallDeclaration) {
				CallDeclaration callFunc = (CallDeclaration) expr;
				if (callFunc.declaration() == this.function)
					return true;
			}
			return false;
		}
		
		@Override
		public boolean refersToSameExpression(ITypeInfo other) {
			return other instanceof CallDeclaration.FunctionReturnTypeInfo && ((CallDeclaration.FunctionReturnTypeInfo)other).function == this.function;
		}
		
		@Override
		public String toString() {
			return "function " + function + " " + super.toString();
		}
		
		@Override
		public void apply(boolean soft, C4ScriptParser parser) {
			if (function == null)
				return;
			function = (Function) function.latestVersion();
			if (!soft && !function.isEngineDeclaration()) {
				function.forceType(type());
			}
		}
		
	}
	
	private final static class VarFunctionsTypeInfo extends TypeInfo {
		private final Function varFunction;
		private final long varIndex;

		private VarFunctionsTypeInfo(Function function, long val) {
			varFunction = function;
			varIndex = val;
		}

		@Override
		public boolean storesTypeInformationFor(ExprElm expr, C4ScriptParser parser) {
			if (expr instanceof CallDeclaration) {
				CallDeclaration callFunc = (CallDeclaration) expr;
				Object ev;
				return
					callFunc.declaration() == varFunction &&
					callFunc.params().length == 1 && // don't bother with more complex cases
					callFunc.params()[0].type(parser) == PrimitiveType.INT &&
					((ev = callFunc.params()[0].evaluateAtParseTime(parser.currentFunction())) != null) &&
					ev.equals(varIndex);
			} else if (expr instanceof AccessVar) {
				AccessVar accessVar = (AccessVar) expr;
				return
					accessVar.declaration() instanceof Variable &&
					parser.currentFunction().localVars() != null &&
					parser.currentFunction().localVars().indexOf(accessVar.declaration) == varIndex;
			}
			return false;
		}

		@Override
		public boolean refersToSameExpression(ITypeInfo other) {
			if (other.getClass() == CallDeclaration.VarFunctionsTypeInfo.class) {
				CallDeclaration.VarFunctionsTypeInfo otherInfo = (CallDeclaration.VarFunctionsTypeInfo) other;
				return otherInfo.varFunction == this.varFunction && otherInfo.varIndex == this.varIndex; 
			}
			else
				return false;
		}
		
		@Override
		public String toString() {
			return String.format("%s(%d)", varFunction.name(), varIndex); //$NON-NLS-1$
		}
		
	}

	private ExprElm[] params;
	private int parmsStart, parmsEnd;

	@Override
	protected void offsetExprRegion(int amount, boolean start, boolean end) {
		super.offsetExprRegion(amount, start, end);
		if (start) {
			parmsStart += amount;
			parmsEnd += amount;
		}
	}
	
	/**
	 * Set the region containing the parameters.
	 * @param start Start of the region
	 * @param end End of the region
	 */
	public void setParmsRegion(int start, int end) {
		parmsStart = start;
		parmsEnd   = end;
	}

	/**
	 * Return the start offset of the parameters region.
	 * @return The start offset
	 */
	@Override
	public int parmsStart() {
		return parmsStart;
	}

	/**
	 * Return the end offset of the parameters region.
	 * @return The end offset
	 */
	@Override
	public int parmsEnd() {
		return parmsEnd;
	}

	/**
	 * Create a CallFunc with a function name and parameter expressions.
	 * @param funcName The function name
	 * @param parms Parameter expressions
	 */
	public CallDeclaration(String funcName, ExprElm... parms) {
		super(funcName);
		params = parms;
		assignParentToSubElements();
	}
	
	/**
	 * Create a CallFunc that directly refers to a {@link Function} object. 
	 * @param function The {@link Function} the new CallFunc will refer to.
	 * @param parms Parameter expressions
	 */
	public CallDeclaration(Function function, ExprElm... parms) {
		this(function.name());
		this.declaration = function;
		assignParentToSubElements();
	}
	
	@Override
	public void doPrint(ExprWriter output, int depth) {
		super.doPrint(output, depth);
		printParmString(output, params, depth);
	}

	/**
	 * Print a parameter string.
	 * @param output Output to print to
	 * @param depth Indentation level of parameter expressions.
	 */
	public static void printParmString(ExprWriter output, ExprElm[] params, int depth) {
		output.append("("); //$NON-NLS-1$
		if (params != null) {
			for (int i = 0; i < params.length; i++) {
				if (params[i] != null)
					params[i].print(output, depth);
				if (i < params.length-1)
					output.append(", "); //$NON-NLS-1$
			}
		}
		output.append(")"); //$NON-NLS-1$
	}
	
	@Override
	public boolean isModifiable(C4ScriptParser context) {
		IType t = type(context);
		return t.canBeAssignedFrom(TypeSet.REFERENCE_OR_ANY_OR_UNKNOWN);
	}
	@Override
	public boolean hasSideEffects() {
		return true;
	}
	
	/**
	 * Return a {@link SpecialFuncRule} applying to {@link CallDeclaration}s with the same name as this one.
	 * @param context Context used to obtain the {@link Engine}, which supplies the pool of {@link SpecialRule}s (see {@link Engine#specialScriptRules()})
	 * @param role Role mask passed to {@link SpecialScriptRules#funcRuleFor(String, int)}
	 * @return The {@link SpecialFuncRule} applying to {@link CallDeclaration}s such as this one, or null.
	 */
	public SpecialFuncRule specialRuleFromContext(DeclarationObtainmentContext context, int role) {
		Engine engine = context.containingScript().engine();
		if (engine != null && engine.specialScriptRules() != null) {
			return engine.specialScriptRules().funcRuleFor(declarationName, role);
		} else {
			return null;
		}
	}
	
	@Override
	protected IType callerType(DeclarationObtainmentContext context) {
		if (predecessorInSequence() != null)
			return predecessorInSequence().type(context);
		else
			return super.callerType(context);
	}
	
	@Override
	protected IType obtainType(DeclarationObtainmentContext context) {
		Declaration d = declarationFromContext(context);
		
		// look for gathered type information
		IType stored = context.queryTypeOfExpression(this, null);
		if (stored != null)
			return stored;
		
		// calling this() as function -> return object type belonging to script
		if (params.length == 0 && (d == cachedFuncs(context).This || d == Variable.THIS)) {
			Definition obj = context.containerAsDefinition();
			if (obj != null)
				return obj;
		}

		// Some special rule applies and the return type is set accordingly
		SpecialFuncRule rule = specialRuleFromContext(context, SpecialScriptRules.RETURNTYPE_MODIFIER);
		if (rule != null) {
			IType returnType = rule.returnType(context, this);
			if (returnType != null) {
				return returnType;
			}
		}
		
		if (d instanceof Function)
			return ((Function)d).returnType();
		else if (d instanceof Variable)
			return ((Variable)d).type();

		return super.obtainType(context);
	}
	@Override
	public boolean isValidInSequence(ExprElm elm, C4ScriptParser context) {
		return super.isValidInSequence(elm, context) || elm instanceof MemberOperator;	
	}
	
	private transient boolean multiplePotentialDeclarations;
	
	@Override
	public Declaration obtainDeclaration(DeclarationObtainmentContext context) {
		super.obtainDeclaration(context);
		Set<IIndexEntity> decs = new HashSet<IIndexEntity>();
		_obtainDeclaration(decs, context);
		for (IIndexEntity e : decs)
			if (e instanceof Declaration)
				return (Declaration)e;
		return null;
	}

	protected void _obtainDeclaration(Set<IIndexEntity> list, DeclarationObtainmentContext context) {
		if (declarationName.equals(Keywords.Return))
			return;
		if (declarationName.equals(Keywords.Inherited) || declarationName.equals(Keywords.SafeInherited)) {
			Function activeFunc = context.currentFunction();
			if (activeFunc != null) {
				Function inher = activeFunc.inheritedFunction();
				if (inher != null)
					list.add(inher);
				return;
			}
		}
		ExprElm p = predecessorInSequence();
		findFunctionUsingPredecessor(p, declarationName, context, list);
		multiplePotentialDeclarations = list.size() > 1;
	}

	/**
	 * Find a {@link Function} for some hypothetical {@link CallDeclaration}, using contextual information such as the {@link ExprElm#type(DeclarationObtainmentContext)} of the {@link ExprElm} preceding this {@link CallDeclaration} in the {@link Sequence}.
	 * @param pred The predecessor of the hypothetical {@link CallDeclaration} ({@link ExprElm#predecessorInSequence()})
	 * @param functionName Name of the function to look for. Would correspond to the hypothetical {@link CallDeclaration}'s {@link #declarationName()}
	 * @param context Context to use for searching
	 * @param listToAddPotentialDeclarationsTo When supplying a non-null value to this parameter, potential declarations will be added to the collection. Such potential declarations would be obtained by querying the {@link Index}'s {@link Index#declarationMap()}.
	 * @return The {@link Function} that is very likely to be the one actually intended to be referenced by the hypothetical {@link CallDeclaration}.
	 */
	public static Declaration findFunctionUsingPredecessor(ExprElm pred, String functionName, DeclarationObtainmentContext context, Set<IIndexEntity> listToAddPotentialDeclarationsTo) {
		IType lookIn = pred == null ? context.containingScript() : pred.type(context);
		if (lookIn != null) for (IType ty : lookIn) {
			if (!(ty instanceof IHasConstraint))
				continue;
			Script script = as(((IHasConstraint)ty).constraint(), Script.class);
			if (script == null)
				continue;
			FindDeclarationInfo info = new FindDeclarationInfo(context.containingScript().index());
			info.searchOrigin = context.containingScript();
			info.contextFunction = context.currentFunction();
			info.findGlobalVariables = pred == null;
			Declaration dec = script.findDeclaration(functionName, info);
			// parse function before this one
			if (dec instanceof Function && context.currentFunction() != null) {
				try {
					context.parseCodeOfFunction((Function) dec, true);
				} catch (ParsingException e) {
					e.printStackTrace();
				}
			}
			if (dec != null) {
				if (listToAddPotentialDeclarationsTo == null)
					return dec;
				else
					listToAddPotentialDeclarationsTo.add(dec);
			}
		}
		if (pred != null) {
			// find global function
			Declaration declaration;
			try {
				declaration = context.containingScript().index().findGlobal(Declaration.class, functionName);
			} catch (Exception e) {
				e.printStackTrace();
				if (context == null)
					System.out.println("No context");
				if (context.containingScript() == null)
					System.out.println("No container");
				if (context.containingScript().index() == null)
					System.out.println("No index");
				return null;
			}
			// find engine function
			if (declaration == null)
				declaration = context.containingScript().index().engine().findFunction(functionName);

			List<Declaration> allFromLocalIndex = context.containingScript().index().declarationMap().get(functionName);
			Declaration decl = context.containingScript().engine().findLocalFunction(functionName, false);
			int numCandidates = 0;
			if (allFromLocalIndex != null)
				numCandidates += allFromLocalIndex.size();
			if (decl != null)
				numCandidates++;
			
			// only return found global function if it's the only choice 
			if (declaration != null && numCandidates == 1) {
				if (listToAddPotentialDeclarationsTo == null)
					return declaration;
				else
					listToAddPotentialDeclarationsTo.add(declaration);
			}
		}
		if ((pred == null || !(pred instanceof MemberOperator) || !((MemberOperator)pred).hasTilde()) && (lookIn == PrimitiveType.ANY || lookIn == PrimitiveType.UNKNOWN) && listToAddPotentialDeclarationsTo != null) {
			List<IType> typesWithThatMember = new LinkedList<IType>();
			for (Declaration d : ArrayUtil.filteredIterable(listToAddPotentialDeclarationsTo, Declaration.class))
				if (!d.isGlobal() && d instanceof Function && d.parentDeclaration() instanceof IHasIncludes)
					typesWithThatMember.add(new ConstrainedProplist((IHasIncludes)d.parentDeclaration(), ConstraintKind.Includes));
			if (typesWithThatMember.size() > 0) {
				IType ty = TypeSet.create(typesWithThatMember);
				ty.setTypeDescription(String.format(Messages.AccessDeclaration_TypesSporting, functionName));
				if (context instanceof C4ScriptParser)
					pred.expectedToBeOfType(ty, (C4ScriptParser)context, TypeExpectancyMode.Force);
			}
		}
		if (listToAddPotentialDeclarationsTo != null && listToAddPotentialDeclarationsTo.size() > 0)
			return ArrayUtil.filteredIterable(listToAddPotentialDeclarationsTo, Declaration.class).iterator().next();
		else
			return null;
	}
	private boolean unknownFunctionShouldBeError(C4ScriptParser parser) {
		ExprElm pred = predecessorInSequence();
		// standalone function? always bark!
		if (pred == null)
			return true;
		// not typed? weird
		IType predType = pred.type(parser);
		if (predType == null)
			return false;
		// called via ~? ok
		if (pred instanceof MemberOperator)
			if (((MemberOperator)pred).hasTilde())
				return false;
		// wat
		boolean anythingNonPrimitive = false;
		// allow this->Unknown()
		if (predType instanceof IHasConstraint && ((IHasConstraint)predType).constraintKind() == ConstraintKind.CallerType)
			return false;
		for (IType t : predType) {
			if (t instanceof PrimitiveType)
				continue;
			if (!(t instanceof IHasConstraint))
				return false;
			else {
				IHasConstraint hasConstraint = (IHasConstraint) t;
				anythingNonPrimitive = true;
				// something resolved to something less specific than a ScriptBase? drop
				if (!(hasConstraint.resolve(parser, callerType(parser)) instanceof Script))
					return false;
			}
		}
		return anythingNonPrimitive;
	}
	@Override
	public void reportErrors(final C4ScriptParser context) throws ParsingException {
		super.reportErrors(context);
		
		// notify parser about unnamed parameter usage
		if (declaration == cachedFuncs(context).Par) {
			if (params.length > 0) {
				context.unnamedParamaterUsed(params[0]);
			}
			else
				context.unnamedParamaterUsed(LongLiteral.ZERO);
		}
		// return as function
		else if (declarationName.equals(Keywords.Return)) {
			if (context.strictLevel() >= 2)
				context.errorWithCode(ParserErrorCode.ReturnAsFunction, this, C4ScriptParser.NO_THROW);
			else
				context.warningWithCode(ParserErrorCode.ReturnAsFunction, this);
		}
		else {
			
			// inherited/_inherited not allowed in non-strict mode
			if (context.strictLevel() <= 0) {
				if (declarationName.equals(Keywords.Inherited) || declarationName.equals(Keywords.SafeInherited)) {
					context.errorWithCode(ParserErrorCode.InheritedDisabledInStrict0, this);
				}
			}
			
			if (multiplePotentialDeclarations)
				return; // pfft, no checking
			
			// variable as function
			if (declaration instanceof Variable) {
				((Variable)declaration).setUsed(true);
				IType type = this.obtainType(context);
				// no warning when in #strict mode
				if (context.strictLevel() >= 2) {
					if (declaration != cachedFuncs(context).This && declaration != Variable.THIS && !PrimitiveType.FUNCTION.canBeAssignedFrom(type))
						context.warningWithCode(ParserErrorCode.VariableCalled, this, declaration.name(), type.typeName(false));
				}
			}
			else if (declaration instanceof Function) {
				Function f = (Function)declaration;
				if (f.visibility() == FunctionScope.GLOBAL || predecessorInSequence() != null)
					context.containingScript().addUsedScript(f.script());
				boolean specialCaseHandled = false;
				
				SpecialFuncRule rule = this.specialRuleFromContext(context, SpecialScriptRules.ARGUMENT_VALIDATOR);
				if (rule != null) {
					specialCaseHandled = rule.validateArguments(this, params, context);
				}
				
				// not a special case... check regular parameter types
				if (!specialCaseHandled) {
					int givenParam = 0;
					for (Variable parm : f.parameters()) {
						if (givenParam >= params.length)
							break;
						ExprElm given = params[givenParam++];
						if (given == null)
							continue;
						if (!given.validForType(parm.type(), context))
							context.warningWithCode(ParserErrorCode.IncompatibleTypes, given, parm.type().typeName(false), given.type(context).typeName(false));
						else
							given.expectedToBeOfType(parm.type(), context);
					}
				}
				
				// warn about too many parameters
				// try again, but only for engine functions
				if (f.isEngineDeclaration() && !declarationName.equals(Keywords.SafeInherited) && f.tooManyParameters(actualParmsNum())) {
					context.addLatentMarker(ParserErrorCode.TooManyParameters, this, IMarker.SEVERITY_WARNING, f, f.numParameters(), actualParmsNum(), f.name());
				}
				
			}
			else if (declaration == null) {
				if (unknownFunctionShouldBeError(context)) {
					if (declarationName.equals(Keywords.Inherited)) {
						Function activeFunc = context.currentFunction();
						if (activeFunc != null)
							context.errorWithCode(ParserErrorCode.NoInheritedFunction, start(), start()+declarationName.length(), C4ScriptParser.NO_THROW, context.currentFunction().name(), true);
						else
							context.errorWithCode(ParserErrorCode.NotAllowedHere, start(), start()+declarationName.length(), C4ScriptParser.NO_THROW, declarationName);
					}
					// _inherited yields no warning or error
					else if (!declarationName.equals(Keywords.SafeInherited))
						context.errorWithCode(ParserErrorCode.UndeclaredIdentifier, start(), start()+declarationName.length(), C4ScriptParser.NO_THROW, declarationName, true);
					}
				}
		}
	}
	public int actualParmsNum() {
		int result = params.length;
		while (result > 0 && params[result-1] instanceof Ellipsis)
			result--;
		return result;
	}
	@Override
	public ExprElm[] subElements() {
		return params;
	}
	@Override
	public void setSubElements(ExprElm[] elms) {
		params = elms;
	}
	protected BinaryOp applyOperatorTo(C4ScriptParser parser, ExprElm[] parms, Operator operator) throws CloneNotSupportedException {
		BinaryOp op = new BinaryOp(operator);
		BinaryOp result = op;
		for (int i = 0; i < parms.length; i++) {
			ExprElm one = parms[i].optimize(parser);
			ExprElm two = i+1 < parms.length ? parms[i+1] : null;
			if (op.leftSide() == null)
				op.setLeftSide(one);
			else if (two == null) {
				op.setRightSide(one);
			}
			else {
				BinaryOp nu = new BinaryOp(operator);
				op.setRightSide(nu);
				nu.setLeftSide(one);
				op = nu;
			}
		}
		return result;
	}
	@Override
	public ExprElm optimize(C4ScriptParser parser) throws CloneNotSupportedException {

		// And(ugh, blugh) -> ugh && blugh
		Operator replOperator = Operator.oldStyleFunctionReplacement(declarationName);
		if (replOperator != null && params.length == 1) {
			// LessThan(x) -> x < 0
			if (replOperator.numArgs() == 2)
				return new BinaryOp(replOperator, params[0].optimize(parser), LongLiteral.ZERO);
			ExprElm n = params[0].optimize(parser);
			if (n instanceof BinaryOp)
				n = new Parenthesized(n);
			return new UnaryOp(replOperator, replOperator.isPostfix() ? UnaryOp.Placement.Postfix : UnaryOp.Placement.Prefix, n);
		}
		if (replOperator != null && params.length >= 2) {
			return applyOperatorTo(parser, params, replOperator);
		}

		// ObjectCall(ugh, "UghUgh", 5) -> ugh->UghUgh(5)
		if (params.length >= 2 && declaration == cachedFuncs(parser).ObjectCall && params[1] instanceof StringLiteral && (Conf.alwaysConvertObjectCalls || !this.containedInLoopHeaderOrNotStandaloneExpression()) && !params[0].hasSideEffects()) {
			ExprElm[] parmsWithoutObject = new ExprElm[params.length-2];
			for (int i = 0; i < parmsWithoutObject.length; i++)
				parmsWithoutObject[i] = params[i+2].optimize(parser);
			String lit = ((StringLiteral)params[1]).stringValue();
			if (lit.length() > 0 && lit.charAt(0) != '~') {
				return Conf.alwaysConvertObjectCalls && this.containedInLoopHeaderOrNotStandaloneExpression()
					? new Sequence(new ExprElm[] {
							params[0].optimize(parser),
							new MemberOperator(false, true, null, 0),
							new CallDeclaration(((StringLiteral)params[1]).stringValue(), parmsWithoutObject)}
					)
					: new IfStatement(params[0].optimize(parser),
							new SimpleStatement(new Sequence(new ExprElm[] {
									params[0].optimize(parser),
									new MemberOperator(false, true, null, 0),
									new CallDeclaration(((StringLiteral)params[1]).stringValue(), parmsWithoutObject)}
							)),
							null
					);
			}
		}

		// OCF_Awesome() -> OCF_Awesome
		if (params.length == 0 && declaration instanceof Variable) {
			if (!parser.containingScript().engine().settings().proplistsSupported && predecessorInSequence() != null)
				return new CallDeclaration("LocalN", new StringLiteral(declarationName));
			else
				return new AccessVar(declarationName);
		}

		// also check for not-nullness since in OC Var/Par are gone and declaration == ...Par returns true -.-
		
		// Par(5) -> nameOfParm6
		if (params.length <= 1 && declaration != null && declaration == cachedFuncs(parser).Par && (params.length == 0 || params[0] instanceof LongLiteral)) {
			LongLiteral number = params.length > 0 ? (LongLiteral) params[0] : LongLiteral.ZERO;
			Function activeFunc = parser.currentFunction();
			if (activeFunc != null) {
				if (number.intValue() >= 0 && number.intValue() < activeFunc.numParameters() && activeFunc.parameter(number.intValue()).isActualParm())
					return new AccessVar(parser.currentFunction().parameter(number.intValue()).name());
			}
		}
		
		// SetVar(5, "ugh") -> Var(5) = "ugh"
		if (params.length == 2 && declaration != null && (declaration == cachedFuncs(parser).SetVar || declaration == cachedFuncs(parser).SetLocal || declaration == cachedFuncs(parser).AssignVar)) {
			return new BinaryOp(Operator.Assign, new CallDeclaration(declarationName.substring(declarationName.equals("AssignVar") ? "Assign".length() : "Set".length()), params[0].optimize(parser)), params[1].optimize(parser)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}

		// DecVar(0) -> Var(0)--
		if (params.length <= 1 && declaration != null && (declaration == cachedFuncs(parser).DecVar || declaration == cachedFuncs(parser).IncVar)) {
			return new UnaryOp(declaration == cachedFuncs(parser).DecVar ? Operator.Decrement : Operator.Increment, Placement.Prefix,
					new CallDeclaration(cachedFuncs(parser).Var.name(), new ExprElm[] {
						params.length == 1 ? params[0].optimize(parser) : LongLiteral.ZERO
					})
			);
		}

		// Call("Func", 5, 5) -> Func(5, 5)
		if (params.length >= 1 && declaration != null && declaration == cachedFuncs(parser).Call && params[0] instanceof StringLiteral) {
			String lit = ((StringLiteral)params[0]).stringValue();
			if (lit.length() > 0 && lit.charAt(0) != '~') {
				ExprElm[] parmsWithoutName = new ExprElm[params.length-1];
				for (int i = 0; i < parmsWithoutName.length; i++)
					parmsWithoutName[i] = params[i+1].optimize(parser);
				return new CallDeclaration(((StringLiteral)params[0]).stringValue(), parmsWithoutName);
			}
		}

		return super.optimize(parser);
	}

	private boolean containedInLoopHeaderOrNotStandaloneExpression() {
		SimpleStatement simpleStatement = null;
		for (ExprElm p = parent(); p != null; p = p.parent()) {
			if (p instanceof Block)
				break;
			if (p instanceof ILoop) {
				if (simpleStatement != null && simpleStatement == ((ILoop)p).body())
					return false;
				return true;
			}
			if (!(p instanceof SimpleStatement))
				return true;
			else
				simpleStatement = (SimpleStatement) p;
		} 
		return false;
	}

	@Override
	public EntityRegion declarationAt(int offset, C4ScriptParser parser) {
		Set<IIndexEntity> list = new HashSet<IIndexEntity>();
		_obtainDeclaration(list, parser);
		return new EntityRegion(list, new Region(start(), declarationName.length()));
	}
	public ExprElm soleParm() {
		if (params.length == 1)
			return params[0];
		return new Tuple(params);
	}
	@Override
	public ControlFlow controlFlow() {
		return declarationName.equals(Keywords.Return) ? ControlFlow.Return : super.controlFlow();
	}
	@Override
	public ExprElm[] params() {
		return params;
	}
	@Override
	public int indexOfParm(ExprElm parm) {
		for (int i = 0; i < params.length; i++)
			if (params[i] == parm)
				return i;
		return -1;
	}
	public Variable parmDefinitionForParmExpression(ExprElm parm) {
		if (declaration instanceof Function) {
			Function f = (Function) declaration;
			int i = indexOfParm(parm);
			return i >= 0 && i < f.numParameters() ? f.parameter(i) : null;
		} else
			return null;
	}
	@Override
	public ITypeInfo createStoredTypeInformation(C4ScriptParser parser) {
		Declaration d = declaration();
		CachedEngineDeclarations cache = cachedFuncs(parser);
		if (isAnyOf(d, cache.Var, cache.Local, cache.Par)) {
			Object ev;
			if (params().length == 1 && (ev = params()[0].evaluateAtParseTime(parser.currentFunction())) != null) {
				if (ev instanceof Number) {
					// Var() with a sane constant number
					return new VarFunctionsTypeInfo((Function) d, ((Number)ev).intValue());
				}
			}
		}
		else if (d instanceof Function) {
			Function f = (Function) d;
			if (f.typeIsInvariant())
				return null;
			IType retType = f.returnType();
			if (retType == null || !retType.subsetOfAny(PrimitiveType.ANY, PrimitiveType.REFERENCE))
				return new FunctionReturnTypeInfo((Function)d);
			if (d.isEngineDeclaration())
				return null;
		}
		else if (d != null)
			return new GenericStoredTypeInfo(this, parser);
		return super.createStoredTypeInformation(parser);
	}
	
	@Override
	public Object evaluate(IEvaluationContext context) {
	    if (declaration instanceof Function) {
	    	Object[] args = ArrayUtil.map(params(), Object.class, Conf.EVALUATE_EXPR);
	    	return ((Function)declaration).invoke(args);
	    }
	    else
	    	return null;
	}
	
	@Override
	public Class<? extends Declaration> declarationClass() {
		return Function.class;
	}

	@Override
	public Function function(DeclarationObtainmentContext context) {
		if (declaration instanceof Variable) {
			for (IType type : ((Variable)declaration).type()) {
				if (type instanceof FunctionType)
					return ((FunctionType)type).prototype();
			}
		}
		return as(declaration(), Function.class);
	}
}