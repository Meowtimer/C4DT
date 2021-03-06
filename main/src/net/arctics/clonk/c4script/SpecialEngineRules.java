package net.arctics.clonk.c4script;

import static net.arctics.clonk.util.ArrayUtil.iterable;
import static net.arctics.clonk.util.Utilities.as;
import static net.arctics.clonk.util.Utilities.defaulting;
import static net.arctics.clonk.util.Utilities.eq;

import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.Region;

import net.arctics.clonk.Core;
import net.arctics.clonk.ProblemException;
import net.arctics.clonk.ast.ASTNode;
import net.arctics.clonk.ast.ASTNodeMatcher;
import net.arctics.clonk.ast.ASTNodePrinter;
import net.arctics.clonk.ast.Declaration;
import net.arctics.clonk.ast.EntityRegion;
import net.arctics.clonk.ast.ExpressionLocator;
import net.arctics.clonk.ast.Structure;
import net.arctics.clonk.c4group.FileExtension;
import net.arctics.clonk.c4script.Directive.DirectiveType;
import net.arctics.clonk.c4script.ast.CallDeclaration;
import net.arctics.clonk.c4script.ast.IDLiteral;
import net.arctics.clonk.c4script.ast.StringLiteral;
import net.arctics.clonk.c4script.typing.ArrayType;
import net.arctics.clonk.c4script.typing.IType;
import net.arctics.clonk.c4script.typing.PrimitiveType;
import net.arctics.clonk.c4script.typing.TypeChoice;
import net.arctics.clonk.c4script.typing.TypeUtil;
import net.arctics.clonk.c4script.typing.TypeVariable;
import net.arctics.clonk.c4script.typing.TypingJudgementMode;
import net.arctics.clonk.index.Definition;
import net.arctics.clonk.index.Engine;
import net.arctics.clonk.index.ID;
import net.arctics.clonk.index.IDeserializationResolvable;
import net.arctics.clonk.index.IIndexEntity;
import net.arctics.clonk.index.IReplacedWhenSaved;
import net.arctics.clonk.index.Index;
import net.arctics.clonk.index.IndexEntity;
import net.arctics.clonk.index.MetaDefinition;
import net.arctics.clonk.index.ProjectIndex;
import net.arctics.clonk.index.ProjectResource;
import net.arctics.clonk.index.Scenario;
import net.arctics.clonk.ini.Action;
import net.arctics.clonk.ini.CategoriesValue;
import net.arctics.clonk.ini.DefinitionPack;
import net.arctics.clonk.ini.FunctionEntry;
import net.arctics.clonk.ini.IDArray;
import net.arctics.clonk.ini.IconSpec;
import net.arctics.clonk.ini.IniData.IniConfiguration;
import net.arctics.clonk.ini.IniData.IniEntryDefinition;
import net.arctics.clonk.ini.IniData.IniSectionDefinition;
import net.arctics.clonk.ini.IniEntry;
import net.arctics.clonk.ini.IniSection;
import net.arctics.clonk.ini.IniUnit;
import net.arctics.clonk.ini.IniUnitWithNamedSections;
import net.arctics.clonk.ini.IntegerArray;
import net.arctics.clonk.ini.MaterialArray;
import net.arctics.clonk.ini.ParticleUnit;
import net.arctics.clonk.ini.ScenarioUnit;
import net.arctics.clonk.ini.SignedInteger;
import net.arctics.clonk.parser.BufferedScanner;
import net.arctics.clonk.parser.IMarkerListener.Decision;
import net.arctics.clonk.ui.editors.ProposalsSite;
import net.arctics.clonk.ui.editors.c4script.ScriptCompletionProcessor;
import net.arctics.clonk.util.ArrayUtil;
import net.arctics.clonk.util.Sink;
import net.arctics.clonk.util.StringUtil;
import net.arctics.clonk.util.Utilities;

/**
 * This class contains some special rules that are applied to certain objects during parsing
 * (like CallFuncians and other species dwelling in ASTs)
 * @author madeen
 *
 */
public abstract class SpecialEngineRules {

	protected Engine engine;

	private final ASTNode RETURN_TRUE;

	public SpecialEngineRules(final Engine engine) {
		super();
		this.engine = engine;
		RETURN_TRUE = ASTNodeMatcher.prepareForMatching("$:FunctionBody$(return $:True$|$:NumberLiteral,/1/$;)", engine);
	}

	/**
	 * Role for SpecialFuncRule: Validate arguments of a function in a special way.
	 */
	public static final int ARGUMENT_VALIDATOR = 1;

	/**
	 * Role for SpecialFuncRule: Modify the return type of a function call.
	 */
	public static final int RETURNTYPE_MODIFIER = 2;

	/**
	 * Role for SpecialFuncRule: Locate declarations inside a parameter of a function call for link-clicking.
	 */
	public static final int DECLARATION_LOCATOR = 4;

	/**
	 * Role for SpecialFuncRule: Assign default types to functions
	 */
	public static final int DEFAULT_PARMTYPE_ASSIGNER = 8;

	/**
	 * Role for SpecialFuncRule: Gets notified when a function is about to be parsed.
	 */
	public static final int FUNCTION_EVENT_LISTENER = 16;

	/**
	 * Role for SpecialFuncRule: Gets queried for additional proposals for parameters of certain functions.
	 * @author madeen
	 *
	 */
	public static final int FUNCTION_PARM_PROPOSALS_CONTRIBUTOR = 32;

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public @interface SignifiesRole {
		public int role();
	}

	/**
	 * Annotation for specifying what functions a special rule is applied to.
	 * @author madeen
	 *
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface AppliedTo {
		/**
		 * Function names.
		 * @return The function names
		 */
		public String[] functions();
		/**
		 * Only applied to the specified functions in a matching role processor.
		 * @return The role mask
		 */
		public int role() default Integer.MAX_VALUE;
	}

	/**
	 * For multiple AppliedTo annotations...
	 * @author madeen
	 *
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface AppliedTos {
		public AppliedTo[] list();
	}

	/**
	 * Base class for special rules.
	 * @author madeen
	 */
	public static abstract class SpecialRule implements IReplacedWhenSaved {

		public static class Ticket implements IDeserializationResolvable, Serializable {
			private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
			private final String name;
			public Ticket(final SpecialRule obj) {
				this.name = obj.name;
			}
			@Override
			public Object resolve(final Index index, final IndexEntity deserializee) {
				return index.engine().specialRules().rule(name);
			}
		}

		@Override
		public Object saveReplacement(final Index context) { return new Ticket(this); }

		public String name;

		/**
		 * Return the list of functions an instance of SpecialRule should be applied to.
		 * Obtained from an @AppliedTo annotation attached to the passed instance field refering to the actual rule.
		 * @param instanceReference The field with the attached @AppliedTo annotation
		 * @return Returns empty array if no AppliedTo annotation exists for the field, annotation.functions() if one exists.
		 */
		public static String[] getFunctionsAppliedTo(final Field instanceReference, final int role) {
			AppliedTo[] annots;
			final AppliedTo annot = instanceReference.getAnnotation(AppliedTo.class);
			if (annot != null) {
				annots = new AppliedTo[] {annot};
			} else {
				final AppliedTos list = instanceReference.getAnnotation(AppliedTos.class);
				if (list != null) {
					annots = list.list();
				} else {
					return null;
				}
			}
			final Set<String> funs = new HashSet<String>(5);
			for (final AppliedTo a : annots) {
				if ((role & a.role()) != 0) {
					for (final String f : a.functions()) {
						funs.add(f);
					}
				}
			}
			return funs.toArray(new String[funs.size()]);
		}
		/**
		 * Construct role mask of this rule using reflection. A rule assumes a role if its class overrides a method with a SignifiesRole annotation attached to it.
		 * So for example, if a class overrides validateArguments and validateArguments has the annotation @SignifiesRole(role=ARGUMENT_VALIDATOR), the mask will contain
		 * ARGUMENT_VALIDATOR.
		 * @return The role mask.
		 */
		public final int computeRoleMask() {
			int result = 0;
			for (final Method m : getClass().getMethods()) {
				try {
					Class<?> cls = m.getDeclaringClass().getSuperclass();
					while (cls != null) {
						final Method baseMethod = cls.getMethod(m.getName(), m.getParameterTypes());
						if (baseMethod == null) {
							break;
						}
						final SignifiesRole annot = baseMethod.getAnnotation(SignifiesRole.class);
						if (annot != null) {
							result |= annot.role();
							break;
						}
						cls = baseMethod.getDeclaringClass().getSuperclass();
					}
				} catch (final Exception e) {
					continue;
				}
			}
			return result;
		}
	}

	/**
	 * Base class for special rules applied to functions.<br>
	 * This class allows
	 * <ul>
	 * <li>validating arguments in a special way</li>
	 * <li>modifying the return type of a function call</li>
	 * <li>making regions of AST expressions link-clickable</li>
	 * <li>Handling creation of new function objects</li>
	 * </ul>
	 * @author madeen
	 *
	 */
	public static abstract class SpecialFuncRule extends SpecialRule {

		/**
		 * Validate arguments of a function call.
		 * @param callFunc The CallFunc
		 * @param arguments The arguments (also obtainable from callFunc)
		 * @param processor The processor serving as processor
		 * @return Returns false if this rule doesn't handle validation of the passed function call. Default validation will be executed.
		 * @throws ProblemException
		 */
		@SignifiesRole(role=ARGUMENT_VALIDATOR)
		public boolean validateArguments(final CallDeclaration callFunc, final ASTNode[] arguments, final ProblemReporter processor) throws ProblemException {
			return false;
		}
		/**
		 * Modify return type of function call.
		 * @param processor processor
		 * @param callFunc function call
		 * @return Modified type or null, in which case the default type will be returned.
		 */
		@SignifiesRole(role=RETURNTYPE_MODIFIER)
		public IType returnType(final ProblemReporter processor, final CallDeclaration callFunc) {
			return null;
		}
		/**
		 * Provide an {@link EntityRegion} for an AST expression at a certain offset. Used for making stuff link-clickable.
		 * Actually only called for {@link StringLiteral}s since other kinds of expressions don't lend themselves to refer to
		 * something only in special contexts.
		 * @param callFunc Function call the passed expression is a parameter of
		 * @param script processor
		 * @param index parameter index
		 * @param offsetInExpression Character offset in the parameter expression
		 * @param parmExpression Parameter expression that should be made link-clickable
		 * @return Return null if no declaration could be found that is referred to.
		 */
		@SignifiesRole(role=DECLARATION_LOCATOR)
		public EntityRegion locateEntityInParameter(final CallDeclaration callFunc, final Script script, final int index, final int offsetInExpression, final ASTNode parmExpression) {
			return null;
		}
		/**
		 * Assign default parameter types to a function.
		 * @param script Context. Will not be the script actually declaring the function when revisiting function as part of visiting included declarations.
		 * @param function The function
		 * @return Returns false if this rule doesn't handle assigning default parameters of the passed function call. No types will be assigned and regular type inference takes place.
		 */
		@SignifiesRole(role=DEFAULT_PARMTYPE_ASSIGNER)
		public boolean assignDefaultParmTypes(final Script script, final Function function, final TypeVariable[] parameterTypeVariables) {
			return false;
		}
		/**
		 * Create new function with the given name. Called by the processor after it has parsed the function name in a function declaration.
		 * @param name The name of the function.
		 * @return Returns null if this rule didn't detect a special name, requiring a specialized function class. Parser will use default class (Function).
		 */
		@SignifiesRole(role=DEFAULT_PARMTYPE_ASSIGNER)
		public Function newFunction(final String name) {
			return null;
		}

		@SignifiesRole(role=FUNCTION_PARM_PROPOSALS_CONTRIBUTOR)
		public void contributeAdditionalProposals(
			final CallDeclaration callFunc, final int index, final ASTNode parmExpression,
			final ScriptCompletionProcessor completions, final ProposalsSite pl) {}
	}

	private final Map<String, SpecialRule> allRules = new HashMap<String, SpecialEngineRules.SpecialRule>();
	private final Map<String, SpecialFuncRule> argumentValidators = new HashMap<String, SpecialFuncRule>();
	private final Map<String, SpecialFuncRule> returnTypeModifiers = new HashMap<String, SpecialFuncRule>();
	private final Map<String, SpecialFuncRule> declarationLocators = new HashMap<String, SpecialFuncRule>();
	private final Map<String, SpecialFuncRule> parmProposalContributors = new HashMap<String, SpecialFuncRule>();
	private final List<SpecialFuncRule> defaultParmTypeAssigners = new LinkedList<SpecialFuncRule>();
	private final List<SpecialFuncRule> functionEventListeners = new LinkedList<SpecialFuncRule>();

	public Iterable<SpecialFuncRule> defaultParmTypeAssignerRules() {
		return defaultParmTypeAssigners;
	}

	public Iterable<SpecialFuncRule> functionEventListeners() {
		return functionEventListeners;
	}

	private interface FunctionsByRole {
		String[] functions(int role);
	}
	private void putFuncRule(final SpecialFuncRule rule, final FunctionsByRole functionsGetter) {
		final int mask = rule.computeRoleMask();
		if ((mask & ARGUMENT_VALIDATOR) != 0) {
			for (final String func : functionsGetter.functions(ARGUMENT_VALIDATOR)) {
				argumentValidators.put(func, rule);
			}
		}
		if ((mask & DECLARATION_LOCATOR) != 0) {
			for (final String func : functionsGetter.functions(DECLARATION_LOCATOR)) {
				declarationLocators.put(func, rule);
			}
		}
		if ((mask & RETURNTYPE_MODIFIER) != 0) {
			for (final String func : functionsGetter.functions(RETURNTYPE_MODIFIER)) {
				returnTypeModifiers.put(func, rule);
			}
		}
		if ((mask & DEFAULT_PARMTYPE_ASSIGNER) != 0) {
			defaultParmTypeAssigners.add(rule);
		}
		if ((mask & FUNCTION_EVENT_LISTENER) != 0) {
			functionEventListeners.add(rule);
		}
		if ((mask & FUNCTION_PARM_PROPOSALS_CONTRIBUTOR) != 0) {
			for (final String func : functionsGetter.functions(FUNCTION_PARM_PROPOSALS_CONTRIBUTOR)) {
				parmProposalContributors.put(func, rule);
			}
		}
	}

	public SpecialRule rule(final String name) {
		return allRules.get(name);
	}

	public void putFuncRule(final SpecialFuncRule rule, final Field fieldReference) {
		putRule(rule, fieldReference);
		putFuncRule(rule, role -> SpecialRule.getFunctionsAppliedTo(fieldReference, role));
	}

	private void putRule(final SpecialFuncRule rule, final Field field) {
		rule.name = field.getName();
		allRules.put(field.getName(), rule);
	}

	private Field fieldWithValue(final Object value) {
		for (Class<?> c = getClass(); c != null; c = c.getSuperclass()) {
			for (final Field f : c.getDeclaredFields()) {
				Object obj;
				try {
					obj = f.get(this);
				} catch (final Exception e) {
					continue;
				}
				if (obj == value) {
					return f;
				}
			}
		}
		return null;
	}

	public void putFuncRule(final SpecialFuncRule rule, final String... functions) {
		final Field field  = fieldWithValue(rule);
		if (field != null) {
			putRule(rule, field);
		}
		putFuncRule(rule, role -> functions);
	}

	public SpecialFuncRule funcRuleFor(final String function, final int role) {
		if (role == ARGUMENT_VALIDATOR) {
			return argumentValidators.get(function);
		} else if (role == DECLARATION_LOCATOR) {
			return declarationLocators.get(function);
		} else if (role == RETURNTYPE_MODIFIER) {
			return returnTypeModifiers.get(function);
		} else if (role == FUNCTION_PARM_PROPOSALS_CONTRIBUTOR) {
			return parmProposalContributors.get(function);
		} else {
			return null;
		}
	}

	/**
	 * CreateObject and similar functions that will return an object of the specified type
	 */
	@AppliedTo(functions={"CreateObject", "CreateContents", "CreateConstruction", "PlaceAnimal", "FindContents", "PlaceVegetation", "CreateObjectAbove"})
	public final SpecialFuncRule objectCreationRule = new SpecialFuncRule() {
		@Override
		public IType returnType(final ProblemReporter processor, final CallDeclaration callFunc) {
			if (callFunc.params().length >= 1) {
				final IType t = processor.typeOf(callFunc.params()[0]);
				IType r = PrimitiveType.OBJECT;
				for (final IType ty : t) {
					if (ty instanceof MetaDefinition) {
						r = processor.script().typing().unify(r, ((MetaDefinition)ty).definition());
					} else if (ty instanceof Definition) {
						r = processor.script().typing().unify(r, ty);
					}
				}
				return r;
			}
			return null;
		}
	};

	/**
	 *  GetID() returns type of calling object
	 */
	@AppliedTo(functions={"GetID"})
	public final SpecialFuncRule getIDRule = new SpecialFuncRule() {
		@Override
		public IType returnType(final ProblemReporter processor, final CallDeclaration callFunc) {
			final IType t =
				callFunc.params().length > 0 ? processor.typeOf(callFunc.params()[0]) :
				callFunc.predecessor() != null ? processor.typeOf(callFunc.predecessor()) :
				processor.script();
			return
				t instanceof Definition ? ((Definition)t).metaDefinition() :
				PrimitiveType.ID;
		}
	};

	/**
	 *  It's a criteria search (FindObjects etc) so guess return type from arguments passed to the criteria search function
	 */
	@AppliedTo(functions={"FindObjects"})
	public final SpecialFuncRule criteriaSearchRule = new SearchCriteriaRuleBase() {
		@Override
		public IType returnType(final ProblemReporter processor, final CallDeclaration callFunc) {
			IType t = searchCriteriaAssumedResult(processor, callFunc, true);
			if (t == null || t == PrimitiveType.UNKNOWN) {
				t = PrimitiveType.OBJECT;
			}
			if (t != null) {
				final Function f = (Function) callFunc.declaration();
				if (f != null && eq(f.returnType(), PrimitiveType.ARRAY)) {
					return new ArrayType(t);
				}
			}
			return t;
		}
		private IType searchCriteriaAssumedResult(final ProblemReporter processor, final CallDeclaration node, final boolean topLevel) {
			final String declarationName = node.name();
			// parameters to FindObjects itself are also &&-ed together
			if (topLevel || declarationName.equals("Find_And")) {
				return andSubConditions(processor, node);
			}
			if (declarationName.equals("Find_Or")) {
				return orSubConditions(processor, node);
			}
			if (declarationName.equals("Find_ID")) {
				return typeByID(processor, node);
			}
			if (declarationName.equals("Find_Func") && node.params().length >= 1) {
				final Object ev = node.params()[0].evaluateStatic(node.parent(Function.class));
				if (ev instanceof String) {
					return typeByFunction(processor.script(), ev);
				}
			}
			return null;
		}
		private IType typeByID(final ProblemReporter processor, final CallDeclaration node) {
			IType unified = null;
			if (node.params().length >= 1) {
				final IType ty = processor.typeOf(node.params()[0]);
				if (ty != null) {
					for (final IType t : ty) {
						final MetaDefinition mdef = as(t, MetaDefinition.class);
						if (mdef != null) {
							unified = processor.script().typing().unify(unified, mdef.definition());
						}
					}
				}
			}
			return unified;
		}
		private IType orSubConditions(final ProblemReporter processor, final CallDeclaration node) {
			final List<IType> types = new LinkedList<IType>();
			for (final ASTNode parm : node.params()) {
				if (parm instanceof CallDeclaration) {
					final CallDeclaration call = (CallDeclaration)parm;
					final IType t = searchCriteriaAssumedResult(processor, call, false);
					if (t != null) {
						for (final IType ty : t) {
							types.add(ty);
						}
					}
				}
			}
			return processor.script().typing().unify(types);
		}
		private IType andSubConditions(final ProblemReporter processor, final CallDeclaration node) {
			final List<IType> types = new LinkedList<IType>();
			for (final ASTNode parm : node.params()) {
				if (parm instanceof CallDeclaration) {
					final CallDeclaration call = (CallDeclaration)parm;
					final IType t = searchCriteriaAssumedResult(processor, call, false);
					if (call.name().equals("Find_ID"))
					 {
						return t; // short-circuit
					}
					if (t != null) {
						types.add(t);
					}

				}
			}
			return processor.script().typing().unify(types);
		}
		private IType typeByFunction(final Script script, final Object ev) {
			final List<Declaration> functions = functionsNamed(script, (String)ev);
			final List<IType> types = new ArrayList<IType>(functions.size());
			for (final Declaration f : functions) {
				if (f instanceof Function) {
					addTypeFromFunction(script, types, (Function) f);
				}
			}
			for (final Index index : script.index().relevantIndexes()) {
				for (final Function f : index.declarationsWithName((String)ev, Function.class)) {
					addTypeFromFunction(script, types, f);
				}
			}
			final IType ty = script.typing().unify(types);
			return ty;
		}
		private void addTypeFromFunction(final Script script, final List<IType> types, final Function f) {
			if (f.body() != null && RETURN_TRUE.match(f.body()) != null) {
				if (f.script() instanceof Definition) {
					types.add(f.script());
				} else {
					for (final Directive directive : f.script().directives()) {
						if (directive.type() == DirectiveType.APPENDTO) {
							final Definition def = f.script().index().definitionNearestTo(script.resource(), directive.contentAsID());
							if (def != null) {
								types.add(def);
							}
						}
					}
				}
			}
		};
	};

	/**
	 *  validate DoSomething() in Schedule("DoSomething()")
	 */
	@AppliedTo(functions={"Schedule"})
	public final SpecialFuncRule scheduleScriptValidationRule = new SpecialFuncRule() {
		@Override
		public boolean validateArguments(final CallDeclaration node, final ASTNode[] arguments, final ProblemReporter processor) {
			if (arguments.length < 1)
			 {
				return false; // no script expression supplied
			}
			final IType objType = arguments.length >= 4 ? processor.typeOf(arguments[3]) : processor.definition();
			Script script = objType != null ? TypeUtil.definition(objType) : null;
			if (script == null)
			 {
				script = processor.script(); // fallback
			}
			final Object scriptExpr = arguments[0].evaluateStatic(script);
			if (scriptExpr instanceof String) {
				try {
					new Standalone(iterable(processor.script())).parse((String)scriptExpr, node.parent(Function.class), null, (markers, positionProvider, code, _node, markerStart, markerEnd, flags, severity, args) -> {
						switch (code) {
						// ignore complaining about missing ';' - some genuine errors might slip through but who cares
						case NotFinished:
						// also ignore undeclared identifier error - local variables and whatnot, checking for all of this is also overkill
						case UndeclaredIdentifier:
							return Decision.DropCharges;
						default:
							if (markers.errorEnabled(code)) {
								try {
									markers.marker(positionProvider, code, _node, arguments[0].start()+1+markerStart, arguments[0].start()+1+markerEnd, flags, severity, args);
								} catch (final ProblemException e1) {}
							}
							return Decision.PassThrough;
						}
					}, null);
				} catch (final ProblemException e) {
					// that on slipped through - pretend nothing happened
				}
			}
			return false; // don't stop regular parameter validating
		};
		@Override
		public EntityRegion locateEntityInParameter(final CallDeclaration node, final Script script, final int index, final int offsetInExpression, final ASTNode parmExpression) {
			if (index == 0 && parmExpression instanceof StringLiteral) {
				final StringLiteral lit = (StringLiteral) parmExpression;
				final ExpressionLocator<Void> locator = new ExpressionLocator<Void>(offsetInExpression-1); // make up for '"'
				try {
					new Standalone(iterable(script)).parse(lit.literal(), node.parent(Function.class), locator, null, null);
				} catch (final ProblemException e) {}
				if (locator.expressionAtRegion() != null) {
					final EntityRegion reg = locator.expressionAtRegion().entityAt(offsetInExpression, locator);
					if (reg != null) {
						return reg.incrementRegionBy(lit.start()+1);
					}
				}
			}
			return null;
		};
	};

	@AppliedTo(functions={"ScheduleCall"})
	public final SpecialFuncRule scheduleCallLinkRule = new SpecialFuncRule() {
		@Override
		public EntityRegion locateEntityInParameter(final CallDeclaration callFunc, final Script script, final int index, final int offsetInExpression, final ASTNode parmExpression) {
			if (index == 1 && parmExpression instanceof StringLiteral) {
				final StringLiteral lit = (StringLiteral) parmExpression;
				final IType t = script.typings().get(callFunc.params()[0]);
				final Script scriptToLookIn = t instanceof Script ? (Script)t : script;
				final Function func = scriptToLookIn.findFunction(lit.literal());
				if (func != null) {
					return new EntityRegion(func, new Region(lit.start()+1, lit.getLength()-2));
				}
			}
			return null;
		};
	};

	/**
	 * Validate parameters for Add/Append/Set Command differently according to the command parameter
	 */
	@AppliedTo(functions={"AddCommand", "AppendCommand", "SetCommand"})
	public final SpecialFuncRule addCommandValidationRule = new SpecialFuncRule() {
		@Override
		public boolean validateArguments(final CallDeclaration node, final ASTNode[] arguments, final ProblemReporter processor) {
			final Function f = node.declaration() instanceof Function ? (Function)node.declaration() : null;
			if (f != null && arguments.length >= 3) {
				// look if command is "Call"; if so treat parms 2, 3, 4 as any
				final Object command = arguments[1].evaluateStatic(node.parent(Function.class));
				if (command instanceof String && command.equals("Call")) { //$NON-NLS-1$
					int givenParam = 0;
					for (final Variable parm : f.parameters()) {
						if (givenParam >= arguments.length) {
							break;
						}
						final ASTNode given = arguments[givenParam++];
						if (given == null) {
							continue;
						}
						final IType parmType = givenParam >= 2 && givenParam <= 4 ? PrimitiveType.ANY : parm.type();
						final IType givenType = processor.typeOf(given);
						if (processor.script().typing().unifyNoChoice(parmType, givenType) == null) {
							processor.incompatibleTypesMarker(node, given, parmType, processor.typeOf(given));
						} else {
							processor.judgement(given, parmType, TypingJudgementMode.UNIFY);
						}
					}
					return true;
				}
			}
			return false;
		};
	};

	/**
	 * Add link to the function being indirectly called by GameCall
	 */
	@AppliedTo(functions={"GameCall", "GameCallEx"})
	public final SpecialFuncRule gameCallLinkRule = new SpecialFuncRule() {
		@Override
		public EntityRegion locateEntityInParameter(final CallDeclaration callFunc, final Script script, final int parameterIndex, final int offsetInExpression, final ASTNode parmExpression) {
			if (parameterIndex == 0 && parmExpression instanceof StringLiteral) {
				final StringLiteral lit = (StringLiteral)parmExpression;
				final Index index = script.index();
				final Scenario scenario = Scenario.nearestScenario(script.resource());
				if (scenario != null) {
					final Function scenFunc = scenario.findFunction(lit.stringValue());
					if (scenFunc != null) {
						return new EntityRegion(scenFunc, lit.identifierRegion());
					}
				} else {
					final List<Declaration> decs = new LinkedList<Declaration>();
					index.forAllRelevantIndexes(ndx -> {
						for (final Scenario s : ndx.scenarios()) {
							final Function f = s.findLocalFunction(lit.literal(), true);
							if (f != null) {
								decs.add(f);
							}
						}
					});
					if (decs.size() > 0) {
						return new EntityRegion(new HashSet<IIndexEntity>(decs), lit.identifierRegion());
					}
				}
			}
			return null;
		};
	};

	/**
	 * Add link to the function being indirectly called by Call
	 */
	@AppliedTo(functions={"Call"})
	public final SpecialFuncRule callLinkRule = new SpecialFuncRule() {
		@Override
		public EntityRegion locateEntityInParameter(final CallDeclaration callFunc, final Script script, final int index, final int offsetInExpression, final ASTNode parmExpression) {
			if (index == 0 && parmExpression instanceof StringLiteral) {
				final StringLiteral lit = (StringLiteral)parmExpression;
				final Function f = script.findFunction(lit.stringValue());
				if (f != null) {
					return new EntityRegion(f, lit.identifierRegion());
				}
			}
			return null;
		};
	};

	/**
	 * Add link to the function being indirectly called by Private/Public/public Call
	 */
	@AppliedTo(functions={"PrivateCall", "PublicCall", "PrivateCall"})
	public final SpecialFuncRule scopedCallLinkRule = new SpecialFuncRule() {
		@Override
		public EntityRegion locateEntityInParameter(final CallDeclaration callFunc, final Script script, final int index, final int offsetInExpression, final ASTNode parmExpression) {
			if (index == 1 && parmExpression instanceof StringLiteral) {
				final StringLiteral lit = (StringLiteral)parmExpression;
				final Definition def = defaulting(
					as(script.typings().get(callFunc.params()[0]), Definition.class),
					() -> callFunc.predecessor() != null ? as(script.typings().get(callFunc.predecessor()), Definition.class) : null,
					() -> as(script, Definition.class)
				);
				if (def != null) {
					final Function f = def.findFunction(lit.stringValue());
					if (f != null) {
						return new EntityRegion(f, lit.identifierRegion());
					}
				}
			}
			return null;
		};
	};

	/**
	 * Link to local vars referenced by LocalN
	 */
	@AppliedTo(functions={"LocalN"})
	public final SpecialFuncRule localNLinkRule = new SpecialFuncRule() {
		@Override
		public EntityRegion locateEntityInParameter(final CallDeclaration callFunc, final Script script, final int index, final int offsetInExpression, final ASTNode parmExpression) {
			if (index == 0 && parmExpression instanceof StringLiteral) {
				final StringLiteral lit = (StringLiteral)parmExpression;
				final Definition def = defaulting(
					callFunc.params().length > 1 ? as(script.typings().get(callFunc.params()[1]), Definition.class) : null,
					() -> callFunc.predecessor() != null ? as(script.typings().get(callFunc.predecessor()), Definition.class) : null,
					() -> as(script, Definition.class)
				);
				if (def != null) {
					final Variable var = def.findVariable(lit.stringValue());
					if (var != null) {
						return new EntityRegion(var, lit.identifierRegion());
					}
				}
			}
			return null;
		};
	};

	/**
	 * Special rule to change the return type of GetPlrKnowledge if certain parameters are passed to it.
	 */
	@AppliedTo(functions={"GetPlrKnowledge", "GetPlrMagic"})
	public final SpecialFuncRule getPlrKnowledgeRule = new SpecialFuncRule() {
		@Override
		public IType returnType(final ProblemReporter processor, final CallDeclaration callFunc) {
			return callFunc.params().length >= 3 ? PrimitiveType.ID : PrimitiveType.INT;
		};
	};

	public abstract class LocateResourceByNameRule extends SpecialFuncRule {
		public abstract Set<IIndexEntity> locateEntitiesByName(CallDeclaration node, String name, ProjectIndex pi, Script script);
		@Override
		public EntityRegion locateEntityInParameter(final CallDeclaration node, final Script script, final int index, final int offsetInExpression, final ASTNode parmExpression) {
			Object parmEv;
			if (index == 0 && (parmEv = parmExpression.evaluateStatic(node.parent(Function.class))) instanceof String) {
				final String resourceName = (String)parmEv;
				final ProjectIndex pi = (ProjectIndex)script.index();
				final Set<IIndexEntity> e = locateEntitiesByName(node, resourceName, pi, script);
				if (e != null) {
					return new EntityRegion(e, parmExpression);
				}
			}
			return null;
		}
	}

	/**
	 * Links to particle definitions from various particle functions.
	 */
	@AppliedTo(functions={"CreateParticle", "CastAParticles", "CastParticles", "CastBackParticles", "PushParticles"})
	public final SpecialFuncRule linkToParticles = new LocateResourceByNameRule() {
		@Override
		public Set<IIndexEntity> locateEntitiesByName(final CallDeclaration callFunc, final String name, final ProjectIndex pi, final Script script) {
			final IIndexEntity unit = pi.findPinnedStructure(ParticleUnit.class, name, script.resource(), true, "Particle.txt");
			return ArrayUtil.set(unit);
		}
	};

	/**
	 * Rule which causes the first argument in a Format call (the format string) to link to a resource as
	 * required by a containing call the result of Format is the first argument for.
	 */
	@AppliedTo(functions={"Format"})
	public final SpecialFuncRule linkFormat = new LocateResourceByNameRule() {
		@Override
		public Set<IIndexEntity> locateEntitiesByName(final CallDeclaration formatCall, final String name, final ProjectIndex pi, final Script script) {
			final CallDeclaration containingCall = as(formatCall.parent(), CallDeclaration.class);
			if (containingCall != null && containingCall.indexOfParm(formatCall) == 0) {
				final SpecialFuncRule rule = formatCall.parent(Declaration.class).engine().specialRules().funcRuleFor(containingCall.name(), DECLARATION_LOCATOR);
				if (rule instanceof LocateResourceByNameRule) {
					return ((LocateResourceByNameRule)rule).locateEntitiesByName(formatCall, name, pi, script);
				}
			}
			return null;
		}
	};

	/**
	 * Rule to link to sound files.
	 */
	@AppliedTo(functions={"Sound"})
	public final SpecialFuncRule linkToSound = new LocateResourceByNameRule() {
		protected void collectSoundResourcesInFolder(final Set<IIndexEntity> set, final Matcher nameMatcher, final Engine engine, final IContainer container, final ProjectIndex pi) {
			try {
				for (final IResource r : container.members()) {
					if (nameMatcher.reset(r.getName()).matches()) {
						set.add(new ProjectResource(pi, r));
					}
				}
			} catch (final CoreException e) {
				e.printStackTrace();
			}
		}
		@Override
		public Set<IIndexEntity> locateEntitiesByName(final CallDeclaration callFunc, String name, final ProjectIndex pi, final Script script) {
			final Engine engine = script.engine();
			name = name.replace(".", "\\\\.").replaceAll("[\\*\\?]", ".*?").replace("%d", "[0-9]*");
			final HashSet<IIndexEntity> results = new HashSet<IIndexEntity>();
			boolean extensionWildcardNeeded = true;
			for (final String e : engine.settings().supportedSoundFileExtensions()) {
				if (name.endsWith("\\\\."+e)) {
					extensionWildcardNeeded = false;
					break;
				}
			}
			if (extensionWildcardNeeded) {
				name += "\\." + StringUtil.writeBlock(null, "(", ")", "|", engine.settings().supportedSoundFileExtensions().stream());
			}
			final Matcher nameMatcher = Pattern.compile(name).matcher("");
			final String soundGroupName = "Sound."+engine.settings().canonicalToConcreteExtension().get(FileExtension.ResourceGroup);
			final IResource r = script.resource();
			for (
				IContainer c = r instanceof IContainer ? (IContainer)r : r != null ? r.getParent() : null, d = null;
				c != null;
				c = c.getParent(), d = c != null ? as(c.findMember(soundGroupName), IContainer.class) : null
			) {
				collectSoundResourcesInFolder(results, nameMatcher, engine, c, pi);
				if (d != null) {
					collectSoundResourcesInFolder(results, nameMatcher, engine, d, pi);
				}
			}
			return results;
		}
	};

	/**
	 * Base class for SetAction link rules.
	 * @author madeen
	 *
	 */
	protected class SetActionLinkRule extends SpecialFuncRule {
		@Override
		public EntityRegion locateEntityInParameter(final CallDeclaration node, final Script script, final int index, final int offsetInExpression, final ASTNode parmExpression) {
			return actionLinkForDefinition(node.parent(Function.class), as(script, Definition.class), parmExpression);
		}
		protected EntityRegion actionLinkForDefinition(final Function currentFunction, final Definition definition, final ASTNode actionNameExpression) {
			Object parmEv;
			if (definition != null && (parmEv = actionNameExpression.evaluateStatic(currentFunction)) instanceof String) {
				final String actionName = (String)parmEv;
				if (definition instanceof Definition) {
					final Definition projDef = definition;
					final IResource res = Utilities.findMemberCaseInsensitively(projDef.definitionFolder(), "ActMap.txt");
					if (res instanceof IFile) {
						final IniUnit unit = (IniUnit) Structure.pinned(res, true, false);
						if (unit instanceof IniUnitWithNamedSections) {
							final IniSection actionSection = unit.sectionMatching(((IniUnitWithNamedSections) unit).nameMatcherPredicate(actionName));
							if (actionSection != null) {
								return new EntityRegion(actionSection, actionNameExpression);
							}
						}
					}
				}
			}
			return null;
		}
	}

	@AppliedTo(functions={"SetAction"})
	public SpecialFuncRule setActionLinkRule = new SetActionLinkRule();

	private abstract class SearchCriteriaRuleBase extends SpecialFuncRule {
		protected List<Declaration> functionsNamed(final Script script, final String name) {
			final List<Declaration> matchingDecs = new LinkedList<Declaration>();
			final Sink<Script> scriptSink = item -> {
				if (item.dictionary() != null && item.dictionary().contains(name)) {
					item.requireLoaded();
					final Function f = item.findFunction(name);
					if (f != null) {
						matchingDecs.add(f);
					}
				}
			};
			script.index().forAllRelevantIndexes(index -> index.allScripts(scriptSink));
			return matchingDecs;
		}
	}

	/**
	 * Rule to make function names inside Find_Func link-clickable (will open DeclarationChooser).
	 */
	@AppliedTo(functions={"Find_Func"})
	public final SpecialFuncRule findFuncRule = new SearchCriteriaRuleBase() {
		@Override
		public EntityRegion locateEntityInParameter(final CallDeclaration callFunc, final Script script,
			final int index, final int offsetInExpression, final ASTNode parmExpression) {
			if (parmExpression instanceof StringLiteral) {
				final StringLiteral lit = (StringLiteral)parmExpression;
				final List<Declaration> matchingDecs = functionsNamed(script, lit.stringValue());
				if (matchingDecs.size() > 0) {
					return new EntityRegion(new HashSet<IIndexEntity>(matchingDecs), lit.identifierRegion());
				}
			}
			return null;
		};
	};

	@AppliedTo(functions={"GetScenarioVal"})
	public final SpecialFuncRule scenarioValResultRule = new SpecialFuncRule() {
		@Override
		public IType returnType(final ProblemReporter processor, final CallDeclaration callFunc) {
			if (callFunc.params().length > 0 && callFunc.params()[0] instanceof StringLiteral) {
				final String entryName = ((StringLiteral)callFunc.params()[0]).stringValue();
				final String sectionName =
					callFunc.params().length > 1 &&
					callFunc.params()[1] instanceof StringLiteral ?
						((StringLiteral)callFunc.params()[1]).stringValue() : null;
				final IniConfiguration conf = processor.script().engine().iniConfigurations().configurationFor("Scenario.txt");
				final IniSectionDefinition primarySection = conf.sections().get(sectionName);
				final IniEntryDefinition trueEntry = primarySection != null ? as(primarySection.entries().get(entryName), IniEntryDefinition.class) : null;
				final IniEntryDefinition entry = defaulting(trueEntry, () -> conf.sections().values().stream()
					.map(def -> as(def.entries().get(entryName), IniEntryDefinition.class))
					.filter(x -> x != null)
					.findFirst().orElse(null));
				if (entry != null) {
					return typeFromEntryClass(entry.entryClass());
				}
			}
			return PrimitiveType.ANY;
		}
		private IType typeFromEntryClass(final Class<?> cls) {
			return
				Action.class.isAssignableFrom(cls) ? PrimitiveType.STRING :
				Boolean.class.isAssignableFrom(cls) ? PrimitiveType.BOOL :
				CategoriesValue.class.isAssignableFrom(cls) ? PrimitiveType.INT :
				DefinitionPack.class.isAssignableFrom(cls) ? PrimitiveType.STRING :
				Enum.class.isAssignableFrom(cls) ? PrimitiveType.STRING :
				FunctionEntry.class.isAssignableFrom(cls) ? PrimitiveType.STRING :
				ID.class.isAssignableFrom(cls) ? PrimitiveType.ID :
				IDArray.class.isAssignableFrom(cls) ? TypeChoice.make(PrimitiveType.ID, PrimitiveType.INT) :
				IconSpec.class.isAssignableFrom(cls) ? PrimitiveType.STRING :
				IntegerArray.class.isAssignableFrom(cls) ? PrimitiveType.INT :
				MaterialArray.class.isAssignableFrom(cls) ? TypeChoice.make(PrimitiveType.STRING, PrimitiveType.INT) :
				SignedInteger.class.isAssignableFrom(cls) ? PrimitiveType.INT :
				java.lang.String.class.isAssignableFrom(cls) ? PrimitiveType.STRING :
				PrimitiveType.ANY;
		}
	};

	/**
	 * Add rules declared as public instance variables to various internal lists so they will be recognized.
	 * Annotations are consulted to decide what lists the rules are added to.
	 */
	public void initialize() {
		for (Class<?> c = getClass(); c != null; c = c.getSuperclass()) {
			for (final Field f : c.getDeclaredFields()) {
				Object obj;
				try {
					obj = f.get(this);
				} catch (final Exception e) {
					continue;
				}
				if (obj instanceof SpecialFuncRule) {
					final SpecialFuncRule funcRule = (SpecialFuncRule) obj;
					putFuncRule(funcRule, f);
				}
			}
		}
	}

	/**
	 * Parse an ID. Default implementation returns null. To be overridden for specific engines.
	 * @param scanner The scanner to parse the id from
	 * @return The parsed id or null if parsing failed
	 */
	public ID parseID(final BufferedScanner scanner) { return null; }
	public void printID(ASTNodePrinter output, IDLiteral literal) { output.append(literal.idValue().stringValue()); }

	public enum ScenarioConfigurationProcessing {
		Load,
		Save
	}

	public void processScenarioConfiguration(final ScenarioUnit unit, final ScenarioConfigurationProcessing processing) {
		// do nothing
	}

	public Predicate<Definition> configurationEntryDefinitionFilter(final IniEntry entry) {
		if (entry instanceof IniEntry && entry.definition() != null && entry.definition().categoryFilter() != null) {
			return new Predicate<Definition>() {
				final String filter = entry.definition().categoryFilter();
				@Override
				public boolean test(final Definition item) {
					final CategoriesValue category = item.category();
					return category != null && category.constants() != null && category.constants().contains(filter);
				}
			};
		} else if (entry.key().equals("Animal")) {
			return item -> item.findFunction("IsAnimal") != null;
		} else if (entry.key().equals("Crew")) {
			return item -> // ignore any definitions that append
				!item.directives().stream().anyMatch(d -> d.type() == DirectiveType.APPENDTO) &&
				item.findFunction("IsClonk") != null;
		} else {
			return item -> true;
		}
	}

	public void refreshIndex(final Index index) {}
	public void contribute(final Engine engine) {}

}
